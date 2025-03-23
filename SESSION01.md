# 1주차: 스프링 네이티브 개요 및 환경 설정

## 학습 목표
- Spring Native와 GraalVM의 기본 개념 이해
- 개발 환경 구성 및 기본 프로젝트 설정
- 첫 번째 네이티브 이미지 애플리케이션 빌드

## 1. GraalVM 소개

### GraalVM이란?
GraalVM은 Oracle에서 개발한 고성능 JDK 배포판으로, Java 및 다른 JVM 언어(Kotlin, Scala 등)로 작성된 애플리케이션을 네이티브 실행 파일로 컴파일하는 기능을 제공합니다.

### 주요 특징
- **네이티브 이미지(Native Image)**: AOT(Ahead-of-Time) 컴파일을 통해 바이트코드를 네이티브 코드로 변환
- **빠른 시작 시간**: 일반 JVM 애플리케이션보다 훨씬 빠른 시작 시간
- **낮은 메모리 사용량**: 메모리 공간을 효율적으로 사용
- **다중 언어 지원**: Java, Kotlin, JavaScript, Ruby, Python, R 등 다양한 언어 지원

### JVM vs 네이티브 이미지 비교

| 특성 | JVM 모드 | 네이티브 이미지 |
|------|---------|--------------|
| 시작 시간 | 느림 (JVM 워밍업 필요) | 매우 빠름 |
| 메모리 사용량 | 높음 | 낮음 |
| 최대 처리량 | 높음 (JIT 최적화) | 중간 |
| 빌드 시간 | 빠름 | 느림 |
| 동적 기능 사용 | 제한 없음 | 제한적 (reflection 등) |
| 배포 크기 | 큼 (JRE 포함) | 작음 (단일 바이너리) |

## 2. 스프링 네이티브 개념

### 스프링 네이티브란?
Spring Native는 Spring 애플리케이션을 GraalVM 네이티브 이미지로 컴파일할 수 있게 해주는 프로젝트입니다. Spring Boot 3.0부터는 네이티브 이미지 지원이 기본 기능으로 포함되었습니다.

### 스프링 네이티브의 장점
- **빠른 시작 시간**: 밀리초 단위의 시작 시간
- **즉각적인 최대 성능**: JIT 컴파일 워밍업 시간 없음
- **낮은 메모리 사용량**: 적은 리소스로 운영 가능
- **작은 배포 크기**: 컨테이너 이미지 크기 감소

### 스프링 네이티브의 제한사항
- 모든 클래스, 메서드, 필드는 빌드 타임에 알려져야 함
- Java Reflection, JNI, Dynamic Proxy 등의 동적 기능 사용 시 특별한 설정 필요
- 일부 서드파티 라이브러리 호환성 문제
- 빌드 시간 증가

## 3. 개발 환경 설정

### GraalVM 설치

#### Windows
```bash
# SDKMAN을 통한 설치
sdk install java 21.0.2-graal

# 또는 직접 다운로드
# https://www.graalvm.org/downloads/ 에서 다운로드 후 환경 변수 설정
```

#### macOS
```bash
# Homebrew 이용
brew install --cask graalvm/tap/graalvm-jdk-21

# 또는 SDKMAN 이용
sdk install java 21.0.2-graal
```

#### Linux
```bash
# SDKMAN 이용
sdk install java 21.0.2-graal

# 또는 직접 다운로드 후 설치
```

### Native Build Tools 설치

#### Windows
```bash
# Visual Studio Build Tools 설치 필요
# https://visualstudio.microsoft.com/downloads/ 에서 "Build Tools for Visual Studio" 다운로드
# "C++ 빌드 도구" 워크로드 선택 설치
```

#### macOS
```bash
xcode-select --install
```

#### Linux
```bash
# Ubuntu/Debian
sudo apt-get install build-essential libz-dev zlib1g-dev

# RHEL/CentOS
sudo yum groupinstall 'Development Tools'
sudo yum install zlib-devel
```

### 환경 변수 설정
```bash
# GRAALVM_HOME 설정
export GRAALVM_HOME=/path/to/graalvm
export PATH=$GRAALVM_HOME/bin:$PATH

# 확인
java -version
native-image --version
```

## 4. 스프링 부트 프로젝트 설정

### Spring Initializr 사용
- [Spring Initializr](https://start.spring.io/)에서 프로젝트 생성
- Kotlin 언어 선택
- Spring Boot 3.x 버전 선택
- 필요한 의존성 추가 (Spring Web, Spring Native 등)

### Gradle 설정 (build.gradle.kts)
```kotlin
plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.graalvm.buildtools.native") version "0.9.28"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

// 네이티브 이미지 설정
graalvmNative {
    binaries {
        named("main") {
            imageName.set("my-app")
            mainClass.set("com.example.demo.DemoApplicationKt")
            buildArgs.add("--verbose")
            buildArgs.add("--no-fallback")
        }
    }
}
```

### Maven 설정 (pom.xml)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    
    <groupId>com.example</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    
    <properties>
        <java.version>17</java.version>
        <kotlin.version>1.9.20</kotlin.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.module</groupId>
            <artifactId>jackson-module-kotlin</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-reflect</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <configuration>
                    <args>
                        <arg>-Xjsr305=strict</arg>
                    </args>
                    <compilerPlugins>
                        <plugin>spring</plugin>
                    </compilerPlugins>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-allopen</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## 5. 첫 번째 애플리케이션 구현

### 기본 애플리케이션 클래스 (DemoApplication.kt)
```kotlin
package com.example.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
```

### 간단한 REST 컨트롤러 구현 (GreetingController.kt)
```kotlin
package com.example.demo

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class GreetingController {

    @GetMapping("/greeting")
    fun greeting(@RequestParam(value = "name", defaultValue = "World") name: String): Map<String, Any> {
        return mapOf(
            "message" to "Hello, $name!",
            "timestamp" to LocalDateTime.now().toString()
        )
    }
    
    @GetMapping("/system-info")
    fun systemInfo(): Map<String, Any> {
        return mapOf(
            "javaVersion" to System.getProperty("java.version"),
            "osName" to System.getProperty("os.name"),
            "availableProcessors" to Runtime.getRuntime().availableProcessors(),
            "maxMemory" to Runtime.getRuntime().maxMemory() / (1024 * 1024),
            "timestamp" to LocalDateTime.now().toString()
        )
    }
}
```

## 6. 애플리케이션 빌드 및 실행

### JVM 모드로 실행
```bash
# Gradle
./gradlew bootRun

# Maven
./mvnw spring-boot:run
```

### 네이티브 이미지 빌드

#### Gradle
```bash
# 네이티브 이미지 빌드
./gradlew nativeCompile

# 네이티브 이미지 실행
./build/native/nativeCompile/my-app
```

#### Maven
```bash
# 네이티브 이미지 빌드
./mvnw -Pnative native:compile

# 네이티브 이미지 실행
./target/demo
```

### Docker를 이용한 네이티브 이미지 빌드

#### Gradle
```bash
# 도커 이미지 빌드
./gradlew bootBuildImage

# 도커 이미지 실행
docker run --rm -p 8080:8080 docker.io/library/demo:0.0.1-SNAPSHOT
```

#### Maven
```bash
# 도커 이미지 빌드
./mvnw spring-boot:build-image

# 도커 이미지 실행
docker run --rm -p 8080:8080 docker.io/library/demo:0.0.1-SNAPSHOT
```

## 7. 성능 비교 및 분석

### 측정 항목
1. 시작 시간
2. 메모리 사용량
3. 첫 요청 응답 시간
4. 최대 처리량

### 측정 방법
```bash
# JVM 모드 시작 시간 측정
time java -jar build/libs/demo-0.0.1-SNAPSHOT.jar

# 네이티브 이미지 시작 시간 측정
time ./build/native/nativeCompile/my-app

# 요청 응답 시간 측정 (curl)
time curl http://localhost:8080/greeting

# 부하 테스트 (Apache Bench)
ab -n 1000 -c 10 http://localhost:8080/greeting
```

## 8. 과제

1. GraalVM과 필요한 도구를 설치하고 개발 환경을 구성하세요.
2. Spring Boot + Kotlin 프로젝트를 생성하고 간단한 REST API를 구현하세요.
3. 다음 엔드포인트를 가진 애플리케이션을 작성하세요:
   - GET /greeting: 기본 인사 메시지 반환
   - GET /calculator?a={number}&b={number}&op={add|subtract|multiply|divide}: 계산 결과 반환
   - GET /info: 시스템 정보 반환 (OS, Java 버전, 메모리 등)
4. JVM 모드와 네이티브 이미지 모드로 각각 빌드하고 실행하세요.
5. 두 모드의 시작 시간, 메모리 사용량, 응답 시간을 측정하고 비교하세요.

## 9. 참고 자료

### 공식 문서
- [GraalVM 공식 문서](https://www.graalvm.org/docs/introduction/)
- [Spring Native 공식 문서](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/)
- [Spring Boot 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/)

### 유용한 블로그 및 튜토리얼
- [GraalVM Native Image Reference](https://www.graalvm.org/reference-manual/native-image/)
- [Spring Boot GraalVM Native Image Support](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [Baeldung: Spring Boot with GraalVM](https://www.baeldung.com/spring-native-intro)

### 샘플 프로젝트
- [Spring Boot Native Samples](https://github.com/spring-projects-experimental/spring-native/tree/main/samples)
- [GraalVM Demos](https://github.com/graalvm/graalvm-demos)

---

## 다음 주차 미리보기
- 네이티브 이미지 빌드 최적화
- Reflection 설정 및 처리
- 리소스 관리 방법
- 빌드 시간 최적화 방법
