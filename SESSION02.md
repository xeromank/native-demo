# 2주차: 스프링 네이티브 기본 구성 및 최적화

## 학습 목표
- 네이티브 이미지의 동작 원리와 빌드 옵션 이해
- AOT(Ahead-of-Time) 컴파일 과정 이해
- 리플렉션, 리소스, 프록시 등 동적 기능 처리 방법 학습
- 빌드 시간 및 이미지 크기 최적화 기법 습득

## 1. 네이티브 이미지 빌드 과정 및 원리

### 네이티브 이미지 빌드 과정

네이티브 이미지는 애플리케이션 코드와 의존성을 분석하여 필요한 코드만 포함하는 독립형 실행 파일을 생성합니다. 빌드 과정은 다음과 같습니다:

1. **정적 분석**: 애플리케이션의 클래스 계층 구조, 메서드, 필드를 분석
2. **도달 가능성 분석(Reachability Analysis)**: 실제 사용되는 코드 경로 식별
3. **AOT 컴파일**: 사용되는 모든 코드를 미리 컴파일
4. **이미지 생성**: 컴파일된 코드와 필요한 리소스를 하나의 실행 파일로 패키징

![네이티브 이미지 빌드 과정](https://i.imgur.com/ZvJnGbL.png)

### Closed World Assumption

네이티브 이미지 빌드는 "닫힌 세계 가정(Closed World Assumption)"에 기반합니다:

- 빌드 시점에 모든 코드가 알려져 있어야 함
- 런타임에 새로운 코드를 로드할 수 없음
- 동적 기능(리플렉션, JNI, 프록시 등)은 특별한 처리 필요

## 2. AOT(Ahead-of-Time) 컴파일 이해

### JIT vs AOT 컴파일

| JIT 컴파일 (기존 JVM) | AOT 컴파일 (네이티브 이미지) |
|----------------------|-------------------------|
| 런타임에 컴파일 | 빌드 타임에 컴파일 |
| 동적 최적화 가능 | 정적 최적화만 가능 |
| 워밍업 시간 필요 | 즉시 최대 성능 |
| 메모리 사용량 높음 | 메모리 사용량 낮음 |
| 런타임 분석 기반 최적화 | 정적 분석 기반 최적화 |

### 스프링 AOT 엔진

Spring Boot 3.0부터는 Spring AOT 엔진이 통합되어 네이티브 이미지 빌드를 지원합니다:

- 빌드 시점에 애플리케이션 컨텍스트 모의 실행
- 리플렉션 힌트 자동 생성
- 동적 프록시 대체 코드 생성
- 리소스 관리

## 3. 네이티브 이미지 빌드 옵션

GraalVM native-image 도구는 다양한 빌드 옵션을 제공합니다:

### 주요 옵션

```bash
native-image [options] -jar myapp.jar
```

| 옵션 | 설명 |
|------|------|
| `--verbose` | 상세한 빌드 로그 출력 |
| `--no-fallback` | JVM 폴백 없이 완전한 네이티브 이미지 생성 |
| `--initialize-at-build-time` | 지정된 클래스를 빌드 시점에 초기화 |
| `--initialize-at-run-time` | 지정된 클래스를 런타임에 초기화 |
| `--report-unsupported-elements-at-runtime` | 지원되지 않는 요소를 런타임에 보고 |
| `--allow-incomplete-classpath` | 불완전한 클래스패스 허용 |

### 메모리 및 성능 관련 옵션

| 옵션 | 설명 |
|------|------|
| `-H:+PrintAnalysisCallTree` | 분석 호출 트리 출력 |
| `-H:+ReportExceptionStackTraces` | 예외 스택 트레이스 보고 |
| `-H:ReflectionConfigurationFiles=reflection-config.json` | 리플렉션 설정 파일 지정 |
| `-H:ResourceConfigurationFiles=resource-config.json` | 리소스 설정 파일 지정 |
| `-H:DynamicProxyConfigurationFiles=proxy-config.json` | 동적 프록시 설정 파일 지정 |
| `-H:IncludeResources=.*\.properties$` | 포함할 리소스 패턴 |
| `-H:+PrintClassInitialization` | 클래스 초기화 정보 출력 |

### Gradle에서의 설정

```kotlin
graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("--verbose")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:+PrintClassInitialization")
            // 초기화 설정
            buildArgs.add("--initialize-at-build-time=org.slf4j,ch.qos.logback")
            buildArgs.add("--initialize-at-run-time=io.netty.channel.epoll.Epoll")
            // 메모리 설정
            buildArgs.add("-H:+AddAllCharsets")
        }
    }
}
```

### Maven에서의 설정

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <configuration>
        <buildArgs>
            <buildArg>--verbose</buildArg>
            <buildArg>--no-fallback</buildArg>
            <buildArg>-H:+ReportExceptionStackTraces</buildArg>
            <buildArg>-H:+PrintClassInitialization</buildArg>
            <buildArg>--initialize-at-build-time=org.slf4j,ch.qos.logback</buildArg>
            <buildArg>--initialize-at-run-time=io.netty.channel.epoll.Epoll</buildArg>
            <buildArg>-H:+AddAllCharsets</buildArg>
        </buildArgs>
    </configuration>
</plugin>
```

## 4. 리플렉션 처리

### 리플렉션 문제점

네이티브 이미지에서 Java 리플렉션은 특별한 처리가 필요:

- GraalVM은 빌드 시점에 사용될 리플렉션 API 호출을 알아야 함
- 그렇지 않으면 `ClassNotFoundException` 또는 `NoSuchMethodException` 발생

### 리플렉션 설정 방법

#### 1. 수동 설정: reflection-config.json

```json
[
  {
    "name": "com.example.MyClass",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  }
]
```

#### 2. 자동 감지: @RegisterReflectionForBinding

Spring Boot 애노테이션 사용:

```kotlin
@Configuration
class MyConfiguration {
    @RegisterReflectionForBinding(MyClass::class)
    fun registerReflection() {
        // 빈 메소드
    }
}
```

#### 3. 트레이스 에이전트 사용

빌드 전 애플리케이션을 실행하며 리플렉션 사용 추적:

```bash
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image -jar target/myapp.jar
```

이 방법으로 생성되는 파일들:
- `reflect-config.json`: 리플렉션 설정
- `resource-config.json`: 리소스 설정
- `proxy-config.json`: 동적 프록시 설정
- `jni-config.json`: JNI 설정
- `serialization-config.json`: 직렬화 설정

### 리플렉션을 통해 동적으로 클래스 로드 및 사용 예제

```kotlin
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
```

이를 해결하기 위한 처리:

```kotlin
@SpringBootApplication
@ImportRuntimeHints(MyRuntimeHints::class)
class DemoApplication

class MyRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // 특정 클래스에 대한 리플렉션 활성화
        hints.reflection().registerType(
            TypeReference.of(MyClass::class.java),
            { typeHint -> 
                typeHint.withConstructor(emptyArray(), MemberCategory.INVOKE)
                    .withMembers(MemberCategory.INVOKE_PUBLIC_METHODS) 
            }
        )
        
        // 패턴을 통한 리소스 포함
        hints.resources().registerPattern("static/*")
    }
}
```

## 5. 리소스 관리

### 리소스 처리 문제

네이티브 이미지에서는 다음과 같은 리소스 관련 제약이 있습니다:

- 빌드 시점에 알려진 리소스만 포함 가능
- 클래스패스 리소스에 대한 동적 접근 제한
- 번들 리소스는 명시적으로 지정해야 함

### 리소스 설정 방법

#### 1. resource-config.json

```json
{
  "resources": {
    "includes": [
      {
        "pattern": ".*\\.properties$"
      },
      {
        "pattern": "static/.*"
      },
      {
        "pattern": "templates/.*\\.html$"
      }
    ],
    "excludes": [
      {
        "pattern": ".*\\.jnilib$"
      }
    ]
  }
}
```

#### 2. Spring Boot 자동 설정

Spring Boot는 많은 리소스를 자동으로 처리합니다:

- `application.properties`/`application.yml`
- Thymeleaf, Freemarker 템플릿
- 정적 리소스 (`/static`, `/public`, `/resources`, `/META-INF/resources`)
- Web 관련 설정 파일

#### 3. 명시적 리소스 힌트 등록

```kotlin
@Bean
fun resourceHints(): RuntimeHintsRegistrar {
    return RuntimeHintsRegistrar { hints, _ ->
        hints.resources()
            .registerPattern("messages/*")
            .registerPattern("static/**")
            .registerPattern("data/*.json")
    }
}
```

## 6. 프록시 처리

### 동적 프록시 문제

네이티브 이미지에서 동적 프록시 생성은 빌드 시점에 설정 필요:

- JDK 동적 프록시 (인터페이스 기반)
- CGLIB 프록시 (클래스 기반)

### 프록시 설정 방법

#### 1. proxy-config.json

```json
[
  {
    "interfaces": [
      "com.example.MyService",
      "org.springframework.aop.SpringProxy",
      "org.springframework.aop.framework.Advised",
      "org.springframework.core.DecoratingProxy"
    ]
  }
]
```

#### 2. Spring AOP 자동 설정

Spring Boot는 많은 AOP 관련 설정을 자동으로 처리:

- Spring Data 리포지토리
- 트랜잭션 관리
- 캐싱
- 비동기 메소드

#### 3. 힌트 등록

```kotlin
@Bean
fun aopHints(): RuntimeHintsRegistrar {
    return RuntimeHintsRegistrar { hints, _ ->
        hints.proxies()
            .registerJdkProxy(
                MyService::class.java,
                SpringProxy::class.java,
                Advised::class.java,
                DecoratingProxy::class.java
            )
    }
}
```

## 7. 시리얼라이제이션 처리

### 시리얼라이제이션 문제

네이티브 이미지에서 Java 시리얼라이제이션은 특별한 처리 필요:

- 시리얼라이제이션 관련 메타데이터는 빌드 시점에 등록 필요
- Jackson, Gson 등의 JSON 라이브러리도 설정 필요

### 시리얼라이제이션 설정

#### 1. serialization-config.json

```json
[
  {
    "name": "com.example.MySerializableClass",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true,
    "allDeclaredFields": true,
    "allPublicFields": true
  }
]
```

#### 2. Jackson JSON 처리를 위한 설정

Spring Boot는 Jackson 관련 많은 설정을 자동으로 처리합니다.

추가 설정이 필요한 경우:

```kotlin
@Bean
fun jacksonHints(): RuntimeHintsRegistrar {
    return RuntimeHintsRegistrar { hints, _ ->
        hints.reflection()
            .registerType(MyDTO::class.java, MemberCategory.INTROSPECT_PUBLIC_METHODS)
            .registerType(SpecialDTO::class.java, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
    }
}
```

## 8. 초기화 시점 제어

### 초기화 시점 문제

클래스 초기화는 빌드 시점 또는 런타임에 발생할 수 있습니다:

- 빌드 시점 초기화: 성능 이점, 하지만 제약 발생
- 런타임 초기화: 유연성 제공, 하지만 시작 시간 증가

### 초기화 설정

#### 1. 빌드 시점 초기화

```
--initialize-at-build-time=org.slf4j,ch.qos.logback
```

대부분의 로깅 프레임워크, 유틸리티 클래스, 불변 상태 클래스는 빌드 시점 초기화 권장

#### 2. 런타임 초기화

```
--initialize-at-run-time=io.netty.channel.epoll.Epoll
```

네이티브 코드 접근, 시스템 속성 접근, 보안 관련 코드, 네트워크/파일 작업 클래스는 런타임 초기화 권장

## 9. 빌드 최적화 기법

### 빌드 시간 최적화

1. **증분 빌드 활용**:
   - Gradle: `org.graalvm.buildtools.native` 플러그인 사용
   - Maven: `native-maven-plugin` 사용

2. **병렬 빌드 설정**:
```
-J-Djava.util.concurrent.ForkJoinPool.common.parallelism=6
```

3. **메모리 할당 증가**:
```
-J-Xmx8g
```

4. **불필요한 클래스 제외**:
```
--exclude-config=exclude-config.json
```

### 이미지 크기 최적화

1. **디버그 정보 제거**:
```
-H:-IncludeDebugInfo
```

2. **최소 리소스만 포함**:
리소스 설정 파일을 통해 필요한 리소스만 정확히 지정

3. **불필요한 의존성 제거**:
애플리케이션이 실제로 사용하는 의존성만 포함

4. **G1GC 대신 더 작은 GC 사용**:
```
-H:+UseSerialGC
```

## 10. 실제 네이티브 이미지 예제 애플리케이션

### 기본 웹 애플리케이션 확장

1주차에서 만든 애플리케이션을 확장하여 다양한 네이티브 이미지 기능을 테스트합니다.

### DTO 클래스

```kotlin
package com.example.demo

data class UserDTO(
    val id: Long,
    val name: String,
    val email: String,
    val roles: List<String>
)

data class ProductDTO(
    val id: Long,
    val name: String,
    val price: Double,
    val description: String,
    val category: String
)
```

### 서비스 인터페이스와 구현체

```kotlin
package com.example.demo

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

interface UserService {
    fun getUsers(): List<UserDTO>
    fun getUserById(id: Long): UserDTO?
    fun createUser(user: UserDTO): UserDTO
}

@Service
class UserServiceImpl : UserService {
    private val users = ConcurrentHashMap<Long, UserDTO>()

    init {
        // 샘플 데이터 추가
        users[1] = UserDTO(1, "John Doe", "john@example.com", listOf("USER"))
        users[2] = UserDTO(2, "Jane Smith", "jane@example.com", listOf("USER", "ADMIN"))
    }

    override fun getUsers(): List<UserDTO> = users.values.toList()

    override fun getUserById(id: Long): UserDTO? = users[id]

    override fun createUser(user: UserDTO): UserDTO {
        users[user.id] = user
        return user
    }
}
```

### 컨트롤러

```kotlin
package com.example.demo

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @GetMapping
    fun getUsers(): ResponseEntity<List<UserDTO>> {
        return ResponseEntity.ok(userService.getUsers())
    }

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserDTO> {
        val user = userService.getUserById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(user)
    }

    @PostMapping
    fun createUser(@RequestBody user: UserDTO): ResponseEntity<UserDTO> {
        return ResponseEntity.ok(userService.createUser(user))
    }
}

@RestController
@RequestMapping("/api/reflection")
class ReflectionDemoController {

    @GetMapping("/inspect-class")
    fun inspectClass(@RequestParam className: String): ResponseEntity<Map<String, Any>> {
        return try {
            val clazz = Class.forName(className)
            val methods = clazz.declaredMethods.map { it.name }
            val fields = clazz.declaredFields.map { it.name }
            
            ResponseEntity.ok(mapOf(
                "className" to clazz.name,
                "methods" to methods,
                "fields" to fields,
                "isInterface" to clazz.isInterface,
                "superclass" to (clazz.superclass?.name ?: "none")
            ))
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf("error" to e.message.toString()))
        }
    }
}
```

### RuntimeHints 설정

```kotlin
package com.example.demo

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.stereotype.Component

@Component
@ImportRuntimeHints(DemoRuntimeHints::class)
class DemoRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // 리플렉션 힌트 등록
        hints.reflection()
            .registerType(UserDTO::class.java, 
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INTROSPECT_DECLARED_METHODS)
            .registerType(ProductDTO::class.java, 
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
```

### 애플리케이션 속성 설정

```properties
# src/main/resources/application.properties

# 로깅 설정
logging.level.root=INFO
logging.level.com.example=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n

# 서버 설정
server.port=8080
server.compression.enabled=true

# 애플리케이션 정보
application.name=demo
application.version=1.0.0
```

### 빌드 설정 업데이트 (Gradle)

```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("native-demo-app")
            buildArgs.add("--verbose")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--initialize-at-build-time=org.slf4j,ch.qos.logback")
            buildArgs.add("-H:+PrintClassInitialization")
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}
```

## 11. 과제

1. 1주차에서 만든 프로젝트를 확장하여 다음 기능을 구현하세요:
   - 사용자 관리 기능 (위의 예제 활용)
   - 리플렉션을 활용한 API 엔드포인트 구현
   - 외부 JSON 파일에서 설정 읽어오기

2. 다음 네이티브 이미지 최적화 기법을 적용하세요:
   - 적절한 리플렉션 힌트 등록
   - 필요한 리소스만 포함
   - 빌드 시점/런타임 초기화 설정

3. 다음 성능 지표를 측정하고 비교하세요:
   - 시작 시간: JVM vs 네이티브
   - 메모리 사용량: JVM vs 네이티브
   - 첫 응답 시간: JVM vs 네이티브
   - 처리량(초당 요청 수): JVM vs 네이티브

4. 트레이스 에이전트를 사용하여 리플렉션, 리소스, 프록시 설정 파일을 생성해보세요.

5. 네이티브 이미지 빌드 과정에서 발생하는 문제를 해결하는 방법을 정리하세요.

## 12. 참고 자료

### 공식 문서
- [GraalVM Native Image Configuration](https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/)
- [Spring Boot AOT Engine](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html#native-image.advanced.aot-engine)
- [Spring Boot 3.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Release-Notes)

### 블로그 및 튜토리얼
- [Understanding Native Images in GraalVM](https://www.baeldung.com/graalvm-native-images)
- [Optimizing Spring Boot Applications for GraalVM](https://spring.io/blog/2021/03/11/announcing-spring-native-beta)
- [Debugging Native Images](https://medium.com/graalvm/debugging-native-images-8f46596103e8)

### 샘플 프로젝트
- [Spring Native Examples](https://github.com/spring-projects-experimental/spring-native-samples)
- [GraalVM Examples](https://github.com/graalvm/graalvm-demos)

---

## 다음 주차 미리보기
- 데이터베이스 연동 및 JPA 활용
- 트랜잭션 관리
- R2DBC를 활용한 리액티브 데이터베이스 접근
