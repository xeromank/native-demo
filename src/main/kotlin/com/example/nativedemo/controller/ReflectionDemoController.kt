package com.example.nativedemo.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/reflection")
class ReflectionDemoController {

    @GetMapping("/inspect-class")
    fun inspectClass(@RequestParam className: String): ResponseEntity<Map<String, Any>> {
        return try {
            val clazz = Class.forName(className)
            val methods = clazz.declaredMethods.map { it.name }
            val fields = clazz.declaredFields.map { it.name }

            ResponseEntity.ok(
                mapOf(
                    "className" to clazz.name,
                    "methods" to methods,
                    "fields" to fields,
                    "isInterface" to clazz.isInterface,
                    "superclass" to (clazz.superclass?.name ?: "none")
                )
            )
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf("error" to e.message.toString()))
        }
    }
}
