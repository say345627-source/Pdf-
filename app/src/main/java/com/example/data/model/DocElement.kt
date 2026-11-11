package com.example.data.model

import android.graphics.Bitmap

sealed class DocElement {
    data class Heading(
        val text: String,
        val level: Int = 1,
        val isRtl: Boolean = true
    ) : DocElement()

    data class Paragraph(
        val text: String,
        val runs: List<TextRun> = emptyList(),
        val isRtl: Boolean = true,
        val alignment: String = "START"
    ) : DocElement()

    data class ListItem(
        val text: String,
        val isRtl: Boolean = true,
        val isOrdered: Boolean = false,
        val number: Int = 1
    ) : DocElement()

    data class Table(
        val rows: List<List<String>>,
        val isRtl: Boolean = true
    ) : DocElement()

    data class ImageItem(
        val bitmap: Bitmap?,
        val caption: String = ""
    ) : DocElement()

    data class Notice(
        val message: String
    ) : DocElement()
}

data class TextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val hexColor: String? = null,
    val fontSizeSp: Float? = null
)
