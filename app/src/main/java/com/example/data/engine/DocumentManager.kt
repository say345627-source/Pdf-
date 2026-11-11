package com.example.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.WaraqDatabase
import com.example.data.model.BookEntity
import com.example.widget.WaraqWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentManager(private val context: Context) {

    private val db = WaraqDatabase.getInstance(context)
    private val bookDao = db.bookDao()

    private val libraryDir: File
        get() {
            val dir = File(context.filesDir, "library")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val coversDir: File
        get() {
            val dir = File(context.filesDir, "covers")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Imports a document from a user-selected SAF Uri.
     */
    suspend fun importDocument(uri: Uri): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            // Attempt persistable permission
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Not all URIs support persistable flags (e.g. some third party file managers)
            }

            var displayName = "مستند_جديد"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val extension = displayName.substringAfterLast('.', "").lowercase()
            val fileType = when (extension) {
                "pdf" -> "PDF"
                "docx" -> "DOCX"
                "doc" -> "DOC"
                else -> {
                    // Try MIME type detection
                    val mime = context.contentResolver.getType(uri) ?: ""
                    when {
                        mime.contains("pdf", ignoreCase = true) -> "PDF"
                        mime.contains("wordprocessingml", ignoreCase = true) -> "DOCX"
                        mime.contains("msword", ignoreCase = true) -> "DOC"
                        else -> "PDF"
                    }
                }
            }

            // Save persistent local copy in library directory
            val uniqueId = UUID.randomUUID().toString().take(8)
            val cleanName = displayName.replace("[^a-zA-Z0-9._\\-\\u0600-\\u06FF]".toRegex(), "_")
            val targetFile = File(libraryDir, "${uniqueId}_$cleanName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("تعذر قراءة محتوى الملف المحدد"))

            if (fileSize == 0L) {
                fileSize = targetFile.length()
            }

            // Calculate total pages / sections and generate cover
            var totalPages = 1
            var coverPath: String? = null

            if (fileType == "PDF") {
                try {
                    val pdfEngine = PdfEngine(targetFile)
                    totalPages = pdfEngine.pageCount.coerceAtLeast(1)
                    val coverBitmap = pdfEngine.renderPage(0, 400)
                    if (coverBitmap != null) {
                        coverPath = saveCoverBitmap(coverBitmap, uniqueId)
                    }
                    pdfEngine.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    val elements = DocxEngine.parseDocument(targetFile)
                    totalPages = elements.size.coerceAtLeast(1)
                    val coverBitmap = generateAestheticCover(displayName, fileType)
                    coverPath = saveCoverBitmap(coverBitmap, uniqueId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Strip extension for title display
            val cleanTitle = displayName.substringBeforeLast('.')

            val book = BookEntity(
                title = cleanTitle,
                originalFileName = displayName,
                localFilePath = targetFile.absolutePath,
                originalUriString = uri.toString(),
                fileType = fileType,
                fileSizeBytes = fileSize,
                lastReadTimestamp = System.currentTimeMillis(),
                dateAddedTimestamp = System.currentTimeMillis(),
                lastReadPage = 1,
                totalPages = totalPages,
                progressPercent = if (totalPages > 0) (1f / totalPages * 100f).coerceAtMost(100f) else 0f,
                coverImagePath = coverPath
            )

            val id = bookDao.insertBook(book)
            val insertedBook = book.copy(id = id)

            // Update home screen widget
            WaraqWidgetProvider.updateAllWidgets(context)

            Result.success(insertedBook)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Saves rendered cover image to internal cache directory.
     */
    private fun saveCoverBitmap(bitmap: Bitmap, uniqueId: String): String {
        val coverFile = File(coversDir, "cover_$uniqueId.webp")
        FileOutputStream(coverFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, stream)
        }
        return coverFile.absolutePath
    }

    /**
     * Generates a sleek dark & green book cover for Word/DOCX or fallback.
     */
    private fun generateAestheticCover(title: String, fileType: String): Bitmap {
        val width = 360
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw dark charcoal background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#121A16")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw spine accent bar
        val spinePaint = Paint().apply {
            color = Color.parseColor("#39F58A")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 16f, height.toFloat(), spinePaint)

        // Draw inner border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#23332A")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(28f, 20f, width - 20f, height - 20f, borderPaint)

        // Draw File Type Badge
        val badgePaint = Paint().apply {
            color = if (fileType == "PDF") Color.parseColor("#FF5722") else Color.parseColor("#2979FF")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(45f, 40f, 130f, 75f, 8f, 8f, badgePaint)

        val badgeTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(fileType, 87.5f, 64f, badgeTextPaint)

        // Draw Title
        val titlePaint = Paint().apply {
            color = Color.parseColor("#F2F5F3")
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }

        val truncatedTitle = if (title.length > 35) title.take(32) + "..." else title
        val lines = truncatedTitle.chunked(18)
        var yPos = 160f
        for (line in lines) {
            canvas.drawText(line, (width - 45).toFloat(), yPos, titlePaint)
            yPos += 36f
        }

        // Draw WARAQ branding at bottom
        val brandPaint = Paint().apply {
            color = Color.parseColor("#9AA69F")
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("وَرَق • WARAQ", (width / 2).toFloat(), (height - 45).toFloat(), brandPaint)

        return bitmap
    }

    /**
     * Seeds two high quality sample documents on first run if database is empty:
     * 1. «مقدمة في فلسفة المعرفة والقراءة» (Arabic PDF)
     * 2. «دليل العمارة البرمجية الحديثة» (Arabic DOCX with headings, formatted runs, list, and table)
     */
    suspend fun seedSampleDocumentsIfEmpty() = withContext(Dispatchers.IO) {
        val count = bookDao.getRecentBooksSync(1).size
        if (count > 0) return@withContext

        try {
            // 1. Create Arabic PDF Sample Document
            val pdfFile = File(libraryDir, "sample_waraq_reading_guide.pdf")
            createSamplePdfDocument(pdfFile)

            val pdfEngine = PdfEngine(pdfFile)
            val pdfPages = pdfEngine.pageCount
            val pdfCover = pdfEngine.renderPage(0, 400)
            val pdfCoverPath = pdfCover?.let { saveCoverBitmap(it, "sample_pdf") }
            pdfEngine.close()

            val samplePdfBook = BookEntity(
                title = "مقدمة في فن القراءة المعمقة",
                originalFileName = "مقدمة_في_فن_القراءة_المعمقة.pdf",
                localFilePath = pdfFile.absolutePath,
                fileType = "PDF",
                fileSizeBytes = pdfFile.length(),
                lastReadTimestamp = System.currentTimeMillis() - 3600000,
                dateAddedTimestamp = System.currentTimeMillis() - 86400000,
                lastReadPage = 1,
                totalPages = pdfPages,
                progressPercent = (1f / pdfPages * 100f).coerceAtMost(100f),
                coverImagePath = pdfCoverPath,
                isFavorite = true,
                lastSnippet = "القراءة ليست مجرد استهلاك للمعلومات، بل حوار متجدد مع الفكر الإنساني..."
            )
            bookDao.insertBook(samplePdfBook)

            // 2. Create Arabic DOCX Sample Document
            val docxFile = File(libraryDir, "sample_software_architecture.docx")
            createSampleDocxDocument(docxFile)

            val docxCover = generateAestheticCover("العمارة البرمجية الحديثة للأندرويد", "DOCX")
            val docxCoverPath = saveCoverBitmap(docxCover, "sample_docx")

            val elements = DocxEngine.parseDocument(docxFile)
            val totalSections = elements.size.coerceAtLeast(1)

            val sampleDocxBook = BookEntity(
                title = "العمارة البرمجية الحديثة للأندرويد",
                originalFileName = "العمارة_البرمجية_الحديثة.docx",
                localFilePath = docxFile.absolutePath,
                fileType = "DOCX",
                fileSizeBytes = docxFile.length(),
                lastReadTimestamp = System.currentTimeMillis(),
                dateAddedTimestamp = System.currentTimeMillis() - 43200000,
                lastReadPage = 1,
                totalPages = totalSections,
                progressPercent = (1f / totalSections * 100f).coerceAtMost(100f),
                coverImagePath = docxCoverPath,
                isFavorite = false,
                lastSnippet = "مبادئ التصميم النظيف وأفضل الممارسات البرمجية وتطوير واجهات المستخدم بلغة Kotlin..."
            )
            bookDao.insertBook(sampleDocxBook)

            // Update widget immediately with initial samples
            WaraqWidgetProvider.updateAllWidgets(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createSamplePdfDocument(outputFile: File) {
        val document = PdfDocument()

        // Page 1
        val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = document.startPage(pageInfo1)
        val canvas1 = page1.canvas
        drawSamplePdfPage1(canvas1)
        document.finishPage(page1)

        // Page 2
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = document.startPage(pageInfo2)
        val canvas2 = page2.canvas
        drawSamplePdfPage2(canvas2)
        document.finishPage(page2)

        // Page 3
        val pageInfo3 = PdfDocument.PageInfo.Builder(595, 842, 3).create()
        val page3 = document.startPage(pageInfo3)
        val canvas3 = page3.canvas
        drawSamplePdfPage3(canvas3)
        document.finishPage(page3)

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun drawSamplePdfPage1(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint().apply {
            color = Color.parseColor("#121A16")
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }

        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#00874A")
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#263238")
            textSize = 13.5f
            textAlign = Paint.Align.RIGHT
        }

        val accentBarPaint = Paint().apply {
            color = Color.parseColor("#39F58A")
            style = Paint.Style.FILL
        }

        // Decorative header bar
        canvas.drawRect(550f, 60f, 560f, 130f, accentBarPaint)

        canvas.drawText("مقدمة في فن القراءة المعمقة", 535f, 90f, headerPaint)
        canvas.drawText("دراسة تحليلية في استيعاب الأفكار وتدوين الملاحظات", 535f, 118f, subHeaderPaint)

        val paragraphs = listOf(
            "تُعتبر القراءة واحدة من أرقى الممارسات العقلية التي طوّرها الإنسان عبر العصور.",
            "إنها ليست مجرد وسيلة لجمع المعلومات أو الترفيه العابر، بل هي جسر للتواصل الفكري المباشر مع أعظم العقول التي شهدها التاريخ الإنساني.",
            "عندما يقرأ الإنسان كتاباً بتأنٍ وتركيز، فإنه يعيد تشكيل مسارات تفكيره، ويوسع مداركه، ويكتسب القدرة على تحليل المشكلات برؤية أكثر شمولاً واتزاناً.",
            "ومن هنا تنبع أهمية قارئ المستندات الحديث «وَرَق»، الذي صُمم ليوفر للمتعلم والباحث بيئة هادئة خالية من المشتتات، تدعم متابعة موضع القراءة وحفظ التقدم التلقائي عبر جميع الأجهزة."
        )

        var y = 175f
        for (p in paragraphs) {
            val lines = chunkArabicText(p, 48)
            for (line in lines) {
                canvas.drawText(line, 540f, y, bodyPaint)
                y += 24f
            }
            y += 14f
        }

        // Footer
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("الصفحة ١ من ٣ • تطبيق وَرَق للقراءة", 297f, 800f, footerPaint)
    }

    private fun drawSamplePdfPage2(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint().apply {
            color = Color.parseColor("#121A16")
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#263238")
            textSize = 13f
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText("الفصل الثاني: استراتيجيات الحفظ واسترجاع المعلومات", 540f, 85f, headerPaint)

        val text = listOf(
            "١. تدوين الملاحظات التفاعلية وتحديد النصوص الجوهرية.",
            "٢. القراءة النقدية والتساؤل المستمر حول حجج الكاتب وبراهينه.",
            "٣. ربط المفاهيم المقروءة بالخبرات الواقعية والتجارب اليومية.",
            "٤. المراجعة الدورية السريعة عبر بطاقات التلخيص وأدوات المتابعة.",
            "تتيح ميزة التحديد والنسخ في تطبيق «وَرَق» استخراج الاقتباسات بكل سهولة ومشاركتها مع التطبيقات الأخرى أو حفظها في سجل الملاحظات الشخصية."
        )

        var y = 140f
        for (item in text) {
            val lines = chunkArabicText(item, 48)
            for (line in lines) {
                canvas.drawText(line, 540f, y, bodyPaint)
                y += 24f
            }
            y += 12f
        }

        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("الصفحة ٢ من ٣ • تطبيق وَرَق للقراءة", 297f, 800f, footerPaint)
    }

    private fun drawSamplePdfPage3(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint().apply {
            color = Color.parseColor("#121A16")
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#263238")
            textSize = 13f
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText("الفصل الثالث: خاتمة وتوصيات ختامية", 540f, 85f, headerPaint)

        val text = listOf(
            "ختاماً، إن القراءة المستمرة هي استثمار طويل الأجل لا يخيب أثره أبداً.",
            "احرص على تخصيص ثلاثين دقيقة يومياً للقراءة دون انقطاع، واستفد من ويدجت الشاشة الرئيسية للعودة المباشرة إلى كتابك المفضل بلمسة واحدة."
        )

        var y = 140f
        for (item in text) {
            val lines = chunkArabicText(item, 48)
            for (line in lines) {
                canvas.drawText(line, 540f, y, bodyPaint)
                y += 24f
            }
            y += 12f
        }

        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("الصفحة ٣ من ٣ • تطبيق وَرَق للقراءة", 297f, 800f, footerPaint)
    }

    private fun chunkArabicText(text: String, approxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            if (current.length + word.length + 1 > approxChars) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    /**
     * Builds a standard valid DOCX ZIP package with XML content.
     */
    private fun createSampleDocxDocument(outputFile: File) {
        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val documentRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>"""

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p>
      <w:pPr><w:pStyle w:val="Heading1"/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:b/><w:sz w:val="48"/><w:color w:val="39F58A"/></w:rPr><w:t>العمارة البرمجية الحديثة للأندرويد</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:bidi/></w:pPr>
      <w:r><w:rPr><w:i/><w:sz w:val="28"/><w:color w:val="9AA69F"/></w:rPr><w:t>دليل عملي لبناء تطبيقات فائقة الأداء باستخدام Jetpack Compose وRoom</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:pStyle w:val="Heading2"/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:b/><w:sz w:val="36"/></w:rPr><w:t>١. مقدمة في معمارية MVVM والتصميم النظيف</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:bidi/></w:pPr>
      <w:r><w:rPr><w:sz w:val="26"/></w:rPr><w:t>تعتمد معمارية التطبيقات الحديثة على فصل المهام والمسؤوليات (Separation of Concerns). حيث تتولى طبقة البيانات إدارة تدفق البيانات والتخزين المؤقت وحفظ التقدم المحلي بشكل دائم دون اعتماد على حالة واجهة المستخدم.</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:pStyle w:val="Heading2"/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:b/><w:sz w:val="36"/></w:rPr><w:t>٢. ركائز الأداء السريع في قارئ المستندات</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:numPr/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:sz w:val="26"/></w:rPr><w:t>• استخدام LruCache لتفادي تكرار معالجة صفحات المستند وخفض استهلاك الذاكرة العشوائية.</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:numPr/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:sz w:val="26"/></w:rPr><w:t>• المعالجة اللامتزامنة عبر Coroutines وDispatchers.IO خارج الخيط الرئيسي لمنع تجمد الشاشة.</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:numPr/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:sz w:val="26"/></w:rPr><w:t>• الحفظ الفوري الدقيق لموضع التمرير والفقرة والصفحة عند أي تغيير.</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:pStyle w:val="Heading2"/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:b/><w:sz w:val="36"/></w:rPr><w:t>٣. جدول مقارنة صيغ المستندات</w:t></w:r>
    </w:p>
    <w:tbl>
      <w:tr>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>الصيغة</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>المزايا الأساسية</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>دقة التخطيط</w:t></w:r></w:p></w:tc>
      </w:tr>
      <w:tr>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:t>PDF</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:t>تطابق كامل ومثالي للطباعة والعرض</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:t>ثابت بنسبة 100%</w:t></w:r></w:p></w:tc>
      </w:tr>
      <w:tr>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:t>DOCX</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:t>تدفق نصوص مرن ودعم التحكم بحجم الخط</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:pPr><w:bidi/></w:pPr><w:r><w:t>متكيف مع الشاشات</w:t></w:r></w:p></w:tc>
      </w:tr>
    </w:tbl>
    <w:p>
      <w:pPr><w:bidi/></w:pPr>
      <w:r><w:rPr><w:sz w:val="26"/></w:rPr><w:t>يمكن للمستخدم تحديد أي نص داخل هذا المستند ونسخه أو مشاركته فوراً عبر قائمة التحديد السريع في قارئ وَرَق.</w:t></w:r>
    </w:p>
  </w:body>
</w:document>"""

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(rootRelsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(documentRelsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }
}
