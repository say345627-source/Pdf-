package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val originalFileName: String,
    val localFilePath: String, // Path in app's internal storage for guaranteed zero-failure persistence
    val originalUriString: String? = null,
    val fileType: String, // "PDF", "DOCX", "DOC"
    val fileSizeBytes: Long = 0L,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val dateAddedTimestamp: Long = System.currentTimeMillis(),
    val lastReadPage: Int = 1, // 1-indexed for PDF, paragraph index for DOCX
    val totalPages: Int = 1,
    val progressPercent: Float = 0f,
    val scrollOffset: Int = 0,
    val coverImagePath: String? = null,
    val isFavorite: Boolean = false,
    val lastSnippet: String? = null
)
