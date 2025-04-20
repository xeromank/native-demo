package com.example.nativedemo.config

import com.example.nativedemo.entity.Comment
import com.example.nativedemo.entity.Post
import com.example.nativedemo.entity.User
import com.example.nativedemo.repository.CommentRepository
import com.example.nativedemo.repository.PostRepository
import com.example.nativedemo.repository.UserRepository
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.data.jpa.repository.JpaRepository

@Configuration
@ImportRuntimeHints(JpaRuntimeHints::class)
class BlogHintsConfig

class JpaRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        listOf(
            User::class.java,
            Post::class.java,
            Comment::class.java,
        ).forEach {
            hints.reflection().registerType(it, *MemberCategory.entries.toTypedArray())
        }

        // JPA 관련 리소스 설정
        hints.resources()
            .registerPattern("META-INF/hibernate.properties")
            .registerPattern("META-INF/jpa-named-queries.properties")
            .registerPattern("schema.sql")
            .registerPattern("data.sql")

        // 프록시 설정
        hints.proxies()
            .registerJdkProxy(UserRepository::class.java, JpaRepository::class.java)
            .registerJdkProxy(PostRepository::class.java, JpaRepository::class.java)
            .registerJdkProxy(CommentRepository::class.java, JpaRepository::class.java)
    }
}
