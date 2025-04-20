plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6"
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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.h2database:h2")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    implementation("org.springframework.boot:spring-boot-starter-validation")


    // GraalVM Hibernate 지원 의존성
    implementation("org.hibernate.orm:hibernate-graalvm")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks {
    bootBuildImage {
        imageName = "native-demo:latest"
    }

}

tasks.withType<Test> {
    useJUnitPlatform()
}

graalvmNative {
    agent {
        enabled.set(false) // GraalVM 에이전트 실행 비활성화
    }

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
            buildArgs.add("--initialize-at-run-time=org.hibernate")
            buildArgs.add("--initialize-at-run-time=com.mysql.cj.jdbc.Driver,com.mysql.cj.jdbc.NonRegisteringDriver,com.example.nativedemo.entity")
            buildArgs.add("--allow-incomplete-classpath")
            buildArgs.add("--report-unsupported-elements-at-runtime")

        }
    }
}
