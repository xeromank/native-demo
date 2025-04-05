package com.example.nativedemo.dto

import java.io.Serializable

data class UserDTO(
    val id: Long,
    val name: String,
    val email: String,
    val roles: List<String>
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
