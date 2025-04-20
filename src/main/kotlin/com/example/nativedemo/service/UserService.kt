package com.example.nativedemo.service

import com.example.nativedemo.dto.UserDTO
import com.example.nativedemo.entity.User
import com.example.nativedemo.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getAllUsers(): List<User> = userRepository.findAll()

    @Transactional(readOnly = true)
    fun getUserById(id: Long): User = userRepository.findById(id)
        .orElseThrow { NoSuchElementException("User not found with id: $id") }

    @Transactional
    fun createUser(user: User): User = userRepository.save(user)

    @Transactional
    fun updateUser(id: Long, user: User): User {
        val existingUser = getUserById(id)
        return userRepository.save(user.copy(id = existingUser.id))
    }

    @Transactional
    fun deleteUser(id: Long) = userRepository.deleteById(id)
}
