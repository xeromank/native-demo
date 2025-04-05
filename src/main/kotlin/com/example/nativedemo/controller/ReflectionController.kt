package com.example.nativedemo.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ReflectionController {
    @GetMapping("/create-dynamic")
    fun createDynamic(@RequestParam className: String): Any {
        try {
            // 이 코드는 네이티브 이미지에서 특별한 설정 없이는 동작하지 않음
            val clazz = Class.forName(className)
            val constructor = clazz.getDeclaredConstructor()
            return constructor.newInstance()
        } catch (e: Exception) {
            return mapOf("error" to e.message)
        }
    }
}
