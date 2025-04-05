package com.example.nativedemo.dto

import java.io.Serializable

data class ProductDTO(
    val id: Long,
    val name: String,
    val price: Double,
    val description: String,
    val category: String
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
