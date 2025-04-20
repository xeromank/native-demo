package com.example.nativedemo.repository

import com.example.nativedemo.entity.Post
import com.example.nativedemo.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.createdAt DESC")
    fun findAllWithAuthor(): List<Post>

    fun findByAuthor(author: User): List<Post>
}
