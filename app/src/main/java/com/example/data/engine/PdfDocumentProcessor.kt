package com.example.data.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.example.data.model.DocElement
import com.example.data.model.DocPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class PageOperationSpec(
    val originalPageIndex: Int, // 0-based
    val rotationDegrees: Int = 0 // 0, 90, 180, 270
)

object PdfDocumentProcessor {

    /**
     * Rebuilds or extracts a subset of PDF pages in the given order and rotations into [outputFile].
     * Uses Android's native PdfRenderer + PdfDocument, 100% offline.
     */
    suspend fun buildPdfFromSpec(
        sourcePdfFile: File,
        pagesSpec: List<PageOperationSpec>,
        outputFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pagesSpec.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("لم يتم تحديد أي صفحات"))
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outPdf = PdfDocument()

        try {
            outputFile.parentFile?.mkdirs()
            pfd = ParcelFileDescriptor.open(sourcePdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalSourcePages = renderer.pageCount

            val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            pagesSpec.forEachIndexed { idx, spec ->
                coroutineContext.ensureActive()
                val pageIdx = spec.originalPageIndex
                if (pageIdx !in 0 until totalSourcePages) return@forEachIndexed

                val srcPage = renderer.openPage(pageIdx)
                val baseW = srcPage.width.coerceAtLeast(100)
                val baseH = srcPage.height.coerceAtLeast(100)

                // Render at ~2x resolution (capped to prevent OOM on giant pages)
                val maxSide = 1650f
                val desiredScale = 2.0f
                val scaledW = (baseW * desiredScale)
                val scaledH = (baseH * desiredScale)
                val factor = if (maxOf(scaledW, scaledH) > maxSide) {
                    maxSide / maxOf(baseW.toFloat(), baseH.toFloat())
                } else {
                    desiredScale
                }

                val bmpW = (baseW * factor).toInt().coerceAtLeast(1)
                val bmpH = (baseH * factor).toInt().coerceAtLeast(1)

                val pageBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                try {
                    pageBitmap.eraseColor(Color.WHITE)
                    srcPage.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                } finally {
                    srcPage.close()
                }

                val normRot = ((spec.rotationDegrees % 360) + 360) % 360
                val swapDims = normRot == 90 || normRot == 270
                val outW = if (swapDims) baseH else baseW
                val outH = if (swapDims) baseW else baseH

                val pageInfo = PdfDocument.PageInfo.Builder(outW, outH, idx + 1).create()
                val outPage = outPdf.startPage(pageInfo)

                val matrix = Matrix()
                // Scale high-res bitmap down to PDF point size
                matrix.postScale(baseW.toFloat() / bmpW.toFloat(), baseH.toFloat() / bmpH.toFloat())
                // Center at origin, rotate, then move to center of target page
                matrix.postTranslate(-baseW / 2f, -baseH / 2f)
                if (normRot != 0) {
                    matrix.postRotate(normRot.toFloat())
                }
                matrix.postTranslate(outW / 2f, outH / 2f)

                outPage.canvas.drawColor(Color.WHITE)
                outPage.canvas.drawBitmap(pageBitmap, matrix, drawPaint)
                outPdf.finishPage(outPage)

                pageBitmap.recycle()
                onProgress?.invoke(idx + 1, pagesSpec.size)
            }

            FileOutputStream(outputFile).use { fos ->
                outPdf.writeTo(fos)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                outPdf.close()
            } catch (_: Exception) {
            }
            try {
                renderer?.close()
            } catch (_: Exception) {
            }
            try {
                pfd?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Exports selected Word/DOCX paginated pages to a cleanly formatted standalone A4 PDF file.
     */
    suspend fun exportWordPagesToPdf(
        documentTitle: String,
        pages: List<DocPage>,
        rotations: Map<Int, Int> = emptyMap(),
        outputFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("لم يتم تحديد أي صفحات"))
        }

        val outPdf = PdfDocument()
        try {
            outputFile.parentFile?.mkdirs()

            val a4W = 595
            val a4H = 842
            val marginH = 48f
            val contentW = (a4W - marginH * 2).toInt()

            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6B7570")
                textSize = 10f
            }
            val h1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#111613")
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
            }
            val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#007A43")
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E2420")
                textSize = 12.5f
            }
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#DCE2DE")
                strokeWidth = 0.8f
            }

            pages.forEachIndexed { idx, docPage ->
                coroutineContext.ensureActive()
                val rot = ((rotations[docPage.pageNumber] ?: 0) % 360 + 360) % 360
                val swap = rot == 90 || rot == 270
                val outW = if (swap) a4H else a4W
                val outH = if (swap) a4W else a4H

                val pageInfo = PdfDocument.PageInfo.Builder(outW, outH, idx + 1).create()
                val pdfPage = outPdf.startPage(pageInfo)
                val canvas = pdfPage.canvas
                canvas.drawColor(Color.WHITE)

                if (rot != 0) {
                    canvas.save()
                    canvas.translate(outW / 2f, outH / 2f)
                    canvas.rotate(rot.toFloat())
                    canvas.translate(-a4W / 2f, -a4H / 2f)
                }

                // Running header
                val headerText = docPage.runningChapter.ifBlank { documentTitle }
                drawRtlText(canvas, headerText, headerPaint, marginH, 34f, contentW)
                canvas.drawLine(marginH, 52f, a4W - marginH, 52f, dividerPaint)

                var yPos = 68f
                val maxBottomY = a4H - 58f

                for (elem in docPage.elements) {
                    if (yPos >= maxBottomY) break
                    when (elem) {
                        is DocElement.Heading -> {
                            val paint = if (elem.level == 1) h1Paint else h2Paint
                            yPos += drawRtlText(canvas, elem.text, paint, marginH, yPos, contentW) + 10f
                        }
                        is DocElement.Paragraph -> {
                            yPos += drawRtlText(canvas, elem.text, bodyPaint, marginH, yPos, contentW) + 8f
                        }
                        is DocElement.ListItem -> {
                            yPos += drawRtlText(canvas, "• ${elem.text}", bodyPaint, marginH, yPos, contentW) + 6f
                        }
                        is DocElement.Table -> {
                            elem.rows.forEach { row ->
                                if (yPos < maxBottomY) {
                                    val rowText = row.joinToString("  |  ")
                                    yPos += drawRtlText(canvas, rowText, bodyPaint, marginH, yPos, contentW) + 4f
                                }
                            }
                            yPos += 6f
                        }
                        is DocElement.Notice -> {
                            yPos += drawRtlText(canvas, elem.message, headerPaint, marginH, yPos, contentW) + 8f
                        }
                        is DocElement.ImageItem -> {
                            val bmp = elem.bitmap
                            if (bmp != null && !bmp.isRecycled && yPos + 120f < maxBottomY) {
                                val targetW = contentW.toFloat().coerceAtMost(320f)
                                val aspect = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
                                val targetH = (targetW * aspect).coerceAtMost(180f)
                                val left = marginH + (contentW - targetW) / 2f
                                val dstRect = android.graphics.RectF(left, yPos, left + targetW, yPos + targetH)
                                canvas.drawBitmap(bmp, null, dstRect, null)
                                yPos += targetH + 10f
                            }
                        }
                    }
                }

                // Running footer
                canvas.drawLine(marginH, a4H - 46f, a4W - marginH, a4H - 46f, dividerPaint)
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#6B7570")
                    textSize = 10f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("— ${idx + 1} من ${pages.size} —", a4W / 2f, a4H - 26f, footerPaint)

                if (rot != 0) {
                    canvas.restore()
                }

                outPdf.finishPage(pdfPage)
                onProgress?.invoke(idx + 1, pages.size)
            }

            FileOutputStream(outputFile).use { fos ->
                outPdf.writeTo(fos)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                outPdf.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun drawRtlText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int
    ): Float {
        if (text.isBlank()) return 0f
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setLineSpacing(2f, 1.15f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }
}
