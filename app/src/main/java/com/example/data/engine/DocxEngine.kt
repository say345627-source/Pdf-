package com.example.data.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import com.example.data.model.DocElement
import com.example.data.model.DocPage
import com.example.data.model.TextRun
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object DocxEngine {

    fun parseDocument(file: File): List<DocElement> {
        return try {
            val extension = file.extension.lowercase()
            if (extension == "doc") {
                parseLegacyDoc(file)
            } else {
                parseDocx(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(
                DocElement.Notice("تعذر قراءة المستند بالشكل الكامل: ${e.localizedMessage ?: "خطأ غير متوقع"}"),
                DocElement.Paragraph("يرجى التأكد من سلامة الملف أو إعادة حفظه بصيغة DOCX صالحة.")
            )
        }
    }

    private fun parseDocx(file: File): List<DocElement> {
        val elements = mutableListOf<DocElement>()
        val zipFile = ZipFile(file)

        try {
            // Read relationships to map rId -> media path
            val relsMap = parseRelationships(zipFile)

            // Cache images in memory safely with downsampling to avoid OOM on large photos
            val imagesCache = mutableMapOf<String, Bitmap>()
            for ((rId, target) in relsMap) {
                if (target.contains("media/", ignoreCase = true)) {
                    val entryName = if (target.startsWith("word/")) target else "word/$target"
                    val entry = zipFile.getEntry(entryName)
                    if (entry != null) {
                        zipFile.getInputStream(entry).use { stream ->
                            val bytes = stream.readBytes()
                            val bitmap = decodeSampledBitmap(bytes, maxDim = 1080)
                            if (bitmap != null) {
                                imagesCache[rId] = bitmap
                            }
                        }
                    }
                }
            }

            val docEntry: ZipEntry? = zipFile.getEntry("word/document.xml")
            if (docEntry == null) {
                return listOf(DocElement.Notice("ملف DOCX غير صالح: لم يتم العثور على محتوى المستند."))
            }

            zipFile.getInputStream(docEntry).use { xmlStream ->
                parseDocumentXml(xmlStream, imagesCache, elements)
            }
        } finally {
            zipFile.close()
        }

        if (elements.isEmpty()) {
            elements.add(DocElement.Paragraph("المستند فارغ."))
        }
        return elements
    }

    private fun parseRelationships(zipFile: ZipFile): Map<String, String> {
        val relsMap = mutableMapOf<String, String>()
        val relsEntry = zipFile.getEntry("word/_rels/document.xml.rels") ?: return relsMap

        try {
            zipFile.getInputStream(relsEntry).use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                        val id = parser.getAttributeValue(null, "Id")
                        val target = parser.getAttributeValue(null, "Target")
                        if (id != null && target != null) {
                            relsMap[id] = target
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return relsMap
    }

    private fun parseDocumentXml(
        stream: InputStream,
        imagesCache: Map<String, Bitmap>,
        elements: MutableList<DocElement>
    ) {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "p" -> {
                        val pElement = parseParagraph(parser, imagesCache)
                        if (pElement != null) {
                            elements.add(pElement)
                        }
                    }
                    "tbl" -> {
                        val table = parseTable(parser)
                        if (table != null) {
                            elements.add(table)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseParagraph(parser: XmlPullParser, imagesCache: Map<String, Bitmap>): DocElement? {
        var isHeading = false
        var headingLevel = 1
        var isRtl = false
        var isListItem = false
        val runs = mutableListOf<TextRun>()
        val pText = StringBuilder()
        var embeddedImageBitmap: Bitmap? = null

        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "p" -> depth++
                        "pStyle" -> {
                            val styleVal = parser.getAttributeValue(null, "val") ?: ""
                            if (styleVal.contains("Heading1", ignoreCase = true) || styleVal.contains("Title", ignoreCase = true)) {
                                isHeading = true
                                headingLevel = 1
                            } else if (styleVal.contains("Heading2", ignoreCase = true)) {
                                isHeading = true
                                headingLevel = 2
                            } else if (styleVal.contains("Heading3", ignoreCase = true)) {
                                isHeading = true
                                headingLevel = 3
                            }
                        }
                        "bidi", "rtl" -> {
                            isRtl = true
                        }
                        "numPr" -> {
                            isListItem = true
                        }
                        "blip" -> {
                            val embed = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                                ?: parser.getAttributeValue(null, "embed")
                            if (embed != null && imagesCache.containsKey(embed)) {
                                embeddedImageBitmap = imagesCache[embed]
                            }
                        }
                        "r" -> {
                            val run = parseRun(parser)
                            if (run.text.isNotEmpty()) {
                                runs.add(run)
                                pText.append(run.text)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p") {
                        depth--
                    }
                }
            }
        }

        if (embeddedImageBitmap != null) {
            return DocElement.ImageItem(embeddedImageBitmap, pText.toString().trim())
        }

        val fullText = pText.toString().trim()
        if (fullText.isEmpty()) {
            return null
        }

        // Detect Arabic characters for automatic RTL
        val arabicCharCount = fullText.count { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
        if (arabicCharCount > 0) {
            isRtl = true
        }

        return when {
            isHeading -> DocElement.Heading(fullText, headingLevel, isRtl)
            isListItem -> DocElement.ListItem(fullText, isRtl)
            else -> DocElement.Paragraph(fullText, runs, isRtl)
        }
    }

    private fun parseRun(parser: XmlPullParser): TextRun {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var hexColor: String? = null
        val runText = StringBuilder()

        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "r" -> depth++
                        "b" -> isBold = true
                        "i" -> isItalic = true
                        "u" -> isUnderline = true
                        "color" -> hexColor = parser.getAttributeValue(null, "val")
                        "t" -> {
                            val text = parser.nextText()
                            runText.append(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "r") {
                        depth--
                    }
                }
            }
        }

        return TextRun(
            text = runText.toString(),
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            hexColor = hexColor
        )
    }

    private fun parseTable(parser: XmlPullParser): DocElement.Table? {
        val rows = mutableListOf<List<String>>()
        var depth = 1

        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "tbl" -> depth++
                        "tr" -> {
                            val cells = parseTableRow(parser)
                            if (cells.isNotEmpty()) {
                                rows.add(cells)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "tbl") {
                        depth--
                    }
                }
            }
        }

        return if (rows.isNotEmpty()) DocElement.Table(rows) else null
    }

    private fun parseTableRow(parser: XmlPullParser): List<String> {
        val cells = mutableListOf<String>()
        var depth = 1

        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "tr" -> depth++
                        "tc" -> {
                            val cellText = parseTableCell(parser)
                            cells.add(cellText)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "tr") {
                        depth--
                    }
                }
            }
        }
        return cells
    }

    private fun parseTableCell(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1

        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "tc" -> depth++
                        "t" -> {
                            val text = parser.nextText()
                            sb.append(text).append(" ")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "tc") {
                        depth--
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    /**
     * Parses legacy binary .doc format safely extracting textual segments
     * and prepending clear technical limitations notice as specified in Section 6.
     */
    private fun parseLegacyDoc(file: File): List<DocElement> {
        val elements = mutableListOf<DocElement>()
        elements.add(
            DocElement.Notice(
                "تم فتح المستند بصيغة DOC القديمة (Compound Binary Format). تم استخراج النصوص الأساسية بدقة. لعرض الجداول المعقدة والتنسيقات المصممة بدقة، يُوصى بحفظ الملف بصيغة DOCX الحديثة."
            )
        )

        val bytes = FileInputStream(file).use { it.readBytes() }
        val extractedStrings = extractCleanStringsFromBinary(bytes)

        if (extractedStrings.isEmpty()) {
            elements.add(DocElement.Paragraph("لم يتم العثور على نصوص قابلة للقراءة في هذا الملف."))
        } else {
            var isFirst = true
            for (paragraph in extractedStrings) {
                if (isFirst && paragraph.length < 80) {
                    elements.add(DocElement.Heading(paragraph, level = 1, isRtl = isArabic(paragraph)))
                    isFirst = false
                } else {
                    elements.add(DocElement.Paragraph(paragraph, isRtl = isArabic(paragraph)))
                }
            }
        }

        return elements
    }

    private fun extractCleanStringsFromBinary(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()

        var i = 0
        while (i < bytes.size - 1) {
            val b1 = bytes[i].toInt() and 0xFF
            val b2 = bytes[i + 1].toInt() and 0xFF

            // Check UTF-16LE characters (common in Word binary streams)
            val charCode = (b2 shl 8) or b1
            val char = charCode.toChar()

            if (char in '\u0020'..'\u007E' || char in '\u0600'..'\u06FF' || char in '\u0750'..'\u077F') {
                current.append(char)
                i += 2
                continue
            } else if (char == '\n' || char == '\r') {
                if (current.length >= 15) {
                    val cleaned = current.toString().trim()
                    if (cleaned.isNotBlank()) {
                        result.add(cleaned)
                    }
                }
                current.clear()
                i += 2
                continue
            }

            // Fallback: check 1-byte ASCII / Windows-1256
            if (b1 in 32..126) {
                current.append(b1.toChar())
            } else if (b1 == 10 || b1 == 13) {
                if (current.length >= 15) {
                    val cleaned = current.toString().trim()
                    if (cleaned.isNotBlank()) {
                        result.add(cleaned)
                    }
                }
                current.clear()
            }
            i++
        }

        if (current.length >= 15) {
            val cleaned = current.toString().trim()
            if (cleaned.isNotBlank()) {
                result.add(cleaned)
            }
        }

        return result
    }

    private fun isArabic(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
    }

    fun paginateElements(elements: List<DocElement>, docTitle: String): List<DocPage> {
        if (elements.isEmpty()) {
            return listOf(DocPage(1, listOf(DocElement.Paragraph("المستند فارغ.")), docTitle, docTitle))
        }

        val pages = mutableListOf<DocPage>()
        var currentPageElements = mutableListOf<DocElement>()
        var currentWeight = 0
        var currentChapter = docTitle

        for (elem in elements) {
            val elemWeight = when (elem) {
                is DocElement.Heading -> {
                    if (elem.level == 1) currentChapter = elem.text
                    150 + elem.text.length
                }
                is DocElement.Paragraph -> 50 + elem.text.length
                is DocElement.ListItem -> 40 + elem.text.length
                is DocElement.Table -> 300 + (elem.rows.size * 60)
                is DocElement.ImageItem -> 400
                is DocElement.Notice -> 150
            }

            val isHeading1 = (elem is DocElement.Heading && elem.level == 1 && currentPageElements.isNotEmpty())
            val wouldExceedPage = (currentWeight + elemWeight > 1050 && currentPageElements.isNotEmpty())

            if (isHeading1 || wouldExceedPage) {
                pages.add(
                    DocPage(
                        pageNumber = pages.size + 1,
                        elements = currentPageElements.toList(),
                        headerTitle = docTitle,
                        runningChapter = currentChapter
                    )
                )
                currentPageElements = mutableListOf()
                currentWeight = 0
            }

            currentPageElements.add(elem)
            currentWeight += elemWeight
        }

        if (currentPageElements.isNotEmpty()) {
            pages.add(
                DocPage(
                    pageNumber = pages.size + 1,
                    elements = currentPageElements.toList(),
                    headerTitle = docTitle,
                    runningChapter = currentChapter
                )
            )
        }

        return pages
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            var sampleSize = 1
            val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxSide / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }
}
