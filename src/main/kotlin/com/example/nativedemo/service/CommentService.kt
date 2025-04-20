package com.example.nativedemo.service

import com.example.nativedemo.entity.Comment
import com.example.nativedemo.repository.CommentRepository
import com.example.nativedemo.repository.PostRepository
import com.example.nativedemo.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) {
    @Transactional(readOnly = true)
    fun getCommentsByPost(postId: Long): List<Comment> {
        val post = postRepository.findById(postId)
            .orElseThrow { NoSuchElementException("Post not found with id: $postId") }
        return commentRepository.findByPostWithAuthor(post)
    }

    @Transactional
    fun addComment(postId: Long, userId: Long, content: String): Comment {
        val post = postRepository.findById(postId)
            .orElseThrow { NoSuchElementException("Post not found with id: $postId") }

        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }

        val comment = Comment(
            content = content,
            post = post,
            author = user
        )

        return commentRepository.save(comment)
    }

    @Transactional
    fun deleteComment(id: Long) = commentRepository.deleteById(id)

}
