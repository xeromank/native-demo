package com.example.nativedemo.config

import com.example.nativedemo.dto.ProductDTO
import com.example.nativedemo.dto.UserDTO
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.stereotype.Component

@Component
@ImportRuntimeHints(DemoRuntimeHints::class)
class DemoRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // 리플렉션 힌트 등록
        hints.reflection()
            .registerType(
                UserDTO::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INTROSPECT_DECLARED_METHODS)
            .registerType(
                ProductDTO::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INTROSPECT_DECLARED_METHODS)
            .registerType(String::class.java)
            .registerType(List::class.java)
            .registerType(Map::class.java)

        // 리소스 힌트 등록
        hints.resources()
            .registerPattern("static/**")
            .registerPattern("templates/**")
            .registerPattern("schema.sql")
            .registerPattern("data.sql")

        // 직렬화 힌트 등록
        hints.serialization()
            .registerType(UserDTO::class.java)
            .registerType(ProductDTO::class.java)
    }
}
