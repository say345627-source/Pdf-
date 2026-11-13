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

    // Conservative bitmap budgets: the reader must coexist with Compose, the database,
    // and the rest of the app on mid-range devices without triggering Android low-memory kills.
    private val memoryCache: LruCache<Int, Bitmap> = run {
        val maxMemBytes = Runtime.getRuntime().maxMemory().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val cacheBytes = (maxMemBytes / 16).coerceIn(6 * 1024 * 1024, 20 * 1024 * 1024)
        object : LruCache<Int, Bitmap>(cacheBytes) {
            override fun sizeOf(key: Int, value: Bitmap): Int {
                return value.byteCount
            }
        }
    }

    // Keep page-manager thumbnails on a separate, much smaller memory budget.
    private val thumbnailCache: LruCache<Int, Bitmap> = object : LruCache<Int, Bitmap>(3 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount
        }
    }

    // Text cache per page
    private val textCache = ConcurrentHashMap<Int, String>()
    private val textParseMutex = Mutex()

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

                    // Render at a bounded resolution. RGB_565 uses half the memory of
                    // ARGB_8888 and is suitable for opaque white document pages.
                    val safeTargetWidth = targetWidth.coerceIn(480, 1000)
                    val scale = (safeTargetWidth.toFloat() / originalWidth.toFloat()).coerceIn(0.5f, 1.6f)
                    var bitmapWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
                    var bitmapHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

                    // Bound both dimensions and total pixel count for unusually large scans.
                    val maxDim = 1600
                    val maxPixels = 2_800_000L
                    val dimensionScale = maxDim.toFloat() / maxOf(bitmapWidth, bitmapHeight).toFloat()
                    val pixelScale = kotlin.math.sqrt(maxPixels.toDouble() /
                        (bitmapWidth.toLong() * bitmapHeight.toLong()).coerceAtLeast(1L)).toFloat()
                    val clampScale = minOf(1f, dimensionScale, pixelScale)
                    if (clampScale < 1f) {
                        bitmapWidth = (bitmapWidth * clampScale).toInt().coerceAtLeast(1)
                        bitmapHeight = (bitmapHeight * clampScale).toInt().coerceAtLeast(1)
                    }

                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565)
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

                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565)
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
        textParseMutex.withLock {
            textCache[pageIndex]?.let { return@withLock it }
            val allExtracted = parsePdfStreams()
            allExtracted.getOrElse(pageIndex) { "" }
        }
    }

    /**
     * Search query across all pages. Returns exact 1-based page matches with per-page match counts and snippets.
     */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = normalizeArabicForSearch(query)
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val reversedQuery = cleanQuery.reversed()
        val results = mutableListOf<SearchResult>()
        val allExtracted = textParseMutex.withLock { parsePdfStreams() }

        allExtracted.forEachIndexed { index, rawText ->
            if (rawText.isBlank()) return@forEachIndexed
            val normalizedText = normalizeArabicForSearch(rawText)

            val forwardCount = countOccurrences(normalizedText, cleanQuery)
            val usedReversed = forwardCount == 0 && cleanQuery.length >= 2 && containsArabic(cleanQuery)
            val matchCount = if (forwardCount > 0) forwardCount else if (usedReversed) countOccurrences(normalizedText, reversedQuery) else 0

            if (matchCount > 0) {
                val targetTerm = if (usedReversed) reversedQuery else cleanQuery
                val matchIdx = normalizedText.indexOf(targetTerm, ignoreCase = true).coerceAtLeast(0)
                val start = (matchIdx - 28).coerceAtLeast(0)
                val end = (matchIdx + targetTerm.length + 36).coerceAtMost(normalizedText.length)
                val rawSnippet = normalizedText.substring(start, end).replace("\n", " ").trim()
                val snippet = if (usedReversed) {
                    // Restore readable order for snippet display
                    rawSnippet.replace(reversedQuery, cleanQuery)
                } else {
                    rawSnippet
                }
                results.add(
                    SearchResult(
                        pageNumber = index + 1,
                        matchCount = matchCount,
                        snippet = snippet,
                        matchedTerm = cleanQuery
                    )
                )
            }
        }
        results
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = haystack.indexOf(needle, ignoreCase = true)
        while (idx != -1) {
            count++
            idx = haystack.indexOf(needle, idx + needle.length, ignoreCase = true)
        }
        return count
    }

    private fun containsArabic(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' || it in '\uFE70'..'\uFEFF' }
    }

    private fun normalizeArabicForSearch(input: String): String {
        val nfkc = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            when {
                ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640' -> Unit // strip diacritics & tatweel
                ch == '\u0622' || ch == '\u0623' || ch == '\u0625' -> sb.append('\u0627') // normalize alef forms
                ch == '\u0649' -> sb.append('\u064A') // alef maksura -> ya
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace("\\s+".toRegex(), " ").trim()
    }

    private data class RawPdfObj(
        val id: Int,
        val header: String,
        val rawBytes: ByteArray,
        val streamStart: Int,
        val streamEnd: Int
    ) {
        fun getDecompressedStream(): ByteArray? {
            if (streamStart < 0 || streamEnd <= streamStart || streamEnd > rawBytes.size) return null
            val slice = rawBytes.copyOfRange(streamStart, streamEnd)
            val isFlate = header.contains("/FlateDecode") || (slice.size >= 2 && slice[0] == 0x78.toByte())
            if (!isFlate) return slice

            return try {
                val inflater = java.util.zip.Inflater()
                inflater.setInput(slice)
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var total = 0
                while (!inflater.finished() && total < 4 * 1024 * 1024) {
                    val count = inflater.inflate(buf)
                    if (count == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) break
                    } else {
                        out.write(buf, 0, count)
                        total += count
                    }
                }
                inflater.end()
                out.toByteArray()
            } catch (_: Exception) {
                slice
            }
        }
    }

    private fun parsePdfStreams(): List<String> {
        if (hasParsedAllText) {
            return (0 until pageCount).map { textCache[it] ?: "" }
        }

        try {
            val fileBytes = file.readBytes()
            val latin = String(fileBytes, Charsets.ISO_8859_1)
            val objects = scanPdfObjects(latin, fileBytes)
            val globalCMap = buildGlobalToUnicodeMap(objects)

            // Resolve ordered page object IDs from /Pages tree
            val orderedPageIds = resolveOrderedPageIds(objects)
            val pageTexts = mutableListOf<String>()

            if (orderedPageIds.isNotEmpty()) {
                for (pageId in orderedPageIds) {
                    val pageObj = objects[pageId]
                    if (pageObj == null) {
                        pageTexts.add("")
                        continue
                    }
                    val contentIds = extractContentIds(pageObj.header)
                    val sb = StringBuilder()
                    for (cid in contentIds) {
                        val contentBytes = objects[cid]?.getDecompressedStream() ?: continue
                        val streamStr = String(contentBytes, Charsets.ISO_8859_1)
                        val extracted = extractTextFromStream(streamStr, globalCMap)
                        if (extracted.isNotBlank()) {
                            if (sb.isNotEmpty()) sb.append("\n")
                            sb.append(extracted)
                        }
                    }
                    pageTexts.add(sb.toString().trim())
                }
            }

            // Fallback if /Pages tree traversal didn't yield pages
            if (pageTexts.isEmpty()) {
                objects.values.forEach { obj ->
                    val streamBytes = obj.getDecompressedStream() ?: return@forEach
                    val streamStr = String(streamBytes, Charsets.ISO_8859_1)
                    if (streamStr.contains("BT") && streamStr.contains("ET")) {
                        val extracted = extractTextFromStream(streamStr, globalCMap)
                        if (extracted.isNotBlank()) {
                            pageTexts.add(extracted)
                        }
                    }
                }
            }

            for (i in 0 until pageCount) {
                textCache[i] = pageTexts.getOrElse(i) { "" }
            }
            hasParsedAllText = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return (0 until pageCount).map { textCache[it] ?: "" }
    }

    private fun scanPdfObjects(latin: String, rawBytes: ByteArray): Map<Int, RawPdfObj> {
        val result = LinkedHashMap<Int, RawPdfObj>()
        val objHeaderRegex = Regex("""(\d+)\s+\d+\s+obj\b""")
        val matches = objHeaderRegex.findAll(latin).toList()

        for (i in matches.indices) {
            val match = matches[i]
            val id = match.groupValues[1].toIntOrNull() ?: continue
            val bodyStart = match.range.last + 1
            val nextStart = if (i + 1 < matches.size) matches[i + 1].range.first else latin.length
            val endObjIdx = latin.indexOf("endobj", bodyStart).let {
                if (it == -1 || it > nextStart) nextStart else it
            }

            val streamKeywordIdx = latin.indexOf("stream", bodyStart)
            if (streamKeywordIdx != -1 && streamKeywordIdx < endObjIdx) {
                val dictPart = latin.substring(bodyStart, streamKeywordIdx)
                var dataStart = streamKeywordIdx + 6
                if (dataStart < endObjIdx && latin[dataStart] == '\r') dataStart++
                if (dataStart < endObjIdx && latin[dataStart] == '\n') dataStart++

                val endStreamIdx = latin.indexOf("endstream", dataStart)
                val dataEnd = if (endStreamIdx != -1 && endStreamIdx <= endObjIdx) {
                    var e = endStreamIdx
                    if (e > dataStart && latin[e - 1] == '\n') e--
                    if (e > dataStart && latin[e - 1] == '\r') e--
                    e
                } else {
                    dataStart
                }
                result[id] = RawPdfObj(id, dictPart, rawBytes, dataStart, dataEnd)
            } else {
                val dictPart = latin.substring(bodyStart, endObjIdx)
                result[id] = RawPdfObj(id, dictPart, rawBytes, -1, -1)
            }
        }
        return result
    }

    private fun resolveOrderedPageIds(objects: Map<Int, RawPdfObj>): List<Int> {
        // Locate root /Pages object (no /Parent or referenced by /Catalog /Pages X 0 R)
        var rootPagesId: Int? = null
        val pagesRefRegex = Regex("""/Pages\s+(\d+)\s+\d+\s+R""")
        for (obj in objects.values) {
            if (obj.header.contains("/Catalog")) {
                val m = pagesRefRegex.find(obj.header)
                if (m != null) {
                    rootPagesId = m.groupValues[1].toIntOrNull()
                    break
                }
            }
        }
        if (rootPagesId == null) {
            rootPagesId = objects.values.firstOrNull {
                it.header.contains("/Type") && it.header.contains("/Pages") && !it.header.contains("/Parent")
            }?.id
        }

        val ordered = mutableListOf<Int>()
        val visited = HashSet<Int>()
        val kidsRegex = Regex("""/Kids\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        val refRegex = Regex("""(\d+)\s+\d+\s+R""")

        fun traverse(nodeId: Int) {
            if (!visited.add(nodeId)) return
            val obj = objects[nodeId] ?: return
            val kidsMatch = kidsRegex.find(obj.header)
            if (kidsMatch != null) {
                val kidsStr = kidsMatch.groupValues[1]
                refRegex.findAll(kidsStr).forEach { ref ->
                    val childId = ref.groupValues[1].toIntOrNull()
                    if (childId != null) traverse(childId)
                }
            } else if (obj.header.contains("/Page")) {
                ordered.add(nodeId)
            }
        }

        if (rootPagesId != null) {
            traverse(rootPagesId)
        }
        return ordered
    }

    private fun extractContentIds(pageHeader: String): List<Int> {
        val ids = mutableListOf<Int>()
        val arrayMatch = Regex("""/Contents\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(pageHeader)
        if (arrayMatch != null) {
            Regex("""(\d+)\s+\d+\s+R""").findAll(arrayMatch.groupValues[1]).forEach {
                it.groupValues[1].toIntOrNull()?.let { id -> ids.add(id) }
            }
            return ids
        }
        val singleMatch = Regex("""/Contents\s+(\d+)\s+\d+\s+R""").find(pageHeader)
        singleMatch?.groupValues?.get(1)?.toIntOrNull()?.let { ids.add(it) }
        return ids
    }

    private fun buildGlobalToUnicodeMap(objects: Map<Int, RawPdfObj>): Map<String, String> {
        val map = HashMap<String, String>()
        val bfCharRegex = Regex("""<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>""")
        val bfRangeBlockRegex = Regex("""beginbfrange(.*?)endbfrange""", RegexOption.DOT_MATCHES_ALL)
        val bfRangeSimpleRegex = Regex("""<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>""")

        for (obj in objects.values) {
            if (obj.streamStart < 0) continue
            val bytes = obj.getDecompressedStream() ?: continue
            val content = String(bytes, Charsets.ISO_8859_1)
            if (!content.contains("beginbfchar") && !content.contains("beginbfrange")) continue

            val bfCharBlockRegex = Regex("""beginbfchar(.*?)endbfchar""", RegexOption.DOT_MATCHES_ALL)
            bfCharBlockRegex.findAll(content).forEach { block ->
                bfCharRegex.findAll(block.groupValues[1]).forEach { pair ->
                    val src = pair.groupValues[1].uppercase()
                    val dst = decodeUtf16Hex(pair.groupValues[2])
                    if (dst.isNotEmpty()) map[src] = dst
                }
            }

            bfRangeBlockRegex.findAll(content).forEach { block ->
                bfRangeSimpleRegex.findAll(block.groupValues[1]).forEach { rng ->
                    val startCode = rng.groupValues[1].toIntOrNull(16) ?: return@forEach
                    val endCode = rng.groupValues[2].toIntOrNull(16) ?: return@forEach
                    val dstStart = rng.groupValues[3].toIntOrNull(16) ?: return@forEach
                    val width = rng.groupValues[1].length
                    if (endCode - startCode in 0..512) {
                        for (offset in 0..(endCode - startCode)) {
                            val srcHex = (startCode + offset).toString(16).uppercase().padStart(width, '0')
                            val unicode = String(Character.toChars(dstStart + offset))
                            map[srcHex] = unicode
                        }
                    }
                }
            }
        }
        return map
    }

    private fun extractTextFromStream(streamContent: String, cMap: Map<String, String>): String {
        val sb = StringBuilder()
        var idx = 0
        while (idx < streamContent.length) {
            val bt = streamContent.indexOf("BT", idx)
            if (bt == -1) break
            val et = streamContent.indexOf("ET", bt + 2)
            if (et == -1) break
            val block = streamContent.substring(bt + 2, et)
            val parsed = parseTextOperators(block, cMap)
            if (parsed.isNotBlank()) {
                sb.append(parsed).append("\n")
            }
            idx = et + 2
        }
        return sb.toString().trim()
    }

    private fun parseTextOperators(block: String, cMap: Map<String, String>): String {
        val sb = StringBuilder()

        val textLiteralPattern = Pattern.compile("\\(([^)]*)\\)\\s*(?:Tj|'|\")")
        val literalMatcher = textLiteralPattern.matcher(block)
        while (literalMatcher.find()) {
            val str = literalMatcher.group(1) ?: ""
            sb.append(cleanPdfString(str)).append(" ")
        }

        val hexPattern = Pattern.compile("<([0-9a-fA-F]+)>\\s*Tj")
        val hexMatcher = hexPattern.matcher(block)
        while (hexMatcher.find()) {
            val hex = hexMatcher.group(1) ?: ""
            sb.append(decodeHexWithCMap(hex, cMap)).append(" ")
        }

        val arrayPattern = Pattern.compile("\\[(.*?)\\]\\s*TJ", Pattern.DOTALL)
        val arrayMatcher = arrayPattern.matcher(block)
        while (arrayMatcher.find()) {
            val arrayContent = arrayMatcher.group(1) ?: ""
            val tokenMatcher = Pattern.compile("\\(([^)]*)\\)|<([0-9a-fA-F]+)>").matcher(arrayContent)
            while (tokenMatcher.find()) {
                val lit = tokenMatcher.group(1)
                val hex = tokenMatcher.group(2)
                if (lit != null) {
                    sb.append(cleanPdfString(lit))
                } else if (hex != null) {
                    sb.append(decodeHexWithCMap(hex, cMap))
                }
            }
            sb.append(" ")
        }

        return sb.toString().trim()
    }

    private fun decodeHexWithCMap(hex: String, cMap: Map<String, String>): String {
        val upper = hex.uppercase()
        if (cMap.isNotEmpty()) {
            // Try 4-hex-digit (2-byte CID) chunks first, then 2-hex-digit (1-byte) chunks
            if (upper.length >= 4 && upper.length % 4 == 0) {
                val sb = StringBuilder()
                var matchedAny = false
                for (i in 0 until upper.length step 4) {
                    val code = upper.substring(i, i + 4)
                    val mapped = cMap[code]
                    if (mapped != null) {
                        sb.append(mapped)
                        matchedAny = true
                    } else {
                        sb.append(decodeUtf16Hex(code))
                    }
                }
                if (matchedAny) return sb.toString()
            }
            if (upper.length >= 2 && upper.length % 2 == 0) {
                val sb = StringBuilder()
                var matchedAny = false
                for (i in 0 until upper.length step 2) {
                    val code = upper.substring(i, i + 2)
                    val mapped = cMap[code]
                    if (mapped != null) {
                        sb.append(mapped)
                        matchedAny = true
                    }
                }
                if (matchedAny) return sb.toString()
            }
        }
        return decodeUtf16Hex(upper)
    }

    private fun decodeUtf16Hex(hex: String): String {
        return try {
            val padded = if (hex.length % 2 != 0) "0$hex" else hex
            val bytes = ByteArray(padded.length / 2)
            for (i in 0 until padded.length step 2) {
                bytes[i / 2] = padded.substring(i, i + 2).toInt(16).toByte()
            }
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            } else if (bytes.size >= 2 && bytes.size % 2 == 0) {
                String(bytes, Charsets.UTF_16BE)
            } else {
                String(bytes, Charsets.ISO_8859_1)
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun cleanPdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
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
        val matchCount: Int = 1,
        val snippet: String,
        val matchedTerm: String = ""
    )
}
