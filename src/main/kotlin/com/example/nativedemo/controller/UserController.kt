package com.example.nativedemo.controller

import com.example.nativedemo.dto.UserDTO
import com.example.nativedemo.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @GetMapping
    fun getUsers(): ResponseEntity<List<UserDTO>> {
        return ResponseEntity.ok(userService.getUsers())
    }

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserDTO> {
        val user = userService.getUserById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(user)
    }

    @PostMapping
    fun createUser(@RequestBody user: UserDTO): ResponseEntity<UserDTO> {
        return ResponseEntity.ok(userService.createUser(user))
    }
}
