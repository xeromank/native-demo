package com.example.nativedemo

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration
class MyConfiguration {
    @RegisterReflectionForBinding(MyClass::class)
    fun registerReflection() {
        // 빈 메소드
    }
}
