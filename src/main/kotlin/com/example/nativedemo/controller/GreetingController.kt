package com.example.nativedemo.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class GreetingController {
    @GetMapping("/greeting")
    fun greeting(
        @RequestParam(value = "name", defaultValue = "World")
        name: String
    ): Map<String, Any> {
        return mapOf(
            "message" to "Hello, $name!",
            "timestamp" to LocalDateTime.now().toString()
        )
    }

    @GetMapping("/calculator")
    fun calculator(
        @RequestParam(value = "a", defaultValue = "0")
        a: Int,
        @RequestParam(value = "b", defaultValue = "0")
        b: Int,
        @RequestParam(value = "op", defaultValue = "add")
        op: String
    ): Map<String, Any> {
        val result = when (op) {
            "add" -> a + b
            "sub" -> a - b
            "mul" -> a * b
            "div" -> a / b
            else -> throw IllegalArgumentException("Invalid operator: $op")
        }
        return mapOf(
            "result" to result,
        )
    }

    @GetMapping("/system-info")
    fun systemInfo(): Map<String, Any> {
        return mapOf(
            "javaVersion" to System.getProperty("java.version"),
            "osName" to System.getProperty("os.name"),
            "availableProcessors" to Runtime.getRuntime().availableProcessors(),
            "maxMemory" to Runtime.getRuntime().maxMemory() / (1024 * 1024),
            "timestamp" to LocalDateTime.now().toString()
        )
    }
}
