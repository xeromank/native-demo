package com.example.nativedemo.controller

import com.example.nativedemo.dto.PostRequest
import com.example.nativedemo.entity.Post
import com.example.nativedemo.service.PostService
import com.example.nativedemo.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts")
class PostController(
    private val postService: PostService,
    private val userService: UserService
) {

    @GetMapping
    fun getAllPosts(): ResponseEntity<List<Post>> =
        ResponseEntity.ok(postService.getAllPosts())

    @GetMapping("/{id}")
    fun getPostById(@PathVariable id: Long): ResponseEntity<Post> =
        ResponseEntity.ok(postService.getPostById(id))

    @GetMapping("/user/{userId}")
    fun getPostsByUser(@PathVariable userId: Long): ResponseEntity<List<Post>> =
        ResponseEntity.ok(postService.getPostsByUser(userId))

    @PostMapping
    fun createPost(
        @RequestBody postRequest: PostRequest
    ): ResponseEntity<Post> {
        val author = userService.getUserById(postRequest.authorId)

        val post = Post(
            title = postRequest.title,
            content = postRequest.content,
            author = author
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(post))
    }

    @PutMapping("/{id}")
    fun updatePost(
        @PathVariable id: Long,
        @RequestBody postRequest: PostRequest
    ): ResponseEntity<Post> {
        val author = userService.getUserById(postRequest.authorId)

        val post = Post(
            title = postRequest.title,
            content = postRequest.content,
            author = author
        )

        return ResponseEntity.ok(postService.updatePost(id, post))
    }

    @DeleteMapping("/{id}")
    fun deletePost(@PathVariable id: Long): ResponseEntity<Unit> {
        postService.deletePost(id)
        return ResponseEntity.noContent().build()
    }
}
