package com.example.nativedemo.repository

import com.example.nativedemo.dto.UserSummaryDto
import com.example.nativedemo.entity.Post
import com.example.nativedemo.entity.User
import com.example.nativedemo.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    @Query("SELECT u FROM User u WHERE u.role = :role")
    fun findAllByRole(@Param("role") role: UserRole): List<User>

    @Query("SELECT new com.example.nativedemo.dto.UserSummaryDto(u.id, u.name, u.email) FROM User u")
    fun findAllUserSummaries(): List<UserSummaryDto>

    @Query(
        value = "SELECT * FROM users WHERE role = :role",
        nativeQuery = true
    )
    fun findUsersByRoleNative(@Param("role") role: String): List<User>
}

