package com.example.nativedemo.controller

import com.example.nativedemo.dto.CommentRequest
import com.example.nativedemo.entity.Comment
import com.example.nativedemo.service.CommentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts/{postId}/comments")
class CommentController(private val commentService: CommentService) {

    @GetMapping
    fun getCommentsByPost(@PathVariable postId: Long): ResponseEntity<List<Comment>> =
        ResponseEntity.ok(commentService.getCommentsByPost(postId))

    @PostMapping
    fun addComment(
        @PathVariable postId: Long,
        @RequestBody commentRequest: CommentRequest
    ): ResponseEntity<Comment> {
        val comment = commentService.addComment(
            postId = postId,
            userId = commentRequest.authorId,
            content = commentRequest.content
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @DeleteMapping("/{id}")
    fun deleteComment(
        @PathVariable postId: Long,
        @PathVariable id: Long
    ): ResponseEntity<Unit> {
        commentService.deleteComment(id)
        return ResponseEntity.noContent().build()
    }
}
