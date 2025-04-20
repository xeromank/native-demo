# 3주차: 데이터베이스 연동 및 JPA 활용

## 목차
1. [네이티브 이미지와 데이터베이스 연결](#1-네이티브-이미지와-데이터베이스-연결)
2. [JPA와 네이티브 이미지 통합](#2-jpa와-네이티브-이미지-통합)
3. [트랜잭션 관리](#3-트랜잭션-관리)
4. [실습: JPA 기반 블로그 애플리케이션 개발](#4-실습-jpa-기반-블로그-애플리케이션-개발)
5. [네이티브 이미지 빌드 및 배포](#5-네이티브-이미지-빌드-및-배포)
6. [성능 테스트 및 비교](#6-성능-테스트-및-비교)
7. [과제](#7-과제)
8. [참고 자료](#8-참고-자료)

## 학습 목표
- 네이티브 이미지에서 데이터베이스 연결 구성 방법 이해
- JPA/Hibernate와 네이티브 이미지 통합 시 고려사항 학습
- 트랜잭션 관리 및 최적화 방법 습득
- 실제 데이터베이스 연동 애플리케이션 개발 및 네이티브 이미지 변환

## 1. 네이티브 이미지와 데이터베이스 연결

### 데이터베이스 드라이버 지원 현황

네이티브 이미지에서 사용 가능한 주요 데이터베이스 드라이버:

| 데이터베이스 | JDBC 드라이버 | 네이티브 이미지 지원 | 고려사항 |
|------------|--------------|-------------------|---------|
| H2 | `h2` | ✅ 완전 지원 | 메모리 모드 권장 |
| MySQL | `mysql-connector-j` | ✅ 완전 지원 | 최신 드라이버(8.x) 사용 권장, SSL 사용 시 추가 설정 필요 |
| MariaDB | `mariadb-java-client` | ✅ 완전 지원 | 최신 버전 권장 |
| PostgreSQL | `postgresql` | ✅ 완전 지원 | 추가 설정 필요 없음 |
| Oracle | `ojdbc11` | ✅ 지원 | JDK 21 호환성 확인, 연결 풀 설정 주의 |
| SQL Server | `mssql-jdbc` | ✅ 지원 | 인증 방식에 따라 추가 설정 필요 |
| SQLite | `sqlite-jdbc` | ⚠️ 부분 지원 | 네이티브 라이브러리 이슈 주의 |

### JDBC 연결 설정 예제 (MySQL)

#### application.properties
```properties
# MySQL 설정
spring.datasource.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# 연결 풀 설정 (HikariCP)
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.connection-timeout=20000

# JPA 설정
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.show-sql=true
```

### 커스텀 DataSource 구성 (Kotlin)

```kotlin
@Configuration
class DataSourceConfig {

    @Bean
    fun dataSource(
        @Value("\${spring.datasource.url}") url: String,
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
   // build.gradle.kts
   graalvmNative {
       binaries {
           named("main") {
               buildArgs.add("--initialize-at-run-time=com.mysql.cj.jdbc.Driver,com.mysql.cj.jdbc.NonRegisteringDriver")
           }
       }
   }
   ```

2. **드라이버 등록 방식**:
   - `DriverManager.getConnection()` 보다 `DataSource` 구현체 사용 권장
   - 일부 드라이버는 `ServiceLoader` 방식으로 자동 등록

3. **JNI 및 네이티브 라이브러리**:
   - MySQL 드라이버와 같은 일부 드라이버는 JNI 나 네이티브 라이브러리 사용
   - 네이티브 이미지에 포함되도록 추가 설정 필요

4. **JDK 21 호환성**:
   - JDK 21 호환성이 확인된 최신 드라이버 사용
   - MySQL의 경우 `mysql-connector-j` 8.x 버전 권장

## 2. JPA와 네이티브 이미지 통합

### JPA/Hibernate 호환성

Hibernate 6.x는 네이티브 이미지를 공식적으로 지원하며, Spring Boot 3.2+와 JDK 21 환경에서 대부분의 JPA 기능이 정상 동작합니다.

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

# MySQL 최적화 설정
spring.datasource.hikari.data-source-properties.cachePrepStmts=true
spring.datasource.hikari.data-source-properties.prepStmtCacheSize=250
spring.datasource.hikari.data-source-properties.prepStmtCacheSqlLimit=2048
spring.datasource.hikari.data-source-properties.useServerPrepStmts=true
spring.datasource.hikari.data-source-properties.useLocalSessionState=true
spring.datasource.hikari.data-source-properties.rewriteBatchedStatements=true
spring.datasource.hikari.data-source-properties.cacheResultSetMetadata=true
spring.datasource.hikari.data-source-properties.cacheServerConfiguration=true
spring.datasource.hikari.data-source-properties.elideSetAutoCommits=true
spring.datasource.hikari.data-source-properties.maintainTimeStats=false
```

### 네이티브 이미지를 위한 JPA 힌트 설정

Spring Boot 3.2+는 자동 AOT 프로세싱을 통해 JPA 관련 힌트를 대부분 자동으로 설정하지만, 커스텀 엔티티나 특별한 JPA 기능을 사용하는 경우 추가 힌트가 필요할 수 있습니다:

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
   - **Lazy 로딩 사용 시 프록시 설정 필요**
   - `@Transactional` 범위에서 Lazy 로딩 처리 권장
     - `이건 어디서나 마찬가지 아닌가?`

2. **프로젝션과 DTO**:
   - 인터페이스 기반 프로젝션 사용 시 힌트 등록 필요
   - 클래스 기반 DTO 사용이 더 안정적
   - JPQL에서 `new` 키워드로 생성자 호출 방식 권장
   
   ```kotlin
   @Query("SELECT new com.example.dto.UserSummaryDto(u.id, u.name, u.email) FROM User u")
   fun findAllUserSummaries(): List<UserSummaryDto>
   ```

3. **동적 쿼리 및 Criteria API**:
   - Criteria API는 프록시와 리플렉션 사용
   - 네이티브 이미지에서 사용 시 추가 설정 필요
   - 가능하면 정적 JPQL 쿼리 사용 권장

4. **네이티브 SQL**:
   - 네이티브 SQL은 문제 없이 동작
   - 결과 매핑에 사용되는 클래스는 힌트 등록 필요
     - `이럴거면 그냥 jdbcTemplate으로 쿼리 쓰는게 가장 효과적일지도`
   
   ```kotlin
   @Query(
       value = "SELECT * FROM users WHERE role = :role", 
       nativeQuery = true
   )
   fun findUsersByRoleNative(@Param("role") role: String): List<User>
   ```

5. **JDK 21 관련 이슈**:
   - JDK 21의 Virtual Thread 사용 시 주의 필요
   - Hibernate 6.4+ 버전 권장
   - Hibernates 패키지 구조 변경에 따른 `--initialize-at-run-time` 설정 확인

## 3. 트랜잭션 관리

### 트랜잭션 관리자 구성

Spring Boot는 사용 중인 데이터 액세스 기술에 맞는 트랜잭션 관리자를 자동으로 구성합니다:

- JPA: `JpaTransactionManager`
- JDBC: `DataSourceTransactionManager`

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
        
        val status = transactionManager.getTransaction(definition)
        
        return try {
            val result = operation()
            transactionManager.commit(status)
            result
        } catch (e: Exception) {
            transactionManager.rollback(status)
            throw e
        }
    }
    
    fun createUsers(users: List<User>): List<User> {
        val savedUsers = mutableListOf<User>()
        
        executeInTransaction {
            for (user in users) {
                savedUsers.add(userRepository.save(user))
            }
            
            // 검증 로직
            if (savedUsers.any { it.email.isBlank() }) {
                throw IllegalStateException("이메일이 없는 사용자가 있습니다")
            }
            
            savedUsers.last() // 트랜잭션 메소드의 결과
        }
        
        return savedUsers
    }
}
```

### JDK 21에서의 트랜잭션 최적화

JDK 21의 Virtual Thread 기능을 활용한 트랜잭션 최적화:

```kotlin
@Service
class VirtualThreadTransactionService(
    private val userRepository: UserRepository
) {
    
    @Transactional(readOnly = true)
    fun processUsersInParallel(userIds: List<Long>): List<User> = userIds
        .map { userId ->
            // Virtual Thread 사용
            Thread.startVirtualThread {
                // 각 Virtual Thread에서 읽기 트랜잭션 실행
                findUserWithValidation(userId)
            }
        }
        .map { it.join() as User }
        .toList()
    
    @Transactional(readOnly = true)
    fun findUserWithValidation(userId: Long): User {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found: $userId") }
        
        // 비즈니스 검증 로직
        if (user.posts.isEmpty()) {
            logger.info("User $userId has no posts")
        }
        
        return user
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(VirtualThreadTransactionService::class.java)
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

4. **Virtual Thread와 트랜잭션**:
   - JDK 21 Virtual Thread 사용 시 트랜잭션 분리 고려
   - 병렬 트랜잭션 처리 최적화

## 4. 실습: JPA 기반 블로그 애플리케이션 개발

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
}
```

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

## 5. 네이티브 이미지 빌드 및 배포

### 네이티브 이미지 빌드 설정 (JPA 애플리케이션)

#### build.gradle.kts
```kotlin
plugins {
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.graalvm.buildtools.native") version "0.10.0"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("plugin.jpa") version "1.9.22"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
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
    testImplementation("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

// 네이티브 이미지 설정
graalvmNative {
    binaries {
        named("main") {
            imageName.set("blog-api")
            buildArgs.add("--verbose")
            buildArgs.add("--no-fallback")
            buildArgs.add("--initialize-at-run-time=com.mysql.cj.jdbc.Driver,com.mysql.cj.jdbc.NonRegisteringDriver")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}
```

### Docker를 이용한 빌드 및 배포

#### Dockerfile
```dockerfile
FROM ghcr.io/graalvm/native-image:ol9-java21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew nativeCompile

FROM oraclelinux:9-slim
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/blog-api .
EXPOSE 8080
ENTRYPOINT ["./blog-api"]
```

#### docker-compose.yml
```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.2
    container_name: mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_DATABASE: blogdb
      MYSQL_USER: bloguser
      MYSQL_PASSWORD: blogpass
      MYSQL_ROOT_PASSWORD: rootpass
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p$MYSQL_ROOT_PASSWORD"]
      interval: 10s
      timeout: 5s
      retries: 5

  blog-api:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: blog-api
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/blogdb?useSSL=false&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: bloguser
      SPRING_DATASOURCE_PASSWORD: blogpass
    depends_on:
      mysql:
        condition: service_healthy

volumes:
  mysql-data:
```

## 6. 성능 테스트 및 비교

### 성능 테스트 설정

JPA 애플리케이션의, JVM 모드와 네이티브 이미지 모드의 성능을 비교하기 위한 테스트 방법:

1. **기본 설정**:
   - 동일한 하드웨어 환경에서 테스트
   - 동일한 데이터셋 사용
   - 테스트 전 워밍업 수행

2. **측정 지표**:
   - 시작 시간 (Startup Time)
   - 메모리 사용량 (Memory Footprint)
   - 처리량 (Throughput)
   - 응답 시간 (Response Time)
   - CPU 사용률

3. **테스트 도구**:
   - Apache JMeter
   - Spring Boot Actuator
   - Prometheus + Grafana

### JMeter 테스트 스크립트 예제

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Blog API Test Plan">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Blog API Users">
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <intProp name="LoopController.loops">100</intProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">50</stringProp>
        <stringProp name="ThreadGroup.ramp_time">10</stringProp>
        <longProp name="ThreadGroup.start_time">1709572800000</longProp>
        <longProp name="ThreadGroup.end_time">1709572800000</longProp>
        <boolProp name="ThreadGroup.scheduler">false</boolProp>
        <stringProp name="ThreadGroup.duration"></stringProp>
        <stringProp name="ThreadGroup.delay"></stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Get All Posts">
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/api/posts</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
          <boolProp name="HTTPSampler.BROWSER_COMPATIBLE_MULTIPART">false</boolProp>
          <boolProp name="HTTPSampler.image_parser">false</boolProp>
          <boolProp name="HTTPSampler.concurrentDwn">false</boolProp>
          <stringProp name="HTTPSampler.concurrentPool">6</stringProp>
          <boolProp name="HTTPSampler.md5">false</boolProp>
          <intProp name="HTTPSampler.ipSourceType">0</intProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary Report">
          <boolProp name="ResultCollector.error_logging">false</boolProp>
          <objProp>
            <name>saveConfig</name>
            <value class="SampleSaveConfiguration">
              <time>true</time>
              <latency>true</latency>
              <timestamp>true</timestamp>
              <success>true</success>
              <label>true</label>
              <code>true</code>
              <message>true</message>
              <threadName>true</threadName>
              <dataType>true</dataType>
              <encoding>false</encoding>
              <assertions>true</assertions>
              <subresults>true</subresults>
              <responseData>false</responseData>
              <samplerData>false</samplerData>
              <xml>false</xml>
              <fieldNames>true</fieldNames>
              <responseHeaders>false</responseHeaders>
              <requestHeaders>false</requestHeaders>
              <responseDataOnError>false</responseDataOnError>
              <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
              <assertionsResultsToSave>0</assertionsResultsToSave>
              <bytes>true</bytes>
              <sentBytes>true</sentBytes>
              <url>true</url>
              <threadCounts>true</threadCounts>
              <idleTime>true</idleTime>
              <connectTime>true</connectTime>
            </value>
          </objProp>
          <stringProp name="filename"></stringProp>
        </ResultCollector>
        <hashTree/>
        <ResultCollector guiclass="GraphVisualizer" testclass="ResultCollector" testname="Graph Results">
          <boolProp name="ResultCollector.error_logging">false</boolProp>
          <objProp>
            <name>saveConfig</name>
            <value class="SampleSaveConfiguration">
              <time>true</time>
              <latency>true</latency>
              <timestamp>true</timestamp>
              <success>true</success>
              <label>true</label>
              <code>true</code>
              <message>true</message>
              <threadName>true</threadName>
              <dataType>true</dataType>
              <encoding>false</encoding>
              <assertions>true</assertions>
              <subresults>true</subresults>
              <responseData>false</responseData>
              <samplerData>false</samplerData>
              <xml>false</xml>
              <fieldNames>true</fieldNames>
              <responseHeaders>false</responseHeaders>
              <requestHeaders>false</requestHeaders>
              <responseDataOnError>false</responseDataOnError>
              <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
              <assertionsResultsToSave>0</assertionsResultsToSave>
              <bytes>true</bytes>
              <sentBytes>true</sentBytes>
              <url>true</url>
              <threadCounts>true</threadCounts>
              <idleTime>true</idleTime>
              <connectTime>true</connectTime>
            </value>
          </objProp>
          <stringProp name="filename"></stringProp>
        </ResultCollector>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### Spring Boot Actuator 설정

#### build.gradle.kts에 의존성 추가
```kotlin
dependencies {
    // 기존 의존성
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
```

#### application.properties 설정
```properties
# Actuator 설정
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
management.metrics.tags.application=${spring.application.name}
```

### 성능 비교 결과 예시
- ./gradlew clean nativeCompile  
  - 5m 58s
- ./gradlew bootBuildImage 
  - 3m 38s

| 측정 지표 | JVM 모드 | 네이티브 이미지 | 개선율 |
|----------|---------|--------------|-------|
| 시작 시간 | 4.5초 | 0.08초 | 98% 향상 |
| 메모리 사용량 (Heap) | 210MB | 45MB | 78% 감소 |
| 최대 TPS (50 사용자) | 420 | 680 | 62% 향상 |
| 평균 응답 시간 | 95ms | 65ms | 32% 감소 |
| CPU 사용률 | 65% | 45% | 31% 감소 |

### JVM과 네이티브 이미지 비교 분석

1. **시작 시간**: 
   - 네이티브 이미지는 JVM 모드보다 약 50-100배 빠르게 시작
   - 컨테이너 환경이나 서버리스 환경에서 큰 이점

2. **메모리 사용량**:
   - 네이티브 이미지는 더 작은 메모리 공간 사용
   - 컨테이너 환경에서 더 많은 인스턴스 실행 가능

3. **처리량과 응답 시간**:
   - 네이티브 이미지는 처리량이 더 높고 응답 시간이 짧음
   - 복잡한 JPA 쿼리에서도 안정적인 성능

4. **가비지 컬렉션**:
   - 네이티브 이미지에서는 가비지 컬렉션 일시 중지가 적음
   - 더 일관된 성능 제공

5. **제한사항**:
   - 네이티브 이미지는 동적 클래스 로딩 및 리플렉션에 제약
   - AOT 컴파일로 인한 빌드 시간 증가

## 7. 과제

### 개인 과제: JPA 기반 API 개발 및 네이티브 이미지 변환

다음 요구사항을 충족하는 JPA 기반 API를 개발하고 네이티브 이미지로 변환하세요:

1. **기본 요구사항**:
   - JDK 21 및 MySQL 8.x 사용
   - Spring Boot 3.2 이상 사용
   - 최소 3개 이상의 연관된 엔티티 설계
   - REST API 엔드포인트 구현

2. **필수 기능**:
   - CRUD 기능 구현
   - 다양한 JPA 관계 사용 (OneToMany, ManyToOne, ManyToMany)
   - 페이징 및 정렬 구현
   - 복잡한 JPQL 쿼리 작성

3. **네이티브 이미지 요구사항**:
   - GraalVM Native Image로 빌드
   - Docker 컨테이너화
   - 성능 테스트 수행 및 결과 기록

4. **제출물**:
   - 소스 코드 (Github 저장소)
   - 네이티브 이미지 빌드 및 실행 방법 문서
   - JVM 모드와 네이티브 이미지 모드의 성능 비교 보고서
   - 개발 과정에서 만난 문제점과 해결 방법

### 팀 과제: 마이크로서비스 설계 및 구현

3-4명의 팀을 구성하여 다음 과제를 수행하세요:

1. **시스템 설계**:
   - 최소 2개 이상의 마이크로서비스로 구성된 시스템 설계
   - 각 서비스는 자체 데이터베이스 보유
   - 서비스 간 통신 방식 정의 (REST, 메시징 등)

2. **구현 요구사항**:
   - 각 서비스는 JPA/Hibernate 사용
   - 트랜잭션 관리 방법 구현
   - 서비스 간 일관성 유지 방법 구현
   - 네이티브 이미지로 빌드 및 컨테이너화

3. **배포 및 테스트**:
   - Docker Compose 또는 Kubernetes로 배포
   - 부하 테스트 수행
   - 성능 모니터링 설정

4. **제출물**:
   - 시스템 설계 문서
   - 소스 코드 및 빌드/배포 스크립트
   - 성능 테스트 결과 및 분석
   - 팀 프레젠테이션 자료

## 8. 참고 자료

### 공식 문서
- [Spring Boot 네이티브 이미지 가이드](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [GraalVM 네이티브 이미지 문서](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Hibernate ORM GraalVM Native Image 지원](https://hibernate.org/orm/documentation/61/native/)
- [Spring Data JPA 문서](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)

### 튜토리얼 및 블로그
- [Spring Boot 3 + GraalVM + JPA 튜토리얼](https://spring.io/blog/2023/02/23/getting-started-with-spring-boot-3-and-spring-framework-6-0)
- [Hibernate와 Spring Native 통합 가이드](https://www.baeldung.com/spring-native-hibernate)
- [네이티브 이미지에서 JPA 성능 최적화](https://www.infoq.com/articles/native-java-spring-boot/)
- [MySQL과 Spring Boot 네이티브 이미지 통합 가이드](https://vladmihalcea.com/spring-native-with-mysql-jpa/)

### 도구 및 라이브러리
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer](https://micrometer.io/)
- [JMeter](https://jmeter.apache.org/)
- [Docker](https://www.docker.com/)
- [Prometheus](https://prometheus.io/)
- [Grafana](https://grafana.com/)
