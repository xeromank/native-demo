package com.example.nativedemo.service

import com.example.nativedemo.dto.UserDTO

interface UserService {
    fun getUsers(): List<UserDTO>
    fun getUserById(id: Long): UserDTO?
    fun createUser(user: UserDTO): UserDTO
}
