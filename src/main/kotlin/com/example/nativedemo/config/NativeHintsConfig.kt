package com.example.nativedemo.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

@Configuration
@ImportRuntimeHints(JpaRuntimeHints::class)
class NativeHintsConfig {
}
