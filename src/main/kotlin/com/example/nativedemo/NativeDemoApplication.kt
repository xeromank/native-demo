package com.example.nativedemo

import org.springframework.aop.SpringProxy
import org.springframework.aop.framework.Advised
import org.springframework.aot.hint.ExecutableMode
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.core.DecoratingProxy

@SpringBootApplication
@ImportRuntimeHints(MyRuntimeHints::class)
class NativeDemoApplication {
    @Bean
    fun resourceHints(): RuntimeHintsRegistrar {
        return RuntimeHintsRegistrar { hints, _ ->
            hints.resources()
                .registerPattern("messages/*")
                .registerPattern("static/**")
                .registerPattern("data/*.json")
        }
    }

    @Bean
    fun aopHints(): RuntimeHintsRegistrar {
        return RuntimeHintsRegistrar { hints, _ ->
            hints.proxies()
                .registerJdkProxy(
                    SpringProxy::class.java,
                    Advised::class.java,
                    DecoratingProxy::class.java
                )
        }
    }

    @Bean
    fun jacksonHints(): RuntimeHintsRegistrar {
        return RuntimeHintsRegistrar { hints, _ ->
            hints.reflection()
                .registerType(MyDataClass::class.java, MemberCategory.INTROSPECT_PUBLIC_METHODS)
        }
    }
}

fun main(args: Array<String>) {
    runApplication<NativeDemoApplication>(*args)
}

class MyRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // 특정 클래스에 대한 리플렉션 활성화
        hints.reflection().registerType(
            TypeReference.of(MyClass::class.java)
        ) { typeHint ->
            typeHint.withConstructor(emptyList(), ExecutableMode.INVOKE)
                .withMembers(MemberCategory.INVOKE_PUBLIC_METHODS)
        }

        // 패턴을 통한 리소스 포함
        hints.resources().registerPattern("static/*")
    }


}
