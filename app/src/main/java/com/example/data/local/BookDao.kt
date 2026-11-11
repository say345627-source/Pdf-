package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC LIMIT :limit")
    fun getRecentBooksFlow(limit: Int): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC LIMIT :limit")
    suspend fun getRecentBooksSync(limit: Int): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookByIdFlow(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC LIMIT 1")
    suspend fun getMostRecentBook(): BookEntity?

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR originalFileName LIKE '%' || :query || '%' ORDER BY lastReadTimestamp DESC")
    fun searchBooksFlow(query: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET lastReadPage = :page, totalPages = :totalPages, progressPercent = :progress, scrollOffset = :scrollOffset, lastReadTimestamp = :timestamp, lastSnippet = :snippet WHERE id = :id")
    suspend fun updateProgress(
        id: Long,
        page: Int,
        totalPages: Int,
        progress: Float,
        scrollOffset: Int,
        timestamp: Long,
        snippet: String?
    )

    @Query("UPDATE books SET title = :newTitle WHERE id = :id")
    suspend fun renameBook(id: Long, newTitle: String)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)
}
