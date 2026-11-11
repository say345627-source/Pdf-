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
}
