# 3주차: 데이터베이스 연동 및 JPA 활용

## 학습 목표
- 네이티브 이미지에서 데이터베이스 연결 구성 방법 이해
- JPA/Hibernate와 네이티브 이미지 통합 시 고려사항 학습
- 리액티브 데이터베이스 접근 방식(R2DBC) 이해
- 트랜잭션 관리 및 최적화 방법 습득
- 실제 데이터베이스 연동 애플리케이션 개발 및 네이티브 이미지 변환

## 1. 네이티브 이미지와 데이터베이스 연결

### 데이터베이스 드라이버 지원 현황

네이티브 이미지에서 사용 가능한 주요 데이터베이스 드라이버:

| 데이터베이스 | JDBC 드라이버 | 네이티브 이미지 지원 | 고려사항 |
|------------|--------------|-------------------|---------|
| H2 | `h2` | ✅ 완전 지원 | 메모리 모드 권장 |
| PostgreSQL | `postgresql` | ✅ 완전 지원 | 추가 설정 필요 없음 |
| MySQL | `mysql-connector-java` | ✅ 완전 지원 | SSL 사용 시 추가 설정 필요 |
| MariaDB | `mariadb-java-client` | ✅ 완전 지원 | 최신 버전 권장 |
| Oracle | `ojdbc8` | ✅ 지원 | 연결 풀 설정 주의 |
| SQL Server | `mssql-jdbc` | ✅ 지원 | 인증 방식에 따라 추가 설정 필요 |
| SQLite | `sqlite-jdbc` | ⚠️ 부분 지원 | 네이티브 라이브러리 이슈 주의 |

### JDBC 연결 설정 예제

#### application.properties
```properties
# PostgreSQL 설정
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=postgres
spring.datasource.password=password
spring.datasource.driver-class-name=org.postgresql.Driver

# 연결 풀 설정 (HikariCP)
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.connection-timeout=20000

# JPA 설정
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.show-sql=true
```

#### DataSource 커스텀 구성 (Kotlin)
```kotlin
@Configuration
class DataSourceConfig {

    @Bean
    fun dataSource(
        @Value("\${spring.datasource.url}

## 8. 네이티브 이미지 빌드 및 배포

### 네이티브 이미지 빌드 설정 (JPA 애플리케이션)

#### build.gradle.kts
```kotlin
plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.graalvm.buildtools.native") version "0.9.28"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    kotlin("plugin.jpa") version "1.9.20"
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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
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
            imageName.set("blog-api")
            buildArgs.add("--verbose")
            buildArgs.add("--no-fallback")
            buildArgs.add("--initialize-at-run-time=org.postgresql.Driver,org.postgresql.util.SharedTimer")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}
```

### 네이티브 이미지 빌드 설정 (R2DBC 애플리케이션)

#### build.gradle.kts
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
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("io.r2dbc:r2dbc-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("io.r2dbc:r2dbc-h2")
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
            imageName.set("reactive-blog-api")
            buildArgs.add("--verbose")
            buildArgs.add("--no-fallback")
            buildArgs.add("--initialize-at-run-time=io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}
```

### Docker를 이용한 빌드 및 배포

#### Dockerfile (JPA 애플리케이션)
```dockerfile
FROM ghcr.io/graalvm/native-image:ol8-java17 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew nativeCompile

FROM oraclelinux:8-slim
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/blog-api .
EXPOSE 8080
ENTRYPOINT ["./blog-api"]
```

#### Dockerfile (R2DBC 애플리케이션)
```dockerfile
FROM ghcr.io/graalvm/native-image:ol8-java17 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew nativeCompile

FROM oraclelinux:8-slim
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/reactive-blog-api .
EXPOSE 8080
ENTRYPOINT ["./reactive-blog-api"]
```

#### docker-compose.yml
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:14-alpine
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: blogdb
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  jpa-blog-api:
    build:
      context: ./jpa-blog
      dockerfile: Dockerfile
    container_name: jpa-blog-api
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/blogdb
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
    depends_on:
      postgres:
        condition: service_healthy

  reactive-blog-api:
    build:
      context: ./reactive-blog
      dockerfile: Dockerfile
    container_name: reactive-blog-api
    ports:
      - "8081:8080"
    environment:
      SPRING_R2DBC_URL: r2dbc:postgresql://postgres:5432/blogdb
      SPRING_R2DBC_USERNAME: postgres
      SPRING_R2DBC_PASSWORD: postgres
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres-data:
```

## 9. 성능 테스트 및 비교

### 측정 항목

1. **시작 시간 비교**:
   - JVM 모드 vs 네이티브 이미지
   - JPA vs R2DBC

2. **메모리 사용량**:
   - JVM 모드 vs 네이티브 이미지
   - JPA vs R2DBC

3. **응답 시간**:
   - 첫 요청 응답 시간
   - 평균 응답 시간
   - 최대 처리량(TPS)

### 테스트 도구

1. **JMeter** 또는 **Apache Bench(ab)**: 부하 테스트
2. **VisualVM** 또는 **JConsole**: JVM 모니터링
3. **Prometheus + Grafana**: 메트릭 수집 및 시각화

### 테스트 시나리오 예시

1. **사용자 조회 테스트**:
   ```bash
   ab -n 10000 -c 100 http://localhost:8080/api/users
   ```

2. **게시물 생성 테스트**:
   ```bash
   ab -n 1000 -c 10 -p post-data.json -T application/json http://localhost:8080/api/posts
   ```

3. **트랜잭션 처리 테스트**:
   ```bash
   ab -n 500 -c 10 http://localhost:8080/api/posts/1/comments
   ```

## 10. 과제

1. 1주차와 2주차에서 만든 프로젝트를 확장하여 다음 기능을 구현하세요:
   - JPA를 사용한 데이터베이스 연동
   - 간단한 엔티티 설계 및 구현 (최소 3개 이상의 관계있는 엔티티)
   - 트랜잭션 처리가 필요한 비즈니스 로직 구현

2. JPA/Hibernate를 사용하는 애플리케이션을 네이티브 이미지로 변환하고 다음 사항을 확인하세요:
   - 리플렉션, 프록시, 리소스 관련 설정 적용
   - 트랜잭션 처리 동작 확인
   - Lazy 로딩 및 연관 관계 동작 확인

3. R2DBC를 사용한 리액티브 버전의 애플리케이션을 구현하고 다음 사항을 비교하세요:
   - JPA vs R2DBC 코드 구조 차이
   - 비동기 트랜잭션 처리 방식
   - 성능 차이 (처리량, 응답 시간, 메모리 사용량)

4. Docker를 사용하여 네이티브 이미지를 컨테이너화하고 배포하세요:
   - docker-compose로 애플리케이션 + 데이터베이스 환경 구성
   - 환경 변수를 통한 설정 관리
   - 컨테이너 리소스 사용량 모니터링

5. 다음 항목에 대한 성능 테스트를 수행하고 결과를 정리하세요:
   - JVM vs 네이티브 이미지 시작 시간
   - JVM vs 네이티브 이미지 메모리 사용량
   - JPA vs R2DBC 처리량
   - JPA vs R2DBC 응답 시간
   - 트랜잭션 처리 성능

## 11. 참고 자료

### 공식 문서
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Spring Data R2DBC Reference](https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/)
- [Hibernate ORM GraalVM Native Image Support](https://hibernate.org/orm/documentation/5.6/native-images/)
- [Reactive Streams Specification](https://github.com/reactive-streams/reactive-streams-jvm)

### 블로그 및 튜토리얼
- [Reactive Programming with Spring Boot and R2DBC](https://spring.io/blog/2019/12/13/reactive-sql-databases-with-r2dbc-and-spring)
- [GraalVM Native Image Support in Spring Boot 3](https://www.baeldung.com/spring-native-intro)
- [Transactional Tests with Spring and JPA](https://www.baeldung.com/spring-transactional-tests)

### 샘플 프로젝트
- [Spring Boot Native JPA Sample](https://github.com/spring-projects-experimental/spring-native-samples/tree/main/jpa)
- [Spring Boot Native R2DBC Sample](https://github.com/spring-projects-experimental/spring-native-samples/tree/main/r2dbc)
- [Spring Boot Petclinic R2DBC](https://github.com/spring-petclinic/spring-petclinic-r2dbc)

---

## 다음 주차 미리보기
- 네이티브 이미지 테스트 전략 및 디버깅
- 통합 테스트 작성 방법
- 네이티브 이미지 디버깅 기법
- CI/CD 파이프라인 구성

### 컨트롤러 계층

```kotlin
// UserController.kt
@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {
    
    @GetMapping
    fun getAllUsers(): ResponseEntity<List<User>> =
        ResponseEntity.ok(userService.getAllUsers())
    
    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<User> =
        ResponseEntity.ok(userService.getUserById(id))
    
    @PostMapping
    fun createUser(@RequestBody user: User): ResponseEntity<User> =
        ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(user))
    
    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: Long, @RequestBody user: User): ResponseEntity<User> =
        ResponseEntity.ok(userService.updateUser(id, user))
    
    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Unit> {
        userService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }
    
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to (e.message ?: "Resource not found")))
}

// PostController.kt
@RestController
@RequestMapping("/api/posts")
class PostController(
    private val postService: PostService,
    private val userService: UserService
) {
    
    @GetMapping
    fun getAllPosts(): ResponseEntity<List<Post>> =
        ResponseEntity.ok(postService.getAllPosts())
    
    @GetMapping("/{id}")
    fun getPostById(@PathVariable id: Long): ResponseEntity<Post> =
        ResponseEntity.ok(postService.getPostById(id))
    
    @GetMapping("/user/{userId}")
    fun getPostsByUser(@PathVariable userId: Long): ResponseEntity<List<Post>> =
        ResponseEntity.ok(postService.getPostsByUser(userId))
    
    @PostMapping
    fun createPost(
        @RequestBody postRequest: PostRequest
    ): ResponseEntity<Post> {
        val author = userService.getUserById(postRequest.authorId)
        
        val post = Post(
            title = postRequest.title,
            content = postRequest.content,
            author = author
        )
        
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(post))
    }
    
    @PutMapping("/{id}")
    fun updatePost(
        @PathVariable id: Long,
        @RequestBody postRequest: PostRequest
    ): ResponseEntity<Post> {
        val author = userService.getUserById(postRequest.authorId)
        
        val post = Post(
            title = postRequest.title,
            content = postRequest.content,
            author = author
        )
        
        return ResponseEntity.ok(postService.updatePost(id, post))
    }
    
    @DeleteMapping("/{id}")
    fun deletePost(@PathVariable id: Long): ResponseEntity<Unit> {
        postService.deletePost(id)
        return ResponseEntity.noContent().build()
    }
}

// Data Transfer Objects
data class PostRequest(
    val title: String,
    val content: String,
    val authorId: Long
)

// CommentController.kt
@RestController
@RequestMapping("/api/posts/{postId}/comments")
class CommentController(private val commentService: CommentService) {
    
    @GetMapping
    fun getCommentsByPost(@PathVariable postId: Long): ResponseEntity<List<Comment>> =
        ResponseEntity.ok(commentService.getCommentsByPost(postId))
    
    @PostMapping
    fun addComment(
        @PathVariable postId: Long,
        @RequestBody commentRequest: CommentRequest
    ): ResponseEntity<Comment> {
        val comment = commentService.addComment(
            postId = postId,
            userId = commentRequest.authorId,
            content = commentRequest.content
        )
        
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }
    
    @DeleteMapping("/{id}")
    fun deleteComment(
        @PathVariable postId: Long,
        @PathVariable id: Long
    ): ResponseEntity<Unit> {
        commentService.deleteComment(id)
        return ResponseEntity.noContent().build()
    }
}

// Data Transfer Objects
data class CommentRequest(
    val content: String,
    val authorId: Long
)
```

### 네이티브 이미지 힌트 설정

```kotlin
@Configuration
@ImportRuntimeHints(BlogHints::class)
class BlogHintsConfig

class BlogHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // 엔티티에 대한 리플렉션 설정
        hints.reflection()
            .registerType(User::class.java, MemberCategory.values().toList())
            .registerType(Post::class.java, MemberCategory.values().toList())
            .registerType(Comment::class.java, MemberCategory.values().toList())
            .registerType(UserRole::class.java, MemberCategory.values().toList())
            .registerType(PostRequest::class.java, MemberCategory.values().toList())
            .registerType(CommentRequest::class.java, MemberCategory.values().toList())
        
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
```

### 데이터베이스 스키마 및 테스트 데이터

```sql
-- schema.sql
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS posts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- data.sql
INSERT INTO users (name, email, password, role)
VALUES
('Admin User', 'admin@example.com', '$2a$10$hKDVYxLefVHV/vV76kXH5.7IUh/MBru7l7GuP5/e6KhlrQw2oqTFu', 'ADMIN'),
('John Doe', 'john@example.com', '$2a$10$hKDVYxLefVHV/vV76kXH5.7IUh/MBru7l7GuP5/e6KhlrQw2oqTFu', 'USER'),
('Jane Smith', 'jane@example.com', '$2a$10$hKDVYxLefVHV/vV76kXH5.7IUh/MBru7l7GuP5/e6KhlrQw2oqTFu', 'USER');

INSERT INTO posts (title, content, user_id)
VALUES
('First Post', 'This is the content of the first post', 1),
('Spring Boot Guide', 'Comprehensive guide to Spring Boot', 2),
('Kotlin for Beginners', 'Getting started with Kotlin', 2),
('Native Images in GraalVM', 'How to build native images with GraalVM', 3);

INSERT INTO comments (content, post_id, user_id)
VALUES
('Great post!', 1, 2),
('Thanks for sharing', 1, 3),
('Very informative', 2, 1),
('Looking forward to more content', 3, 1),
('This was helpful', 4, 2);
```

## 6. 실습: R2DBC 기반 리액티브 애플리케이션 개발

### 도메인 모델

```kotlin
// User.kt
@Table("users")
data class User(
    @Id
    val id: Long? = null,
    
    val name: String,
    
    val email: String,
    
    val password: String,
    
    val role: String = "USER",
    
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// Post.kt
@Table("posts")
data class Post(
    @Id
    val id: Long? = null,
    
    val title: String,
    
    val content: String,
    
    @Column("user_id")
    val userId: Long,
    
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column("updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

// PostWithAuthor.kt (Join 결과를 위한 DTO)
data class PostWithAuthor(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val userId: Long,
    val authorName: String,
    val authorEmail: String
)

// Comment.kt
@Table("comments")
data class Comment(
    @Id
    val id: Long? = null,
    
    val content: String,
    
    @Column("post_id")
    val postId: Long,
    
    @Column("user_id")
    val userId: Long,
    
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// CommentWithAuthor.kt (Join 결과를 위한 DTO)
data class CommentWithAuthor(
    val id: Long,
    val content: String,
    val createdAt: LocalDateTime,
    val postId: Long,
    val userId: Long,
    val authorName: String
)
```

### 리포지토리 계층

```kotlin
// UserRepository.kt
interface UserRepository : ReactiveCrudRepository<User, Long> {
    fun findByEmail(email: String): Mono<User>
}

// PostRepository.kt
interface PostRepository : ReactiveCrudRepository<Post, Long> {
    fun findByUserId(userId: Long): Flux<Post>
    
    @Query("""
        SELECT p.id, p.title, p.content, p.created_at, p.updated_at, p.user_id, 
               u.name as author_name, u.email as author_email
        FROM posts p
        JOIN users u ON p.user_id = u.id
        ORDER BY p.created_at DESC
    """)
    fun findAllWithAuthor(): Flux<Map<String, Any>>
    
    @Query("""
        SELECT p.id, p.title, p.content, p.created_at, p.updated_at, p.user_id, 
               u.name as author_name, u.email as author_email
        FROM posts p
        JOIN users u ON p.user_id = u.id
        WHERE p.id = :id
    """)
    fun findByIdWithAuthor(id: Long): Mono<Map<String, Any>>
}

// CommentRepository.kt
interface CommentRepository : ReactiveCrudRepository<Comment, Long> {
    fun findByPostId(postId: Long): Flux<Comment>
    
    @Query("""
        SELECT c.id, c.content, c.created_at, c.post_id, c.user_id, u.name as author_name
        FROM comments c
        JOIN users u ON c.user_id = u.id
        WHERE c.post_id = :postId
        ORDER BY c.created_at ASC
    """)
    fun findByPostIdWithAuthor(postId: Long): Flux<Map<String, Any>>
}
```

### 서비스 계층

```kotlin
// UserService.kt
@Service
class UserService(private val userRepository: UserRepository) {
    
    fun getAllUsers(): Flux<User> = userRepository.findAll()
    
    fun getUserById(id: Long): Mono<User> = userRepository.findById(id)
        .switchIfEmpty(Mono.error(NoSuchElementException("User not found with id: $id")))
    
    fun getUserByEmail(email: String): Mono<User> = userRepository.findByEmail(email)
        .switchIfEmpty(Mono.error(NoSuchElementException("User not found with email: $email")))
    
    @Transactional
    fun createUser(user: User): Mono<User> = userRepository.save(user)
    
    @Transactional
    fun updateUser(id: Long, user: User): Mono<User> {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(NoSuchElementException("User not found with id: $id")))
            .flatMap { userRepository.save(user.copy(id = it.id)) }
    }
    
    @Transactional
    fun deleteUser(id: Long): Mono<Void> = userRepository.deleteById(id)
}

// PostService.kt
@Service
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) {
    
    fun getAllPosts(): Flux<PostWithAuthor> {
        return postRepository.findAllWithAuthor()
            .map { row -> mapToPostWithAuthor(row) }
    }
    
    fun getPostById(id: Long): Mono<PostWithAuthor> {
        return postRepository.findByIdWithAuthor(id)
            .switchIfEmpty(Mono.error(NoSuchElementException("Post not found with id: $id")))
            .map { row -> mapToPostWithAuthor(row) }
    }
    
    fun getPostsByUser(userId: Long): Flux<Post> {
        return userRepository.findById(userId)
            .switchIfEmpty(Mono.error(NoSuchElementException("User not found with id: $userId")))
            .flatMapMany { postRepository.findByUserId(userId) }
    }
    
    @Transactional
    fun createPost(post: Post): Mono<Post> {
        return userRepository.findById(post.userId)
            .switchIfEmpty(Mono.error(NoSuchElementException("User not found with id: ${post.userId}")))
            .flatMap { postRepository.save(post) }
    }
    
    @Transactional
    fun updatePost(id: Long, post: Post): Mono<Post> {
        return postRepository.findById(id)
            .switchIfEmpty(Mono.error(NoSuchElementException("Post not found with id: $id")))
            .flatMap { existingPost ->
                userRepository.findById(post.userId)
                    .switchIfEmpty(Mono.error(NoSuchElementException("User not found with id: ${post.userId}")))
                    .flatMap {
                        postRepository.save(post.copy(
                            id = existingPost.id,
                            createdAt = existingPost.createdAt,
                            updatedAt = LocalDateTime.now()
                        ))
                    }
            }
    }
    
    @Transactional
    fun deletePost(id: Long): Mono<Void> = postRepository.deleteById(id)
    
    private fun mapToPostWithAuthor(row: Map<String, Any>): PostWithAuthor {
        return PostWithAuthor(
            id = (row["id"] as Number).toLong(),
            title = row["title"] as String,
            content = row["content"] as String,
            createdAt = row["created_at"] as LocalDateTime,
            updatedAt = row["updated_at"] as LocalDateTime,
            userId = (row["user_id"] as Number).toLong(),
            authorName = row["author_name"] as String,
            authorEmail = row["author_email"] as String
        )
    }
}

// CommentService.kt
@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) {
    
    fun getCommentsByPost(postId: Long): Flux<CommentWithAuthor> {
        return postRepository.findById(postId)
            .switchIfEmpty(Mono.error(NoSuchElementException("Post not found with id: $postId")))
            .flatMapMany {
                commentRepository.findByPostIdWithAuthor(postId)
                    .map { row -> mapToCommentWithAuthor(row) }
            }
    }
    
    @Transactional
    fun addComment(comment: Comment): Mono<Comment> {
        return Mono.zip(
            postRepository.findById(comment.postId)
                .switchIfEmpty(Mono.error(NoSuchElementException("Post not found with id: ${comment.postId}"))),
            userRepository.findById(comment.userId)
                .switchIfEmpty(Mono.error(NoSuchElementException("User not found with id: ${comment.userId}")))
        ).flatMap { tuple ->
            commentRepository.save(comment)
        }
    }
    
    @Transactional
    fun deleteComment(id: Long): Mono<Void> = commentRepository.deleteById(id)
    
    private fun mapToCommentWithAuthor(row: Map<String, Any>): CommentWithAuthor {
        return CommentWithAuthor(
            id = (row["id"] as Number).toLong(),
            content = row["content"] as String,
            createdAt = row["created_at"] as LocalDateTime,
            postId = (row["post_id"] as Number).toLong(),
            userId = (row["user_id"] as Number).toLong(),
            authorName = row["author_name"] as String
        )
    }
}
```

### 컨트롤러 계층

```kotlin
// UserController.kt
@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {
    
    @GetMapping
    fun getAllUsers(): Flux<User> = userService.getAllUsers()
    
    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): Mono<User> = userService.getUserById(id)
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody user: User): Mono<User> = userService.createUser(user)
    
    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: Long, @RequestBody user: User): Mono<User> =
        userService.updateUser(id, user)
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable id: Long): Mono<Void> = userService.deleteUser(id)
    
    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(e: NoSuchElementException): Mono<Map<String, String>> =
        Mono.just(mapOf("error" to (e.message ?: "Resource not found")))
}

// PostController.kt
@RestController
@RequestMapping("/api/posts")
class PostController(private val postService: PostService) {
    
    @GetMapping
    fun getAllPosts(): Flux<PostWithAuthor> = postService.getAllPosts()
    
    @GetMapping("/{id}")
    fun getPostById(@PathVariable id: Long): Mono<PostWithAuthor> = postService.getPostById(id)
    
    @GetMapping("/user/{userId}")
    fun getPostsByUser(@PathVariable userId: Long): Flux<Post> = postService.getPostsByUser(userId)
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPost(@RequestBody post: Post): Mono<Post> = postService.createPost(post)
    
    @PutMapping("/{id}")
    fun updatePost(@PathVariable id: Long, @RequestBody post: Post): Mono<Post> =
        postService.updatePost(id, post)
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePost(@PathVariable id: Long): Mono<Void> = postService.deletePost(id)
}

// CommentController.kt
@RestController
@RequestMapping("/api/posts/{postId}/comments")
class CommentController(private val commentService: CommentService) {
    
    @GetMapping
    fun getCommentsByPost(@PathVariable postId: Long): Flux<CommentWithAuthor> =
        commentService.getCommentsByPost(postId)
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addComment(@PathVariable postId: Long, @RequestBody comment: Comment): Mono<Comment> =
        commentService.addComment(comment.copy(postId = postId))
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComment(@PathVariable id: Long): Mono<Void> =
        commentService.deleteComment(id)
}
```

### 네이티브 이미지 힌트 설정

```kotlin
@Configuration
@ImportRuntimeHints(ReactiveHints::class)
class ReactiveHintsConfig

class ReactiveHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // 엔티티에 대한 리플렉션 설정
        hints.reflection()
            .registerType(User::class.java, MemberCategory.values().toList())
            .registerType(Post::class.java, MemberCategory.values().toList())
            .registerType(Comment::class.java, MemberCategory.values().toList())
            .registerType(PostWithAuthor::class.java, MemberCategory.values().toList())
            .registerType(CommentWithAuthor::class.java, MemberCategory.values().toList())
        
        // R2DBC 관련 리소스 설정
        hints.resources()
            .registerPattern("META-INF/r2dbc.properties")
            .registerPattern("schema.sql")
            .registerPattern("data.sql")
        
        // R2DBC 드라이버 초기화
        hints.reflection()
            .registerType(io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider::class.java, 
                MemberCategory.values().toList())
        
        // 프록시 설정
        hints.proxies()
            .registerJdkProxy(UserRepository::class.java, ReactiveCrudRepository::class.java)
            .registerJdkProxy(PostRepository::class.java, ReactiveCrudRepository::class.java)
            .registerJdkProxy(CommentRepository::class.java, ReactiveCrudRepository::class.java)
    }
}
```

## 7. 통합 테스트 작성

### JPA 통합 테스트

```kotlin
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceIntegrationTest {
    
    @Autowired
    lateinit var userService: UserService
    
    @Autowired
    lateinit var userRepository: UserRepository
    
    @BeforeAll
    fun setup() {
        // 테스트 데이터 초기화
    }
    
    @AfterAll
    fun cleanup() {
        userRepository.deleteAll()
    }
    
    @Test
    fun `when findById with existing id then return user`() {
        // given
        val user = User(name = "Test User", email = "test@example.com", password = "password")
        val savedUser = userRepository.save(user).block()!!
        
        // when
        val result = userService.getUserById(savedUser.id!!).block()
        
        // then
        assertNotNull(result)
        assertEquals(savedUser.id, result!!.id)
        assertEquals(savedUser.name, result.name)
        assertEquals(savedUser.email, result.email)
    }
    
    @Test
    fun `when findById with non-existing id then throw exception`() {
        // given
        val nonExistingId = 999L
        
        // when, then
        assertThrows<NoSuchElementException> {
            userService.getUserById(nonExistingId).block()
        }
    }
    
    @Test
    fun `when createUser with valid data then create and return user`() {
        // given
        val user = User(name = "New Reactive User", email = "reactive@example.com", password = "password")
        
        // when
        val result = userService.createUser(user).block()
        
        // then
        assertNotNull(result!!.id)
        assertEquals(user.name, result.name)
        assertEquals(user.email, result.email)
        
        // verify in database
        val savedUser = userRepository.findById(result.id!!).block()
        assertNotNull(savedUser)
        assertEquals(user.name, savedUser!!.name)
    }
}", email = "test@example.com", password = "password")
        val savedUser = userRepository.save(user)
        
        // when
        val result = userService.getUserById(savedUser.id!!)
        
        // then
        assertNotNull(result)
        assertEquals(savedUser.id, result.id)
        assertEquals(savedUser.name, result.name)
        assertEquals(savedUser.email, result.email)
    }
    
    @Test
    fun `when findById with non-existing id then throw exception`() {
        // given
        val nonExistingId = 999L
        
        // when, then
        assertThrows<NoSuchElementException> {
            userService.getUserById(nonExistingId)
        }
    }
    
    @Test
    @Transactional
    fun `when createUser with valid data then create and return user`() {
        // given
        val user = User(name = "New User", email = "new@example.com", password = "password")
        
        // when
        val result = userService.createUser(user)
        
        // then
        assertNotNull(result.id)
        assertEquals(user.name, result.name)
        assertEquals(user.email, result.email)
        
        // verify in database
        val savedUser = userRepository.findById(result.id!!)
        assertTrue(savedUser.isPresent)
        assertEquals(user.name, savedUser.get().name)
    }
}
```

### R2DBC 통합 테스트

```kotlin
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceReactiveIntegrationTest {
    
    @Autowired
    lateinit var userService: UserService
    
    @Autowired
    lateinit var userRepository: UserRepository
    
    @BeforeAll
    fun setup() {
        // 테스트 데이터 초기화
        userRepository.deleteAll().block()
    }
    
    @AfterAll
    fun cleanup() {
        userRepository.deleteAll().block()
    }
    
    @Test
    fun `when findById with existing id then return user`() {
        // given
        val user = User(name = "Test User

// PostService.kt
@Service
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) {
    
    @Transactional(readOnly = true)
    fun getAllPosts(): List<Post> = postRepository.findAllWithAuthor()
    
    @Transactional(readOnly = true)
    fun getPostById(id: Long): Post = postRepository.findById(id)
        .orElseThrow { NoSuchElementException("Post not found with id: $id") }
    
    @Transactional(readOnly = true)
    fun getPostsByUser(userId: Long): List<Post> {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }
        return postRepository.findByAuthor(user)
    }
    
    @Transactional
    fun createPost(post: Post): Post = postRepository.save(post)
    
    @Transactional
    fun updatePost(id: Long, post: Post): Post {
        val existingPost = getPostById(id)
        return postRepository.save(post.copy(
            id = existingPost.id,
            author = existingPost.author,
            createdAt = existingPost.createdAt,
            updatedAt = LocalDateTime.now()
        ))
    }
    
    @Transactional
    fun deletePost(id: Long) = postRepository.deleteById(id)
}

// CommentService.kt
@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) {
    
    @Transactional(readOnly = true)
    fun getCommentsByPost(postId: Long): List<Comment> {
        val post = postRepository.findById(postId)
            .orElseThrow { NoSuchElementException("Post not found with id: $postId") }
        return commentRepository.findByPostWithAuthor(post)
    }
    
    @Transactional
    fun addComment(postId: Long, userId: Long, content: String): Comment {
        val post = postRepository.findById(postId)
            .orElseThrow { NoSuchElementException("Post not found with id: $postId") }
        
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }
        
        val comment = Comment(
            content = content,
            post = post,
            author = user
        )
        
        return commentRepository.save(comment)
    }
    
    @Transactional
    fun deleteComment(id: Long) = commentRepository.deleteById(id)
}") url: String,
        @Value("\${spring.datasource.username}") username: String,
        @Value("\${spring.datasource.password}") password: String
    ): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            maximumPoolSize = 10
            minimumIdle = 5
            idleTimeout = 30000
            connectionTimeout = 20000
            // 네이티브 이미지에서 최적화를 위한 설정
            isRegisterMbeans = false
        }
        return HikariDataSource(config)
    }
}
```

### 네이티브 이미지에서 데이터베이스 연결 시 고려사항

1. **초기화 시점 주의**:
   - 대부분의 데이터베이스 드라이버는 런타임 초기화가 필요
   - 네이티브 이미지 빌드 시 `--initialize-at-run-time` 옵션 추가

   ```kotlin
   graalvmNative {
       binaries {
           named("main") {
               buildArgs.add("--initialize-at-run-time=org.postgresql.Driver,org.postgresql.util.SharedTimer")
           }
       }
   }
   ```

2. **드라이버 등록 방식**:
   - `DriverManager.getConnection()` 보다 `DataSource` 구현체 사용 권장
   - 일부 드라이버는 `ServiceLoader` 방식으로 자동 등록

3. **JNI 및 네이티브 라이브러리**:
   - 일부 드라이버는 JNI 나 네이티브 라이브러리 사용
   - 네이티브 이미지에 포함되도록 추가 설정 필요

## 2. JPA와 네이티브 이미지 통합

### JPA/Hibernate 호환성

Hibernate 6.x부터 네이티브 이미지를 공식적으로 지원하며, Spring Boot 3.x와 통합 시 대부분의 JPA 기능이 정상 동작합니다.

#### 엔티티 예제
```kotlin
@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false, length = 100)
    val name: String,
    
    @Column(nullable = false, unique = true)
    val email: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole = UserRole.USER,
    
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    val posts: MutableList<Post> = mutableListOf(),
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class UserRole {
    USER, ADMIN, MODERATOR
}

@Entity
@Table(name = "posts")
data class Post(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false)
    val title: String,
    
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

#### 리포지토리 인터페이스
```kotlin
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    
    @Query("SELECT u FROM User u WHERE u.role = :role")
    fun findAllByRole(@Param("role") role: UserRole): List<User>
}

@Repository
interface PostRepository : JpaRepository<Post, Long> {
    fun findByUser(user: User): List<Post>
    
    @Query("""
        SELECT p FROM Post p
        JOIN p.user u
        WHERE u.id = :userId
        ORDER BY p.createdAt DESC
    """)
    fun findPostsByUserId(@Param("userId") userId: Long): List<Post>
}
```

### JPA 최적화 설정

네이티브 이미지에서 JPA를 사용할 때 최적화를 위한 설정:

#### application.properties
```properties
# Hibernate 최적화 설정
spring.jpa.properties.hibernate.generate_statistics=false
spring.jpa.properties.hibernate.id.new_generator_mappings=true
spring.jpa.properties.hibernate.connection.provider_disables_autocommit=true
spring.jpa.properties.hibernate.query.in_clause_parameter_padding=true
spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true
spring.jpa.properties.hibernate.query.plan_cache_max_size=2048
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.jdbc.fetch_size=50
spring.jpa.properties.hibernate.default_batch_fetch_size=50
```

### 네이티브 이미지를 위한 JPA 힌트 설정

Spring Boot 3.x는 JPA 관련 힌트를 자동으로 설정하지만, 커스텀 엔티티나 특별한 JPA 기능을 사용하는 경우 추가 힌트가 필요할 수 있습니다:

```kotlin
@Configuration
class JpaHintsConfig {
    
    @Bean
    fun jpaHints(): RuntimeHintsRegistrar {
        return RuntimeHintsRegistrar { hints, _ ->
            // 엔티티 클래스에 대한 리플렉션 설정
            hints.reflection()
                .registerType(User::class.java, MemberCategory.values().toList())
                .registerType(Post::class.java, MemberCategory.values().toList())
                .registerType(UserRole::class.java, MemberCategory.values().toList())
                
            // JPA 쿼리 파일 리소스 설정
            hints.resources()
                .registerPattern("META-INF/orm.xml")
                .registerPattern("META-INF/jpa-named-queries.properties")
                
            // 프록시 설정 (JPA 리포지토리)
            hints.proxies()
                .registerJdkProxy(UserRepository::class.java, JpaRepository::class.java)
                .registerJdkProxy(PostRepository::class.java, JpaRepository::class.java)
        }
    }
}
```

### 네이티브 이미지에서 JPA 사용 시 주의사항

1. **Eager vs Lazy 로딩**:
   - Eager 로딩은 네이티브 이미지에서 문제가 적음
   - Lazy 로딩 사용 시 프록시 설정 필요
   - `@Transactional` 범위에서 Lazy 로딩 처리 권장

2. **프로젝션과 DTO**:
   - 인터페이스 기반 프로젝션 사용 시 힌트 등록 필요
   - 클래스 기반 DTO 사용이 더 안정적
   - JPQL에서 `new` 키워드로 생성자 호출 방식 권장

3. **동적 쿼리 및 Criteria API**:
   - Criteria API는 프록시와 리플렉션 사용
   - 네이티브 이미지에서 사용 시 추가 설정 필요
   - 가능하면 정적 JPQL 쿼리 사용 권장

4. **네이티브 SQL**:
   - 네이티브 SQL은 문제 없이 동작
   - 결과 매핑에 사용되는 클래스는 힌트 등록 필요

## 3. R2DBC와 리액티브 데이터베이스 접근

### R2DBC 소개

R2DBC(Reactive Relational Database Connectivity)는 관계형 데이터베이스에 대한 리액티브 프로그래밍 API를 제공합니다.

#### R2DBC의 장점
- 비동기 논블로킹 데이터베이스 접근
- 적은 스레드로 높은 처리량 달성
- Reactive Streams 표준 기반
- 백프레셔(Backpressure) 지원

### R2DBC 설정

#### 의존성 추가 (build.gradle.kts)
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.r2dbc:r2dbc-postgresql") // PostgreSQL용 R2DBC 드라이버
    testImplementation("io.projectreactor:reactor-test")
}
```

#### application.properties
```properties
# R2DBC 설정
spring.r2dbc.url=r2dbc:postgresql://localhost:5432/mydb
spring.r2dbc.username=postgres
spring.r2dbc.password=password
spring.r2dbc.pool.initial-size=5
spring.r2dbc.pool.max-size=20
```

### R2DBC 엔티티 및 리포지토리

#### 엔티티 예제
```kotlin
@Table("users")
data class User(
    @Id
    val id: Long? = null,
    
    val name: String,
    
    val email: String,
    
    val role: String = "USER",
    
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Table("posts")
data class Post(
    @Id
    val id: Long? = null,
    
    val title: String,
    
    val content: String,
    
    @Column("user_id")
    val userId: Long,
    
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

#### 리포지토리 인터페이스
```kotlin
interface UserRepository : ReactiveCrudRepository<User, Long> {
    fun findByEmail(email: String): Mono<User>
    
    @Query("SELECT * FROM users WHERE role = :role")
    fun findAllByRole(role: String): Flux<User>
}

interface PostRepository : ReactiveCrudRepository<Post, Long> {
    fun findByUserId(userId: Long): Flux<Post>
    
    @Query("""
        SELECT * FROM posts
        WHERE user_id = :userId
        ORDER BY created_at DESC
    """)
    fun findPostsByUserId(userId: Long): Flux<Post>
}
```

### R2DBC 서비스 및 컨트롤러

#### 서비스 예제
```kotlin
@Service
class UserService(private val userRepository: UserRepository) {
    
    fun getAllUsers(): Flux<User> = userRepository.findAll()
    
    fun getUserById(id: Long): Mono<User> = userRepository.findById(id)
        .switchIfEmpty(Mono.error(NoSuchElementException("User not found with id: $id")))
    
    fun createUser(user: User): Mono<User> = userRepository.save(user)
    
    fun updateUser(id: Long, user: User): Mono<User> {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(NoSuchElementException("User not found with id: $id")))
            .flatMap { existingUser ->
                userRepository.save(user.copy(id = existingUser.id))
            }
    }
    
    fun deleteUser(id: Long): Mono<Void> = userRepository.deleteById(id)
}
```

#### 컨트롤러 예제
```kotlin
@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {
    
    @GetMapping
    fun getAllUsers(): Flux<User> = userService.getAllUsers()
    
    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): Mono<User> = userService.getUserById(id)
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody user: User): Mono<User> = userService.createUser(user)
    
    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: Long, @RequestBody user: User): Mono<User> =
        userService.updateUser(id, user)
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable id: Long): Mono<Void> = userService.deleteUser(id)
    
    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFoundException(ex: NoSuchElementException): Mono<Map<String, String>> =
        Mono.just(mapOf("error" to (ex.message ?: "Not found")))
}
```

### R2DBC와 네이티브 이미지 통합 시 고려사항

1. **드라이버 초기화**:
   ```kotlin
   graalvmNative {
       binaries {
           named("main") {
               buildArgs.add("--initialize-at-run-time=io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider")
           }
       }
   }
   ```

2. **리액티브 스트림 처리**:
   - Project Reactor 라이브러리는 네이티브 이미지 지원
   - Reactive Streams 구현체는 추가 설정 없이 사용 가능

3. **리플렉션 및 프록시 설정**:
   ```kotlin
   @Bean
   fun r2dbcHints(): RuntimeHintsRegistrar {
       return RuntimeHintsRegistrar { hints, _ ->
           hints.reflection()
               .registerType(User::class.java, MemberCategory.values().toList())
               .registerType(Post::class.java, MemberCategory.values().toList())
           
           hints.proxies()
               .registerJdkProxy(UserRepository::class.java, ReactiveCrudRepository::class.java)
               .registerJdkProxy(PostRepository::class.java, ReactiveCrudRepository::class.java)
       }
   }
   ```

## 4. 트랜잭션 관리

### 트랜잭션 관리자 구성

Spring Boot는 사용 중인 데이터 액세스 기술에 맞는 트랜잭션 관리자를 자동으로 구성합니다:

- JPA: `JpaTransactionManager`
- JDBC: `DataSourceTransactionManager`
- R2DBC: `R2dbcTransactionManager`

네이티브 이미지에서 트랜잭션 관리자는 추가 설정 없이 정상 동작합니다.

### 트랜잭션 선언 방식

#### JPA 트랜잭션 선언
```kotlin
@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository
) : UserService {
    
    @Transactional(readOnly = true)
    override fun getUsers(): List<User> = userRepository.findAll()
    
    @Transactional
    override fun createUserWithPosts(user: User, postTitles: List<String>): User {
        val savedUser = userRepository.save(user)
        
        postTitles.forEach { title ->
            val post = Post(
                title = title,
                content = "Content for $title",
                user = savedUser
            )
            savedUser.posts.add(post)
        }
        
        return userRepository.save(savedUser)
    }
    
    @Transactional
    override fun transferUserRole(fromUserId: Long, toUserId: Long): Pair<User, User> {
        val fromUser = userRepository.findById(fromUserId)
            .orElseThrow { NoSuchElementException("User not found with id: $fromUserId") }
        
        val toUser = userRepository.findById(toUserId)
            .orElseThrow { NoSuchElementException("User not found with id: $toUserId") }
        
        val fromRole = fromUser.role
        
        // 역할 교환
        val updatedFromUser = userRepository.save(fromUser.copy(role = toUser.role))
        val updatedToUser = userRepository.save(toUser.copy(role = fromRole))
        
        return Pair(updatedFromUser, updatedToUser)
    }
}
```

#### R2DBC 트랜잭션 선언
```kotlin
@Service
class UserServiceReactive(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository
) {
    
    @Transactional(readOnly = true)
    fun getAllUsers(): Flux<User> = userRepository.findAll()
    
    @Transactional
    fun createUserWithPosts(user: User, postTitles: List<String>): Mono<User> {
        return userRepository.save(user)
            .flatMap { savedUser ->
                val posts = postTitles.map { title ->
                    Post(
                        title = title,
                        content = "Content for $title",
                        userId = savedUser.id!!
                    )
                }
                
                Flux.fromIterable(posts)
                    .flatMap { post -> postRepository.save(post) }
                    .then(Mono.just(savedUser))
            }
    }
    
    @Transactional
    fun transferUserRole(fromUserId: Long, toUserId: Long): Mono<Pair<User, User>> {
        return Mono.zip(
            userRepository.findById(fromUserId),
            userRepository.findById(toUserId)
        ).switchIfEmpty(Mono.error(NoSuchElementException("One or both users not found")))
        .flatMap { tuple ->
            val fromUser = tuple.t1
            val toUser = tuple.t2
            val fromRole = fromUser.role
            
            // 역할 교환
            Mono.zip(
                userRepository.save(fromUser.copy(role = toUser.role)),
                userRepository.save(toUser.copy(role = fromRole))
            ).map { resultTuple -> Pair(resultTuple.t1, resultTuple.t2) }
        }
    }
}
```

### 프로그래밍 방식 트랜잭션 관리

#### JPA 프로그래밍 방식 트랜잭션
```kotlin
@Service
class ManualTransactionService(
    private val transactionManager: PlatformTransactionManager,
    private val userRepository: UserRepository
) {
    
    fun executeInTransaction(operation: () -> User): User {
        val definition = DefaultTransactionDefinition().apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
            timeout = 30 // 30초 타임아웃
        }
        
        val transaction = transactionManager.getTransaction(definition)
        
        return try {
            val result = operation()
            transactionManager.commit(transaction)
            result
        } catch (e: Exception) {
            transactionManager.rollback(transaction)
            throw e
        }
    }
    
    fun createUsers(users: List<User>): List<User> {
        return executeInTransaction {
            val savedUsers = mutableListOf<User>()
            
            for (user in users) {
                savedUsers.add(userRepository.save(user))
            }
            
            // 검증 로직
            if (savedUsers.any { it.email.isBlank() }) {
                throw IllegalStateException("이메일이 없는 사용자가 있습니다")
            }
            
            savedUsers.last() // 트랜잭션 메소드의 결과
        } as User
        
        return savedUsers
    }
}
```

#### R2DBC 프로그래밍 방식 트랜잭션
```kotlin
@Service
class ReactiveTransactionService(
    private val databaseClient: DatabaseClient
) {
    
    fun executeInTransaction(operation: () -> Mono<User>): Mono<User> {
        return TransactionalOperator.create(ReactiveTransactionManager.create(databaseClient))
            .transactional(operation())
    }
    
    fun createUsers(users: List<User>): Mono<List<User>> {
        return executeInTransaction {
            Flux.fromIterable(users)
                .flatMap { user -> databaseClient.insert().into(User::class.java).using(user).fetch().rowsUpdated().thenReturn(user) }
                .collectList()
                .flatMap { savedUsers ->
                    // 검증 로직
                    if (savedUsers.any { it.email.isBlank() }) {
                        return@flatMap Mono.error<List<User>>(IllegalStateException("이메일이 없는 사용자가 있습니다"))
                    }
                    Mono.just(savedUsers)
                }
        }.map { it as List<User> }
    }
}
```

### 네이티브 이미지에서 트랜잭션 관리 시 고려사항

1. **트랜잭션 프록시**:
   - `@Transactional`은 프록시 기반으로 동작
   - 네이티브 이미지에서는 AOP 설정 필요
   
   ```kotlin
   @Bean
   fun transactionHints(): RuntimeHintsRegistrar {
       return RuntimeHintsRegistrar { hints, _ ->
           // 트랜잭션 관리자 관련 프록시 설정
           hints.proxies()
               .registerJdkProxy(
                   UserService::class.java,
                   TransactionalProxy::class.java,
                   SpringProxy::class.java,
                   Advised::class.java
               )
       }
   }
   ```

2. **실패 시 롤백 메커니즘**:
   - 네이티브 이미지에서 트랜잭션 롤백 작동 확인
   - 예외 처리 및 롤백 로직 테스트

3. **트랜잭션 테스트**:
   - 네이티브 이미지 빌드 전 트랜잭션 테스트 케이스 작성
   - 다양한 시나리오에서 트랜잭션 동작 검증

## 5. 실습: JPA 기반 블로그 애플리케이션 개발

### 도메인 모델

```kotlin
// User.kt
@Entity
@Table(name = "users")
data class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(nullable = false, unique = true)
    val email: String,
    
    @Column(nullable = false)
    val password: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole = UserRole.USER,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class UserRole {
    USER, ADMIN
}

// Post.kt
@Entity
@Table(name = "posts")
data class Post(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false)
    val title: String,
    
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val author: User,
    
    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    val comments: MutableList<Comment> = mutableListOf(),
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

// Comment.kt
@Entity
@Table(name = "comments")
data class Comment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val author: User,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

### 리포지토리 계층

```kotlin
// UserRepository.kt
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
}

// PostRepository.kt
@Repository
interface PostRepository : JpaRepository<Post, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<Post>
    
    @Query("SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.createdAt DESC")
    fun findAllWithAuthor(): List<Post>
    
    fun findByAuthor(author: User): List<Post>
}

// CommentRepository.kt
@Repository
interface CommentRepository : JpaRepository<Comment, Long> {
    fun findByPost(post: Post): List<Comment>
    
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post = :post ORDER BY c.createdAt ASC")
    fun findByPostWithAuthor(@Param("post") post: Post): List<Comment>
}
```

### 서비스 계층

```kotlin
// UserService.kt
@Service
class UserService(private val userRepository: UserRepository) {
    
    @Transactional(readOnly = true)
    fun getAllUsers(): List<User> = userRepository.findAll()
    
    @Transactional(readOnly = true)
    fun getUserById(id: Long): User = userRepository.findById(id)
        .orElseThrow { NoSuchElementException("User not found with id: $id") }
    
    @Transactional
    fun createUser(user: User): User = userRepository.save(user)
    
    @Transactional
    fun updateUser(id: Long, user: User): User {
        val existingUser = getUserById(id)
        return userRepository.save(user.copy(id = existingUser.id))
    }
    
    @Transactional
    fun deleteUser(id: Long) = userRepository.deleteById(id)
}
