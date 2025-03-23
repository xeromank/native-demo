package com.example.nativedemo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NativeDemoApplication

fun main(args: Array<String>) {
    runApplication<NativeDemoApplication>(*args)
}
