plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.9.28"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

graalvmNative {
    metadataRepository {
        enabled.set(true)
    }
    binaries {
        named("main") {
            imageName.set("native-demo")
            mainClass.set("com.example.nativedemo.NativeDemoApplicationKt")
            debug.set(true)
            verbose.set(true)
            fallback.set(false)

            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--initialize-at-build-time=org.slf4j,ch.qos.logback")
            buildArgs.add("-H:+PrintClassInitialization")

//            buildArgs.add("-H:ResourceConfigurationFiles=META-INF/native-image/resource-config.json")
//            buildArgs.add("-H:ReflectionConfigurationFiles=META-INF/native-image/reflect-config.json")

//            buildArgs.add("--no-fallback")
//            buildArgs.add("--initialize-at-build-time=org.slf4j,ch.qos.logback")
//            buildArgs.add("-H:+ReportExceptionStackTraces")
//            buildArgs.add("-J-Djava.util.concurrent.ForkJoinPool.common.parallelism=6")
//            buildArgs.add("-H:-IncludeDebugInfo")
//            buildArgs.add("-H:+UseSerialGC")
        }
    }
}
