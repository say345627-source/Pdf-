package com.example.data.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.regex.Pattern

class PdfEngine(val file: File) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val pageCountInternal: Int

    // 24MB bitmap cache for pages
    private val memoryCache: LruCache<Int, Bitmap> = object : LruCache<Int, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount
        }
    }

    // Text cache per page
    private val textCache = mutableMapOf<Int, String>()
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

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until pageCount) return@withContext null

        synchronized(memoryCache) {
            val cached = memoryCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        try {
            val r = renderer ?: return@withContext null
            val page = synchronized(r) { r.openPage(pageIndex) }

            val originalWidth = page.width
            val originalHeight = page.height

            // Scale to render high-resolution bitmap (minimum targetWidth or 2x original)
            val scale = (targetWidth.toFloat() / originalWidth.toFloat()).coerceIn(1.5f, 3.0f)
            val bitmapWidth = (originalWidth * scale).toInt()
            val bitmapHeight = (originalHeight * scale).toInt()

            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            synchronized(r) {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
            }

            synchronized(memoryCache) {
                memoryCache.put(pageIndex, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getPageAspectRatio(pageIndex: Int): Float {
        if (pageIndex !in 0 until pageCount) return 1.414f
        val r = renderer ?: return 1.414f
        return try {
            synchronized(r) {
                val page = r.openPage(pageIndex)
                val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                page.close()
                ratio.coerceIn(0.5f, 3.0f)
            }
        } catch (e: Exception) {
            1.414f
        }
    }

    /**
     * Extracts text of a specific page (0-based) for selection and copying.
     */
    suspend fun getPageText(pageIndex: Int): String = withContext(Dispatchers.IO) {
        if (textCache.containsKey(pageIndex)) {
            return@withContext textCache[pageIndex] ?: ""
        }
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

        // Fallback: search all BT ... ET blocks if page splitting by pattern found nothing
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
        // Match (string) Tj or (string) ' or [(string)(string)] TJ
        val textLiteralPattern = Pattern.compile("\\((.*?)\\)\\s*(?:Tj|'|\")")
        val literalMatcher = textLiteralPattern.matcher(block)
        while (literalMatcher.find()) {
            val str = literalMatcher.group(1) ?: ""
            sb.append(cleanPdfString(str)).append(" ")
        }

        // Match hex strings <06270644...> Tj
        val hexPattern = Pattern.compile("<([0-9a-fA-F]+)>\\s*Tj")
        val hexMatcher = hexPattern.matcher(block)
        while (hexMatcher.find()) {
            val hex = hexMatcher.group(1) ?: ""
            sb.append(decodeHexString(hex)).append(" ")
        }

        // Match array TJ: [(text) -10 (more)] TJ
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
            // Check UTF-16BE BOM or standard 2-byte UTF-16
            if (bytes.size >= 2 && (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            } else if (bytes.size % 2 == 0) {
                String(bytes, Charsets.UTF_16BE)
            } else {
                String(bytes, Charsets.ISO_8859_1)
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun close() {
        try {
            renderer?.close()
            renderer = null
            fileDescriptor?.close()
            fileDescriptor = null
            memoryCache.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class SearchResult(
        val pageNumber: Int,
        val snippet: String
    )
}
