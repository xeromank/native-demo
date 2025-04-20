package com.example.nativedemo.repository

import com.example.nativedemo.entity.Comment
import com.example.nativedemo.entity.Post
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CommentRepository : JpaRepository<Comment, Long> {
    fun findByPost(post: Post): List<Comment>

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post = :post ORDER BY c.createdAt ASC")
    fun findByPostWithAuthor(@Param("post") post: Post): List<Comment>
}

