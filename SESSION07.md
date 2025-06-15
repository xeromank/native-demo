# 7주차: 코틀린 스프링부트 네이티브 이미지 + OpenTelemetry + 뉴렐릭 연동

## 학습 목표
- OpenTelemetry의 개념과 네이티브 이미지 환경에서의 특이사항 이해
- 스프링부트 애플리케이션에 OpenTelemetry 설정
- 뉴렐릭으로 직접 메트릭, 트레이스, 로그 전송
- 네이티브 이미지 빌드 시 필요한 설정 최적화

## 1. OpenTelemetry 개념 이해 (30분)

### 1.1 OpenTelemetry란?
- 관찰 가능성(Observability)의 세 가지 기둥: Metrics, Traces, Logs
- OpenTelemetry의 핵심 구성 요소
  - **SDK**: 데이터 생성 및 수집
  - **API**: 코드 계측(Instrumentation)
  - **Exporter**: 데이터 전송

### 1.2 네이티브 이미지 환경에서의 고려사항
- Reflection 설정 필요성
- 런타임 초기화 vs 빌드 타임 초기화
- 자동 계측(Auto-instrumentation) vs 수동 계측

## 2. 프로젝트 설정 (40분)

### 2.1 의존성 추가 (뉴렐릭 에이전트 대체용)

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop") // @WithSpan 어노테이션용
    
    // OpenTelemetry Spring Boot Starter (네이티브 이미지 지원)
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:2.8.0")
    
    // 자동 계측 라이브러리들 (에이전트 기능 대체)
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.8.0")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-webmvc-6.0:2.8.0-alpha")
    implementation("io.opentelemetry.instrumentation:opentelemetry-jdbc:2.8.0-alpha")
    implementation("io.opentelemetry.instrumentation:opentelemetry-hikaricp-4.0:2.8.0-alpha")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.8.0-alpha")
    
    // JVM 메트릭을 위한 Micrometer (에이전트 JVM 메트릭 대체)
    implementation("io.micrometer:micrometer-registry-otlp:1.12.1")
    implementation("io.micrometer:micrometer-tracing-bridge-otel:1.2.1")
    
    // OTLP 직접 전송용
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.32.0")
}
```

### 2.2 에이전트 vs Starter 비교표

| 기능 | 뉴렐릭 에이전트 | OpenTelemetry Starter |
|------|---------------|---------------------|
| 자동 계측 | ✅ 광범위 | ⚠️ 제한적 (수동 추가 필요) |
| JVM 메트릭 | ✅ 자동 | ❌ Micrometer 필요 |
| 네이티브 이미지 | ❌ 미지원 | ✅ 완전 지원 |
| SLO 생성 | ✅ 지원 | ❌ 미지원 |
| 시작 오버헤드 | 높음 | 낮음 |
| 설정 복잡도 | 낮음 | 높음 |

### 2.2 application.yml 설정

```yaml
# application.yml
spring:
  application:
    name: otel-native-demo
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 

otel:
  service:
    name: ${spring.application.name}
  exporter:
    otlp:
      endpoint: https://otlp.nr-data.net:4318
      headers:
        api-key: ${NEW_RELIC_LICENSE_KEY}
  traces:
    exporter: otlp
  metrics:
    exporter: otlp
  logs:
    exporter: otlp

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## 3. OpenTelemetry 설정 클래스 (45분)

### 3.1 기본 설정 클래스

```kotlin
@Configuration
@EnableAutoConfiguration
class OpenTelemetryConfig {

    @Value("\${otel.service.name}")
    private lateinit var serviceName: String

    @Value("\${NEW_RELIC_LICENSE_KEY}")
    private lateinit var newRelicLicenseKey: String

    @Bean
    fun openTelemetry(): OpenTelemetry {
        return OpenTelemetryBuilder()
            .setTracerProvider(tracerProvider())
            .setMeterProvider(meterProvider())
            .buildAndRegisterGlobal()
    }

    @Bean
    fun tracerProvider(): TracerProvider {
        return SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint("https://otlp.nr-data.net:4317")
                    .addHeader("api-key", newRelicLicenseKey)
                    .build()
            ).build())
            .setResource(
                Resource.getDefault()
                    .merge(Resource.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, "1.0.0")
                        .build())
            )
            .build()
    }

    @Bean
    fun meterProvider(): MeterProvider {
        return SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(
                    OtlpGrpcMetricExporter.builder()
                        .setEndpoint("https://otlp.nr-data.net:4317")
                        .addHeader("api-key", newRelicLicenseKey)
                        .build()
                ).setInterval(Duration.ofSeconds(30))
                .build()
            )
            .setResource(
                Resource.getDefault()
                    .merge(Resource.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .build())
            )
            .build()
    }
}
```

### 3.2 커스텀 계측 클래스

```kotlin
@Component
class CustomInstrumentation {
    
    private val tracer = GlobalOpenTelemetry.getTracer("custom-instrumentation")
    private val meter = GlobalOpenTelemetry.getMeter("custom-metrics")
    
    // 커스텀 카운터
    private val requestCounter = meter
        .counterBuilder("custom_requests_total")
        .setDescription("Total number of custom requests")
        .build()
    
    // 커스텀 히스토그램
    private val processingTime = meter
        .histogramBuilder("custom_processing_duration")
        .setDescription("Processing time in milliseconds")
        .setUnit("ms")
        .build()

    fun recordRequest(operation: String) {
        requestCounter.add(1, Attributes.of(AttributeKey.stringKey("operation"), operation))
    }

    fun <T> measureTime(operation: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val span = tracer.spanBuilder("custom_operation")
            .setAttribute("operation.name", operation)
            .startSpan()
        
        return try {
            span.makeCurrent().use {
                val result = block()
                span.setStatus(StatusCode.OK)
                result
            }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
            span.recordException(e)
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            processingTime.record(duration.toDouble(), 
                Attributes.of(AttributeKey.stringKey("operation"), operation))
            span.end()
        }
    }
}
```

## 4. 자동 Span 생성 및 어노테이션 활용 (30분)

### 4.1 자동 Span 생성 확인

OpenTelemetry Spring Boot Starter는 다음과 같은 span을 자동 생성합니다:

```kotlin
// 이러한 호출들이 자동으로 span을 생성합니다
@RestController
class UserController(private val userService: UserService) {
    
    @GetMapping("/api/users")  // 자동 HTTP span: "GET /api/users"
    fun getAllUsers(): ResponseEntity<List<User>> {
        val users = userService.findAllUsers()  // 자동 method span 가능
        return ResponseEntity.ok(users)
    }
}

@Service
class UserService(private val userRepository: UserRepository) {
    
    fun findAllUsers(): List<User> {
        return userRepository.findAll()  // 자동 DB span: "SELECT user"
    }
}
```

### 4.2 @WithSpan 어노테이션으로 커스텀 Span

```kotlin
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.api.trace.Span

@Service
class UserService(
    private val userRepository: UserRepository
) {
    
    @WithSpan("user-creation-process")  // 커스텀 span 이름
    fun createUser(
        @SpanAttribute("user.email") email: String,  // 파라미터를 span 속성으로
        @SpanAttribute("user.name") name: String
    ): User {
        val currentSpan = Span.current()
        currentSpan.addEvent("Starting user validation")
        
        // 비즈니스 로직
        val existingUser = userRepository.findByEmail(email)
        if (existingUser != null) {
            currentSpan.addEvent("User already exists")
            currentSpan.setAttribute("validation.result", "failed")
            throw IllegalArgumentException("User with email $email already exists")
        }
        
        currentSpan.addEvent("Creating new user")
        val user = User(name = name, email = email)
        val savedUser = userRepository.save(user)
        
        currentSpan.setAttribute("user.id", savedUser.id.toString())
        currentSpan.setAttribute("validation.result", "success")
        currentSpan.addEvent("User created successfully")
        
        return savedUser
    }
    
    @WithSpan  // 기본 span 이름 (메서드명 사용)
    fun calculateUserStats(@SpanAttribute("user.id") userId: Long): UserStats {
        val span = Span.current()
        span.setAttribute("operation.type", "calculation")
        
        // 복잡한 계산 로직
        Thread.sleep(100) // 시뮬레이션
        
        return UserStats(userId, "active")
    }
}
```

### 4.3 수동 Span 생성 (고급 사용)

```kotlin
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.SpanKind

@Service
class AdvancedUserService(
    private val openTelemetry: OpenTelemetry,
    private val userRepository: UserRepository
) {
    private val tracer: Tracer = openTelemetry.getTracer("advanced-user-service")
    
    fun processUserData(userId: Long): ProcessResult {
        return tracer.spanBuilder("process-user-data")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("user.id", userId)
            .startSpan()
            .use { span ->
                try {
                    span.addEvent("Starting data processing")
                    
                    // 자식 span 생성
                    val validationResult = validateUser(userId, span)
                    val enrichmentResult = enrichUserData(userId, span)
                    
                    span.setAttribute("validation.result", validationResult.toString())
                    span.setAttribute("enrichment.result", enrichmentResult.toString())
                    span.addEvent("Processing completed")
                    
                    ProcessResult.success()
                } catch (e: Exception) {
                    span.recordException(e)
                    span.setAttribute("error", true)
                    ProcessResult.failure(e.message ?: "Unknown error")
                }
            }
    }
    
    private fun validateUser(userId: Long, parentSpan: Span): Boolean {
        return tracer.spanBuilder("validate-user")
            .setParent(Context.current().with(parentSpan))
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()
            .use { span ->
                span.setAttribute("user.id", userId)
                // 검증 로직
                true
            }
    }
}
```

## 5. 실제 애플리케이션 코드 작성 (45분)

### 4.1 엔티티 및 리포지토리

```kotlin
@Entity
@Table(name = "users")
data class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(nullable = false, unique = true)
    val email: String,
    
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
}
```

### 5.2 JVM 메트릭 추가 (에이전트 기능 대체)

```kotlin
@Configuration
class MicrometerConfig {
    
    @Bean
    fun jvmMetrics(): MeterBinder {
        return JvmMetrics()
    }
    
    @Bean
    fun processMetrics(): MeterBinder {
        return ProcessMetrics()
    }
    
    @Bean
    fun systemMetrics(): MeterBinder {
        return SystemMetrics()
    }
    
    @Bean
    fun tomcatMetrics(): MeterBinder {
        return TomcatMetrics.monitor(null, "tomcat")
    }
}

// application.yml 추가 설정
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      otlp:
        enabled: true
        url: https://otlp.nr-data.net:4318/v1/metrics
        headers:
          api-key: ${NEW_RELIC_LICENSE_KEY}
        step: 30s
  otlp:
    metrics:
      export:
        enabled: true
```

### 5.3 서비스 레이어 (Span 적용)

```kotlin
@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val customInstrumentation: CustomInstrumentation
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    @WithSpan("create-user-operation")
    fun createUser(
        @SpanAttribute("user.email") email: String,
        @SpanAttribute("user.name") name: String
    ): User {
        val span = Span.current()
        span.addEvent("Starting user creation process")
        
        return customInstrumentation.measureTime("create_user") {
            customInstrumentation.recordRequest("create_user")
            
            logger.info("Creating user with email: {}", email)
            span.addEvent("Validating user email")
            
            val existingUser = userRepository.findByEmail(email)
            if (existingUser != null) {
                span.addEvent("User validation failed - email exists")
                span.setAttribute("validation.error", "email_exists")
                throw IllegalArgumentException("User with email $email already exists")
            }
            
            span.addEvent("Creating user entity")
            val user = User(name = name, email = email)
            val savedUser = userRepository.save(user)
            
            span.setAttribute("user.id", savedUser.id.toString())
            span.addEvent("User created successfully")
            logger.info("User created successfully with id: {}", savedUser.id)
            savedUser
        }
    }

    @WithSpan("find-all-users")
    @Transactional(readOnly = true)
    fun findAllUsers(): List<User> {
        val span = Span.current()
        span.addEvent("Fetching all users from database")
        
        return customInstrumentation.measureTime("find_all_users") {
            customInstrumentation.recordRequest("find_all_users")
            val users = userRepository.findAll()
            span.setAttribute("users.count", users.size)
            span.addEvent("Users fetched successfully")
            users
        }
    }

    @WithSpan("find-user-by-id")
    @Transactional(readOnly = true)
    fun findUserById(@SpanAttribute("user.id") id: Long): User? {
        val span = Span.current()
        span.addEvent("Searching for user by ID")
        
        return customInstrumentation.measureTime("find_user_by_id") {
            customInstrumentation.recordRequest("find_user_by_id")
            val user = userRepository.findById(id).orElse(null)
            
            if (user != null) {
                span.setAttribute("user.found", true)
                span.setAttribute("user.email", user.email)
                span.addEvent("User found")
            } else {
                span.setAttribute("user.found", false)
                span.addEvent("User not found")
            }
            user
        }
    }
}
```

### 4.3 컨트롤러

```kotlin
@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {
    
    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<User> {
        return try {
            val user = userService.createUser(request.name, request.email)
            ResponseEntity.ok(user)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
    
    @GetMapping
    fun getAllUsers(): ResponseEntity<List<User>> {
        val users = userService.findAllUsers()
        return ResponseEntity.ok(users)
    }
    
    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<User> {
        val user = userService.findUserById(id)
        return if (user != null) {
            ResponseEntity.ok(user)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}

data class CreateUserRequest(
    val name: String,
    val email: String
)
```

## 5. 네이티브 이미지 설정 (30분)

### 5.1 reflect-config.json

```json
[
  {
    "name": "io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  }
]
```

### 5.2 native-image.properties

```properties
Args = --initialize-at-build-time=io.opentelemetry.sdk.OpenTelemetrySdk \
       --initialize-at-build-time=io.opentelemetry.api.GlobalOpenTelemetry \
       --initialize-at-run-time=io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter \
       --enable-url-protocols=http,https \
       --allow-incomplete-classpath
```

### 5.3 build.gradle.kts 네이티브 설정

```kotlin
tasks.named<BootBuildImage>("bootBuildImage") {
    builder.set("paketobuildpacks/builder:tiny")
    environment.set(mapOf(
        "BP_NATIVE_IMAGE" to "true",
        "BP_NATIVE_IMAGE_BUILD_ARGUMENTS" to "--enable-url-protocols=http,https --initialize-at-build-time=io.opentelemetry"
    ))
}
```

## 6. 실습 및 테스트 (40분)

### 6.1 로컬 테스트

```bash
# 환경변수 설정
export NEW_RELIC_LICENSE_KEY=your-license-key-here

# 네이티브 이미지 빌드
./gradlew nativeCompile

# 실행
./build/native/nativeCompile/otel-native-demo

# 또는 Docker로 빌드 및 실행
./gradlew bootBuildImage
docker run -p 8080:8080 -e NEW_RELIC_LICENSE_KEY=your-key otel-native-demo:latest
```

### 6.2 API 테스트

```bash
# 사용자 생성
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "홍길동", "email": "hong@example.com"}'

# 사용자 목록 조회
curl http://localhost:8080/api/users

# 특정 사용자 조회
curl http://localhost:8080/api/users/1

# 메트릭 확인
curl http://localhost:8080/actuator/metrics
```

## 7. 뉴렐릭에서 확인하기 (20분)

### 7.1 확인할 항목들
- **APM > Services**: 애플리케이션 성능 모니터링
- **Distributed Tracing**: 요청 흐름 추적
- **Metrics**: 커스텀 메트릭 확인
- **Logs**: 애플리케이션 로그

### 7.2 대시보드 생성
- 핵심 비즈니스 메트릭 시각화
- 에러율 및 응답시간 모니터링
- 데이터베이스 쿼리 성능 분석

## 8. 뉴렐릭 에이전트 vs OpenTelemetry 비교 및 한계점 (25분)

### 8.1 기능 비교표

| 기능 | 뉴렐릭 에이전트 | OpenTelemetry + 뉴렐릭 | 비고 |
|------|---------------|---------------------|------|
| **자동 계측** | ✅ 매우 광범위 | ⚠️ 수동 설정 필요 | OTel은 라이브러리별 추가 |
| **JVM 메트릭** | ✅ 자동 수집 | ⚠️ Micrometer 필요 | 추가 설정으로 해결 가능 |
| **네이티브 이미지** | ❌ 미지원 | ✅ 완전 지원 | OTel의 큰 장점 |
| **SLO/SLA** | ✅ UI에서 생성 | ❌ 미지원 | 뉴렐릭 에이전트 전용 |
| **에러 분석** | ✅ 자동 에러 추적 | ⚠️ 수동 설정 | exception recording 필요 |
| **Database 쿼리** | ✅ 자동 수집 | ✅ 자동 수집 | 동일한 수준 |
| **프로파일링** | ✅ 지원 | ❌ 미지원 | 에이전트만 가능 |
| **배포 추적** | ✅ 자동 | ⚠️ 수동 | API 호출 필요 |

### 8.2 OpenTelemetry 한계점 및 해결 방안

#### 8.2.1 JVM 메트릭 부족
```yaml
# 해결 방안: Micrometer 추가
management:
  metrics:
    export:
      otlp:
        enabled: true
        url: https://otlp.nr-data.net:4318/v1/metrics
```

#### 8.2.2 SLO 생성 불가
- **문제**: 뉴렐릭 UI에서 SLO 생성 불가
- **해결 방안**: 
  - NRQL 쿼리로 대시보드 생성
  - 알림 조건 수동 설정
  - 서드파티 도구 활용

#### 8.2.3 자동 계측 부족
```kotlin
// 수동으로 계측 라이브러리 추가 필요
implementation("io.opentelemetry.instrumentation:opentelemetry-kafka-clients-2.6:2.8.0-alpha")
implementation("io.opentelemetry.instrumentation:opentelemetry-redis-jedis-4.0:2.8.0-alpha")
implementation("io.opentelemetry.instrumentation:opentelemetry-elasticsearch-rest-7.0:2.8.0-alpha")
```

### 8.3 언제 뉴렐릭 에이전트를, 언제 OpenTelemetry를?

#### 뉴렐릭 에이전트 선택 시기:
- ✅ 빠른 설정이 필요한 경우
- ✅ SLO/SLA 기능이 중요한 경우  
- ✅ 프로덕션 환경에서 검증된 안정성이 필요
- ✅ JVM 환경에서만 운영하는 경우

#### OpenTelemetry 선택 시기:
- ✅ 네이티브 이미지가 필요한 경우 (필수)
- ✅ 벤더 락인 방지가 중요한 경우
- ✅ 커스텀 계측이 많이 필요한 경우
- ✅ 다른 observability 백엔드로 전환 가능성
- ✅ 컨테이너/쿠버네티스 환경에서 성능 최적화 필요

### 8.4 하이브리드 접근법

실제로는 다음과 같은 하이브리드 접근이 가능합니다:

```kotlin
// 일부 서비스는 뉴렐릭 에이전트
// 네이티브 이미지 서비스만 OpenTelemetry 사용

// 동일한 뉴렐릭 계정에서 두 데이터 모두 볼 수 있음
```

## 9. 문제 해결 및 최적화 (20분)

### 9.1 일반적인 문제들
- **네이티브 이미지 빌드 실패**: Reflection 설정 누락
- **메트릭 전송 안됨**: 네트워크 설정 또는 API 키 오류
- **성능 이슈**: Batch 설정 최적화 필요
- **Span 누락**: @WithSpan 어노테이션이나 자동 계측 라이브러리 누락
- **JVM 메트릭 부족**: Micrometer 설정 필요

### 9.2 성능 최적화 팁
```kotlin
// Batch 크기 조정
BatchSpanProcessor.builder(exporter)
    .setMaxExportBatchSize(512)
    .setExportTimeout(Duration.ofSeconds(2))
    .setScheduleDelay(Duration.ofSeconds(5))
    .build()

// 샘플링 설정
TraceIdRatioBasedSampler.create(0.1) // 10% 샘플링
```

### 9.3 네이티브 이미지 최적화
```properties
# native-image.properties
Args = --initialize-at-build-time=io.opentelemetry.sdk.OpenTelemetrySdk \
       --initialize-at-build-time=io.opentelemetry.api.GlobalOpenTelemetry \
       --enable-url-protocols=http,https \
       --allow-incomplete-classpath \
       -H:+ReportExceptionStackTraces
```

## 과제
1. **기본 과제**: 위의 예제를 따라하여 네이티브 이미지로 빌드하고 뉴렐릭에서 데이터 확인
2. **중급 과제**: 기존 뉴렐릭 에이전트 사용 서비스와 OpenTelemetry 서비스의 메트릭/트레이스 비교 분석
3. **심화 과제**: 자신의 프로젝트에 OpenTelemetry 적용하고 커스텀 메트릭 3개 이상 추가, @WithSpan 어노테이션 5개 이상 적용
4. **도전 과제**: 
   - 뉴렐릭 에이전트에서 OpenTelemetry로 마이그레이션 플랜 작성
   - 누락된 JVM 메트릭을 Micrometer로 보완하여 에이전트와 동일한 수준 달성
   - SLO 대신 NRQL 쿼리 기반 대시보드 및 알림 구성

## ⚠️ 중요한 결론

**OpenTelemetry로 뉴렐릭 에이전트를 100% 대체하는 것은 현재 불가능합니다.**

### 대체 가능한 부분:
- ✅ 기본적인 APM 모니터링 (트레이스, 메트릭)
- ✅ 네이티브 이미지 지원
- ✅ 커스텀 계측
- ✅ 데이터베이스 쿼리 모니터링

### 대체 불가능한 부분:
- ❌ SLO/SLA 자동 생성
- ❌ 일부 고급 APM 기능
- ❌ 자동 프로파일링
- ❌ 완전한 JVM 메트릭 (설정으로 보완 가능)

**권장사항**: 네이티브 이미지가 필요한 서비스만 OpenTelemetry를 사용하고, 나머지는 뉴렐릭 에이전트를 유지하는 하이브리드 접근법을 고려해보세요.

## 다음 주 준비사항
- 하이브리드 모니터링 환경에서의 통합 대시보드 구성
- OpenTelemetry와 뉴렐릭 에이전트 데이터 상관관계 분석 방법
- 프로덕션 환경 배포 전략 수립
