package com.example.nativedemo.dto

data class PostRequest(
    val title: String,
    val content: String,
    val authorId: Long
)

