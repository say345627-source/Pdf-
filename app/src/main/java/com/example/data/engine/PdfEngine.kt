package com.example.data.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

class PdfEngine(val file: File) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val pageCountInternal: Int
    private val renderMutex = Mutex()

    @Volatile
    private var isClosed = false

    // Aspect ratio cache (height / width) so UI layout never blocks Main Thread
    private val aspectRatioCache = ConcurrentHashMap<Int, Float>()

    // Dynamic full-page bitmap cache (up to 48MB depending on heap)
    private val memoryCache: LruCache<Int, Bitmap> = run {
        val maxMemBytes = Runtime.getRuntime().maxMemory().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val cacheBytes = (maxMemBytes / 6).coerceIn(24 * 1024 * 1024, 48 * 1024 * 1024)
        object : LruCache<Int, Bitmap>(cacheBytes) {
            override fun sizeOf(key: Int, value: Bitmap): Int {
                return value.byteCount
            }
        }
    }

    // Separate small cache for Page Manager thumbnails so grid scrolling doesn't evict reader pages
    private val thumbnailCache: LruCache<Int, Bitmap> = object : LruCache<Int, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount
        }
    }

    // Text cache per page
    private val textCache = ConcurrentHashMap<Int, String>()

    @Volatile
    private var hasParsedAllText = false

    init {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        fileDescriptor = pfd
        val r = PdfRenderer(pfd)
        renderer = r
        pageCountInternal = r.pageCount
    }

    val pageCount: Int
        get() = pageCountInternal

    /**
     * Non-blocking O(1) lookup for UI thread layout pass. Never touches PdfRenderer synchronously!
     */
    fun getCachedAspectRatio(pageIndex: Int): Float {
        return aspectRatioCache[pageIndex] ?: 1.414f
    }

    /**
     * Asynchronously resolves and caches a page's aspect ratio (height / width) on Dispatchers.IO.
     */
    suspend fun loadAspectRatio(pageIndex: Int): Float = withContext(Dispatchers.IO) {
        aspectRatioCache[pageIndex]?.let { return@withContext it }
        if (isClosed || pageIndex !in 0 until pageCount) return@withContext 1.414f

        renderMutex.withLock {
            aspectRatioCache[pageIndex]?.let { return@withContext it }
            if (isClosed) return@withContext 1.414f
            val r = renderer ?: return@withContext 1.414f
            try {
                val page = r.openPage(pageIndex)
                val ratio = (page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)).coerceIn(0.45f, 3.0f)
                page.close()
                aspectRatioCache[pageIndex] = ratio
                ratio
            } catch (_: Exception) {
                1.414f
            }
        }
    }

    /**
     * Pre-scans page dimensions for fast smooth scrolling without Main Thread blocking.
     */
    suspend fun preloadAspectRatios(startIndex: Int = 0, count: Int = 40) = withContext(Dispatchers.IO) {
        if (isClosed) return@withContext
        val end = (startIndex + count).coerceAtMost(pageCount)
        for (i in startIndex.coerceAtLeast(0) until end) {
            coroutineContext.ensureActive()
            if (isClosed) break
            if (!aspectRatioCache.containsKey(i)) {
                loadAspectRatio(i)
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        if (isClosed || pageIndex !in 0 until pageCount) return@withContext null

        synchronized(memoryCache) {
            val cached = memoryCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        renderMutex.withLock {
            coroutineContext.ensureActive()
            if (isClosed) return@withContext null

            synchronized(memoryCache) {
                val cached = memoryCache.get(pageIndex)
                if (cached != null && !cached.isRecycled) {
                    return@withContext cached
                }
            }

            try {
                val r = renderer ?: return@withContext null
                val page = r.openPage(pageIndex)
                try {
                    val originalWidth = page.width.coerceAtLeast(1)
                    val originalHeight = page.height.coerceAtLeast(1)
                    val ratio = (originalHeight.toFloat() / originalWidth.toFloat()).coerceIn(0.45f, 3.0f)
                    aspectRatioCache[pageIndex] = ratio

                    val safeTargetWidth = targetWidth.coerceIn(480, 1280)
                    var scale = (safeTargetWidth.toFloat() / originalWidth.toFloat()).coerceIn(0.8f, 2.2f)
                    var bitmapWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
                    var bitmapHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

                    // Prevent oversized bitmaps from causing OOM or slow rasterization stalls
                    val maxDim = 2100
                    if (bitmapWidth > maxDim || bitmapHeight > maxDim) {
                        val clampRatio = maxDim.toFloat() / maxOf(bitmapWidth, bitmapHeight).toFloat()
                        bitmapWidth = (bitmapWidth * clampRatio).toInt().coerceAtLeast(1)
                        bitmapHeight = (bitmapHeight * clampRatio).toInt().coerceAtLeast(1)
                    }

                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    synchronized(memoryCache) {
                        memoryCache.put(pageIndex, bitmap)
                    }
                    bitmap
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Renders a lightweight thumbnail for the Page Manager grid.
     */
    suspend fun renderThumbnail(pageIndex: Int, targetWidth: Int = 320): Bitmap? = withContext(Dispatchers.IO) {
        if (isClosed || pageIndex !in 0 until pageCount) return@withContext null

        synchronized(thumbnailCache) {
            val cached = thumbnailCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        renderMutex.withLock {
            coroutineContext.ensureActive()
            if (isClosed) return@withContext null

            synchronized(thumbnailCache) {
                val cached = thumbnailCache.get(pageIndex)
                if (cached != null && !cached.isRecycled) {
                    return@withContext cached
                }
            }

            try {
                val r = renderer ?: return@withContext null
                val page = r.openPage(pageIndex)
                try {
                    val originalWidth = page.width.coerceAtLeast(1)
                    val originalHeight = page.height.coerceAtLeast(1)
                    val ratio = (originalHeight.toFloat() / originalWidth.toFloat()).coerceIn(0.45f, 3.0f)
                    aspectRatioCache[pageIndex] = ratio

                    val scale = (targetWidth.toFloat() / originalWidth.toFloat()).coerceIn(0.25f, 1.5f)
                    val bitmapWidth = (originalWidth * scale).toInt().coerceIn(80, 480)
                    val bitmapHeight = (bitmapWidth * ratio).toInt().coerceIn(80, 720)

                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    synchronized(thumbnailCache) {
                        thumbnailCache.put(pageIndex, bitmap)
                    }
                    bitmap
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Extracts text of a specific page (0-based) for selection and copying.
     */
    suspend fun getPageText(pageIndex: Int): String = withContext(Dispatchers.IO) {
        textCache[pageIndex]?.let { return@withContext it }
        val allExtracted = parsePdfStreams()
        return@withContext allExtracted.getOrElse(pageIndex) { "" }
    }

    /**
     * Search query across all pages. Returns a list of matching page numbers (1-indexed) and snippets.
     */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<SearchResult>()
        val allExtracted = parsePdfStreams()

        allExtracted.forEachIndexed { index, text ->
            if (text.contains(query, ignoreCase = true)) {
                val matchIdx = text.indexOf(query, ignoreCase = true)
                val start = (matchIdx - 30).coerceAtLeast(0)
                val end = (matchIdx + query.length + 50).coerceAtMost(text.length)
                val snippet = "..." + text.substring(start, end).replace("\n", " ").trim() + "..."
                results.add(
                    SearchResult(
                        pageNumber = index + 1,
                        snippet = snippet
                    )
                )
            }
        }
        results
    }

    private fun parsePdfStreams(): List<String> {
        if (hasParsedAllText) {
            return (0 until pageCount).map { textCache[it] ?: "" }
        }

        try {
            val textBlocks = mutableListOf<String>()
            FileInputStream(file).use { stream ->
                val content = readStreamToString(stream)
                val pages = extractPagesTextFromRawPdf(content)
                textBlocks.addAll(pages)
            }

            for (i in 0 until pageCount) {
                val text = if (i < textBlocks.size) textBlocks[i] else ""
                textCache[i] = text
            }
            hasParsedAllText = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return (0 until pageCount).map { textCache[it] ?: "" }
    }

    private fun readStreamToString(stream: InputStream): String {
        val sb = StringBuilder()
        val buffer = ByteArray(8192)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            sb.append(String(buffer, 0, read, Charsets.ISO_8859_1))
        }
        return sb.toString()
    }

    private fun extractPagesTextFromRawPdf(raw: String): List<String> {
        val result = mutableListOf<String>()
        val pagePattern = Pattern.compile("/Type\\s*/Page[^s].*?stream\\r?\\n(.*?)\\r?\\nendstream", Pattern.DOTALL)
        val matcher = pagePattern.matcher(raw)

        while (matcher.find()) {
            val streamContent = matcher.group(1) ?: ""
            val extracted = extractTextFromStream(streamContent)
            result.add(extracted)
        }

        if (result.isEmpty()) {
            val allBt = extractAllBtBlocks(raw)
            if (allBt.isNotEmpty()) {
                result.add(allBt)
            }
        }

        return result
    }

    private fun extractAllBtBlocks(raw: String): String {
        val sb = StringBuilder()
        val btPattern = Pattern.compile("BT\\s+(.*?)\\s+ET", Pattern.DOTALL)
        val matcher = btPattern.matcher(raw)
        while (matcher.find()) {
            val block = matcher.group(1) ?: ""
            sb.append(parseTextOperators(block)).append("\n")
        }
        return sb.toString().trim()
    }

    private fun extractTextFromStream(streamContent: String): String {
        val sb = StringBuilder()
        val btPattern = Pattern.compile("BT\\s+(.*?)\\s+ET", Pattern.DOTALL)
        val matcher = btPattern.matcher(streamContent)
        while (matcher.find()) {
            val block = matcher.group(1) ?: ""
            sb.append(parseTextOperators(block)).append("\n")
        }
        return sb.toString().trim()
    }

    private fun parseTextOperators(block: String): String {
        val sb = StringBuilder()
        val textLiteralPattern = Pattern.compile("\\((.*?)\\)\\s*(?:Tj|'|\")")
        val literalMatcher = textLiteralPattern.matcher(block)
        while (literalMatcher.find()) {
            val str = literalMatcher.group(1) ?: ""
            sb.append(cleanPdfString(str)).append(" ")
        }

        val hexPattern = Pattern.compile("<([0-9a-fA-F]+)>\\s*Tj")
        val hexMatcher = hexPattern.matcher(block)
        while (hexMatcher.find()) {
            val hex = hexMatcher.group(1) ?: ""
            sb.append(decodeHexString(hex)).append(" ")
        }

        val arrayPattern = Pattern.compile("\\[(.*?)\\]\\s*TJ", Pattern.DOTALL)
        val arrayMatcher = arrayPattern.matcher(block)
        while (arrayMatcher.find()) {
            val arrayContent = arrayMatcher.group(1) ?: ""
            val subMatcher = Pattern.compile("\\((.*?)\\)").matcher(arrayContent)
            while (subMatcher.find()) {
                val str = subMatcher.group(1) ?: ""
                sb.append(cleanPdfString(str))
            }
            sb.append(" ")
        }

        return sb.toString().trim()
    }

    private fun cleanPdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun decodeHexString(hex: String): String {
        return try {
            val bytes = ByteArray(hex.length / 2)
            for (i in 0 until hex.length step 2) {
                bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
            }
            if (bytes.size >= 2 && (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            } else if (bytes.size % 2 == 0) {
                String(bytes, Charsets.UTF_16BE)
            } else {
                String(bytes, Charsets.ISO_8859_1)
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun close() {
        isClosed = true
        memoryCache.evictAll()
        thumbnailCache.evictAll()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            renderMutex.withLock {
                try {
                    renderer?.close()
                    renderer = null
                    fileDescriptor?.close()
                    fileDescriptor = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    data class SearchResult(
        val pageNumber: Int,
        val snippet: String
    )
}
