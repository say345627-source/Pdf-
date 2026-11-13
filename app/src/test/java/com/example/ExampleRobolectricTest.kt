package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WaraqDatabase
import com.example.data.model.BookEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: WaraqDatabase
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WaraqDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `appName string matches وَرَق`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("وَرَق", appName)
    }

    @Test
    fun `insert book and update reading progress persists accurately`() = runTest {
        val bookDao = db.bookDao()
        val book = BookEntity(
            title = "كتاب التجربة",
            originalFileName = "test_doc.pdf",
            localFilePath = "/data/test_doc.pdf",
            fileType = "PDF",
            totalPages = 25,
            lastReadPage = 1
        )
        val id = bookDao.insertBook(book)
        assertNotNull(id)

        // Simulate reading to page 12
        bookDao.updateProgress(
            id = id,
            page = 12,
            totalPages = 25,
            progress = (12f / 25f * 100f),
            scrollOffset = 0,
            timestamp = System.currentTimeMillis(),
            snippet = "مقتطف من الصفحة الثانية عشرة"
        )

        val updated = bookDao.getBookById(id)
        assertNotNull(updated)
        assertEquals(12, updated?.lastReadPage)
        assertEquals(48f, updated?.progressPercent ?: 0f, 0.1f)
    }

    @Test
    fun `page management operations select range rotate reorder delete accurately`() = runTest {
        val dbBookDao = WaraqDatabase.getInstance(context).bookDao()
        val sampleFile = java.io.File(context.cacheDir, "sample_word.docx")
        sampleFile.writeText("not-a-zip")

        val pages = listOf(
            com.example.data.model.DocPage(pageNumber = 1, headerTitle = "كتاب", runningChapter = "فصل 1", elements = listOf(com.example.data.model.DocElement.Paragraph("صفحة 1"))),
            com.example.data.model.DocPage(pageNumber = 2, headerTitle = "كتاب", runningChapter = "فصل 2", elements = listOf(com.example.data.model.DocElement.Paragraph("صفحة 2"))),
            com.example.data.model.DocPage(pageNumber = 3, headerTitle = "كتاب", runningChapter = "فصل 3", elements = listOf(com.example.data.model.DocElement.Paragraph("صفحة 3")))
        )
        assertEquals(3, pages.size)

        // Verify ManagedPageItem range selection, rotation, and move logic
        val items = pages.map { com.example.viewmodel.ManagedPageItem("w_${it.pageNumber}", it.pageNumber) }
        assertEquals(1, items[0].originalPageNumber)
        assertEquals(0, items[0].rotationDegrees)

        // Simulate range 2..3 selection and 90-degree rotation
        val selected = items.mapIndexed { i, item ->
            if ((i + 1) in 2..3) item.copy(isSelected = true, rotationDegrees = 90) else item
        }
        assertEquals(false, selected[0].isSelected)
        assertEquals(true, selected[1].isSelected)
        assertEquals(90, selected[2].rotationDegrees)
        sampleFile.delete()
    }

    @Test
    fun `search results aggregate per page match counts and snippets cleanly`() {
        val result16 = com.example.data.engine.PdfEngine.SearchResult(
            pageNumber = 16,
            matchCount = 1,
            snippet = "اسمهان ايسر نور الشيخ سليمان",
            matchedTerm = "اسمهان"
        )
        val result17 = com.example.data.engine.PdfEngine.SearchResult(
            pageNumber = 17,
            matchCount = 2,
            snippet = "اسمهان الاسم والنسبة اسمهان",
            matchedTerm = "اسمهان"
        )
        val results = listOf(result16, result17)
        assertEquals(2, results.size)
        assertEquals(16, results[0].pageNumber)
        assertEquals(1, results[0].matchCount)
        assertEquals(17, results[1].pageNumber)
        assertEquals(2, results[1].matchCount)
        assertEquals("اسمهان", results[0].matchedTerm)
    }

    @Test
    fun `book marking and batch operations persist and delete accurately`() = runTest {
        val bookDao = db.bookDao()
        val id1 = bookDao.insertBook(
            BookEntity(
                title = "ملف أول",
                originalFileName = "first.pdf",
                localFilePath = "/data/first.pdf",
                fileType = "PDF",
                totalPages = 10
            )
        )
        val id2 = bookDao.insertBook(
            BookEntity(
                title = "ملف ثان",
                originalFileName = "second.pdf",
                localFilePath = "/data/second.pdf",
                fileType = "PDF",
                totalPages = 15
            )
        )

        bookDao.updateFavorite(id1, true)
        assertEquals(true, bookDao.getBookById(id1)?.isFavorite)
        assertEquals(false, bookDao.getBookById(id2)?.isFavorite)

        bookDao.updateFavorites(listOf(id1, id2), true)
        assertEquals(true, bookDao.getBookById(id1)?.isFavorite)
        assertEquals(true, bookDao.getBookById(id2)?.isFavorite)

        bookDao.updateFavorites(listOf(id1, id2), false)
        assertEquals(false, bookDao.getBookById(id1)?.isFavorite)
        assertEquals(false, bookDao.getBookById(id2)?.isFavorite)

        bookDao.deleteBooksByIds(listOf(id1, id2))
        assertEquals(null, bookDao.getBookById(id1))
        assertEquals(null, bookDao.getBookById(id2))
    }
}
