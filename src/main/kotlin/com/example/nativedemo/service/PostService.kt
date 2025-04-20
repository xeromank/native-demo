package com.example.nativedemo.service

import com.example.nativedemo.entity.Post
import com.example.nativedemo.repository.PostRepository
import com.example.nativedemo.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) {
    @Transactional(readOnly = true)
    fun getAllPosts(): List<Post> = postRepository.findAllWithAuthor()

    @Transactional(readOnly = true)
    fun getPostById(id: Long): Post = postRepository.findById(id)
        .orElseThrow { NoSuchElementException("Post not found with id: $id") }

    @Transactional(readOnly = true)
    fun getPostsByUser(userId: Long): List<Post> {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }
        return postRepository.findByAuthor(user)
    }

    @Transactional
    fun createPost(post: Post): Post = postRepository.save(post)

    @Transactional
    fun updatePost(id: Long, post: Post): Post {
        val existingPost = getPostById(id)
        return postRepository.save(post.copy(
            id = existingPost.id,
            author = existingPost.author,
            createdAt = existingPost.createdAt,
            updatedAt = LocalDateTime.now()
        ))
    }

    @Transactional
    fun deletePost(id: Long) = postRepository.deleteById(id)

}
