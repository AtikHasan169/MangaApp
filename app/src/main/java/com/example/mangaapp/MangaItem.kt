package com.example.mangaapp

data class MangaItem(
    val title: String,
    val path: String,
    val coverUrl: String,
    // New fields for History/Library support
    var chapterTitle: String = "",
    var chapterUrl: String = "",
    var timestamp: Long = 0
)
