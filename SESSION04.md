# 4주차: 테스트 전략 및 디버깅

## 학습 목표
- 네이티브 이미지 애플리케이션의 테스트 전략 이해
- 단위 테스트, 통합 테스트, 컨트랙트 테스트 작성 방법 습득
- 네이티브 이미지 디버깅 기법 학습
- CI/CD 파이프라인 구성 및 최적화 방법 이해
- 효과적인 테스트 자동화 구현

## 1. 네이티브 이미지 애플리케이션 테스트 전략

### 테스트 피라미드 재고찰

전통적인 테스트 피라미드는 다음과 같은 계층으로 구성됩니다:

```
    /\
   /  \
  /    \
 / E2E  \
/--------\
/ 통합 테스트 \
/------------\
/   단위 테스트  \
```

네이티브 이미지 애플리케이션의 특성을 고려한 테스트 전략은 다음과 같이 조정됩니다:

1. **JVM 모드 테스트 (개발 주기)**
   - 단위 테스트: 빠른 피드백을 위해 JVM 모드에서 실행
   - 통합 테스트: 대부분의 통합 테스트는 JVM 모드에서 실행
   - Mock 테스트: 외부 의존성 모킹을 통해 격리된 테스트 수행

2. **네이티브 이미지 테스트 (배포 전)**
   - 기능 테스트: 핵심 기능에 대한 테스트를 네이티브 이미지에서 실행
   - 성능 테스트: 네이티브 이미지의 성능 특성 검증
   - 메모리 사용량 테스트: 메모리 사용 패턴 검증

3. **프로덕션 환경 유사 테스트**
   - 컨테이너 기반 테스트: Docker 컨테이너 내에서 애플리케이션 테스트
   - 통합 환경 테스트: 실제 서비스와의 통합 검증

### 네이티브 이미지 테스트 특성

네이티브 이미지 애플리케이션을 테스트할 때 고려해야 할 주요 특성:

1. **빌드 시간**
   - 네이티브 이미지 빌드는 JVM 애플리케이션보다 오래 걸림
   - 모든 테스트를 네이티브 이미지로 실행하는 것은 비효율적

2. **런타임 동작 차이**
   - 리플렉션, 프록시 등의 동작이 JVM 모드와 다를 수 있음
   - 일부 테스트는 반드시 네이티브 이미지에서 실행해야 함

3. **리소스 초기화**
   - 클래스 로딩 및 초기화 시점이 다름
   - 초기화 관련 문제는 네이티브 이미지에서만 발견될 수 있음

4. **메모리 관리**
   - 메모리 사용 패턴이 JVM과 다름
   - 메모리 누수 및 사용량 테스트는 네이티브 이미지에서 수행해야 함

## 2. 단위 테스트 작성

### 단위 테스트의 원칙

네이티브 이미지 애플리케이션의 단위 테스트 원칙:

1. **빠른 피드백**: 단위 테스트는 JVM 모드에서 실행하여 빠른 피드백 제공
2. **테스트 격리**: 외부 의존성을 모킹하여 테스트 격리 유지
3. **코드 커버리지**: 핵심 비즈니스 로직에 높은 커버리지 확보
4. **경계 조건 테스트**: 네이티브 이미지에서 문제될 수 있는 경계 조건 집중 테스트

### JUnit 5와 Mockito를 활용한 단위 테스트

```kotlin
@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    
    @Mock
    private lateinit var userRepository: UserRepository
    
    @InjectMocks
    private lateinit var userService: UserServiceImpl
    
    @Test
    fun `getUserById should return user when user exists`() {
        // given
        val userId = 1L
        val expectedUser = User(id = userId, name = "Test User", email = "test@example.com")
        given(userRepository.findById(userId)).willReturn(Optional.of(expectedUser))
        
        // when
        val actualUser = userService.getUserById(userId)
        
        // then
        assertEquals(expectedUser, actualUser)
        verify(userRepository).findById(userId)
    }
    
    @Test
    fun `getUserById should throw exception when user does not exist`() {
        // given
        val userId = 1L
        given(userRepository.findById(userId)).willReturn(Optional.empty())
        
        // when/then
        assertThrows<NoSuchElementException> {
            userService.getUserById(userId)
        }
        verify(userRepository).findById(userId)
    }
    
    @Test
    fun `createUser should save and return user`() {
        // given
        val newUser = User(name = "New User", email = "new@example.com")
        val savedUser = User(id = 1L, name = "New User", email = "new@example.com")
        given(userRepository.save(newUser)).willReturn(savedUser)
        
        // when
        val result = userService.createUser(newUser)
        
        // then
        assertEquals(savedUser, result)
        verify(userRepository).save(newUser)
    }
}
```

### Kotest를 활용한 BDD 스타일 테스트

```kotlin
class UserServiceSpec : FunSpec({
    val userRepository = mockk<UserRepository>()
    val userService = UserServiceImpl(userRepository)
    
    test("getUserById should return user when user exists") {
        // given
        val userId = 1L
        val expectedUser = User(id = userId, name = "Test User", email = "test@example.com")
        every { userRepository.findById(userId) } returns Optional.of(expectedUser)
        
        // when
        val actualUser = userService.getUserById(userId)
        
        // then
        actualUser shouldBe expectedUser
        verify { userRepository.findById(userId) }
    }
    
    test("getUserById should throw exception when user does not exist") {
        // given
        val userId = 1L
        every { userRepository.findById(userId) } returns Optional.empty()
        
        // when/then
        val exception = shouldThrow<NoSuchElementException> {
            userService.getUserById(userId)
        }
        exception.message shouldContain "User not found"
        verify { userRepository.findById(userId) }
    }
})
```

### 생성자 주입을 활용한 테스트 용이성 확보

```kotlin
// 테스트하기 쉬운 서비스 구현
@Service
class PostServiceImpl(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val clock: Clock // 시간 관련 로직을 테스트하기 쉽게 함
) : PostService {
    
    override fun createPost(post: Post): Post {
        val currentTime = LocalDateTime.now(clock)
        return postRepository.save(post.copy(createdAt = currentTime, updatedAt = currentTime))
    }
    
    // 나머지 구현...
}

// 테스트 코드
@Test
fun `createPost should set creation time using provided clock`() {
    // given
    val fixedClock = Clock.fixed(
        Instant.parse("2023-01-01T10:00:00Z"),
        ZoneId.of("UTC")
    )
    val postRepository = mock<PostRepository>()
    val userRepository = mock<UserRepository>()
    val postService = PostServiceImpl(postRepository, userRepository, fixedClock)
    
    val post = Post(title = "Test", content = "Content", author = User(id = 1L))
    val expected = post.copy(
        createdAt = LocalDateTime.now(fixedClock),
        updatedAt = LocalDateTime.now(fixedClock)
    )
    given(postRepository.save(any())).willReturn(expected)
    
    // when
    val result = postService.createPost(post)
    
    // then
    verify(postRepository).save(argThat { 
        createdAt == LocalDateTime.now(fixedClock) && 
        updatedAt == LocalDateTime.now(fixedClock) 
    })
    assertEquals(expected, result)
}
```

## 3. 통합 테스트 작성

### Spring Boot 통합 테스트

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {
    
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
    }
    
    @Test
    fun `should get user by id`() {
        // given
        val user = userRepository.save(User(name = "Test User", email = "test@example.com"))
        
        // when/then
        mockMvc.perform(get("/api/users/${user.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(user.id))
            .andExpect(jsonPath("$.name").value(user.name))
            .andExpect(jsonPath("$.email").value(user.email))
    }
    
    @Test
    fun `should return 404 when user not found`() {
        // when/then
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound)
    }
    
    @Test
    fun `should create new user`() {
        // given
        val userJson = """
            {
                "name": "New User",
                "email": "new@example.com",
                "password": "password123"
            }
        """.trimIndent()
        
        // when/then
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("New User"))
            .andExpect(jsonPath("$.email").value("new@example.com"))
            
        // verify in database
        val users = userRepository.findAll()
        assertEquals(1, users.size)
        assertEquals("New User", users[0].name)
    }
}
```

### 테스트 컨테이너를 활용한 통합 테스트

```kotlin
@SpringBootTest
@Testcontainers
class DatabaseIntegrationTest {
    
    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:14-alpine").apply {
            withDatabaseName("testdb")
            withUsername("testuser")
            withPassword("testpass")
        }
        
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
        }
    }
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Test
    fun `should save and retrieve user from database`() {
        // given
        val user = User(name = "DB Test User", email = "dbtest@example.com", password = "pass")
        
        // when
        val savedUser = userRepository.save(user)
        val retrievedUser = userRepository.findById(savedUser.id!!).orElse(null)
        
        // then
        assertNotNull(retrievedUser)
        assertEquals(savedUser.name, retrievedUser.name)
        assertEquals(savedUser.email, retrievedUser.email)
    }
}
```

### WebTestClient를 활용한 리액티브 애플리케이션 테스트

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerReactiveTest {
    
    @Autowired
    private lateinit var webTestClient: WebTestClient
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @BeforeEach
    fun setup() {
        userRepository.deleteAll().block()
    }
    
    @Test
    fun `should get user by id`() {
        // given
        val user = userRepository.save(User(name = "Test User", email = "test@example.com")).block()!!
        
        // when/then
        webTestClient.get()
            .uri("/api/users/${user.id}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(user.id)
            .jsonPath("$.name").isEqualTo(user.name)
            .jsonPath("$.email").isEqualTo(user.email)
    }
    
    @Test
    fun `should create new user`() {
        // given
        val newUser = User(name = "New User", email = "new@example.com", password = "password123")
        
        // when/then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(newUser)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo(newUser.name)
            .jsonPath("$.email").isEqualTo(newUser.email)
            
        // verify in database
        val count = userRepository.count().block()
        assertEquals(1, count)
    }
}
```

### 슬라이스 테스트 (Repository, Controller)

#### Repository 슬라이스 테스트

```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {
    
    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:14-alpine").apply {
            withDatabaseName("testdb")
            withUsername("testuser")
            withPassword("testpass")
        }
        
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
        }
    }
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Test
    fun `should find user by email`() {
        // given
        val email = "test@example.com"
        val user = User(name = "Test User", email = email, password = "password")
        userRepository.save(user)
        
        // when
        val foundUser = userRepository.findByEmail(email)
        
        // then
        assertTrue(foundUser.isPresent)
        assertEquals(email, foundUser.get().email)
    }
    
    @Test
    fun `should find all users with role`() {
        // given
        userRepository.save(User(name = "User 1", email = "user1@example.com", role = UserRole.USER))
        userRepository.save(User(name = "User 2", email = "user2@example.com", role = UserRole.USER))
        userRepository.save(User(name = "Admin", email = "admin@example.com", role = UserRole.ADMIN))
        
        // when
        val users = userRepository.findAllByRole(UserRole.USER)
        
        // then
        assertEquals(2, users.size)
        assertTrue(users.all { it.role == UserRole.USER })
    }
}
```

#### Controller 슬라이스 테스트

```kotlin
@WebMvcTest(UserController::class)
class UserControllerTest {
    
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @MockBean
    private lateinit var userService: UserService
    
    @Test
    fun `should get user by id`() {
        // given
        val userId = 1L
        val user = User(id = userId, name = "Test User", email = "test@example.com")
        given(userService.getUserById(userId)).willReturn(user)
        
        // when/then
        mockMvc.perform(get("/api/users/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.name").value(user.name))
            .andExpect(jsonPath("$.email").value(user.email))
    }
    
    @Test
    fun `should return 404 when user not found`() {
        // given
        val userId = 999L
        given(userService.getUserById(userId)).willThrow(NoSuchElementException("User not found"))
        
        // when/then
        mockMvc.perform(get("/api/users/$userId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("User not found"))
    }
}
```

## 4. 네이티브 이미지 테스트

### 네이티브 이미지 테스트 전략

네이티브 이미지는 빌드 시간이 오래 걸리므로, 모든 테스트를 네이티브 이미지에서 실행하는 것은 비효율적입니다. 다음과 같은 전략이 권장됩니다:

1. **단위 테스트와 대부분의 통합 테스트는 JVM 모드에서 실행**
   - 빠른 피드백을 위해 일반적인 테스트는 JVM 모드에서 실행

2. **네이티브 이미지 특화 테스트를 별도로 구성**
   - 네이티브 이미지 빌드 후 실행되는 테스트 세트 구성
   - 핵심 기능 및 네이티브 이미지 특화 문제에 집중

3. **CI/CD 파이프라인에서 네이티브 이미지 테스트 실행**
   - 개발 주기에서는 JVM 모드 테스트
   - CI/CD 파이프라인에서 네이티브 이미지 테스트 자동화

### 네이티브 이미지 테스트 구성

#### 네이티브 이미지 테스트를 위한 Gradle 설정

```kotlin
// build.gradle.kts
tasks.register<Test>("nativeTest") {
    group = "verification"
    description = "Runs tests on native image"
    
    dependsOn("nativeCompile")
    
    doFirst {
        // Set environment variables for native test
        environment("SPRING_PROFILES_ACTIVE", "native-test")
    }
    
    doLast {
        // Run tests using the native image
        exec {
            workingDir = file("${buildDir}/native/nativeCompile")
            commandLine = listOf("./my-app", "--spring.profiles.active=native-test")
        }
    }
}
```

#### 네이티브 이미지 특화 테스트 클래스

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["spring.profiles.active=native-test"])
@ActiveProfiles("native-test")
class NativeSpecificTest {
    
    @LocalServerPort
    private var port: Int = 0
    
    @Test
    fun `should handle reflection based operations in native mode`() {
        // Test reflection-based functionality
        val client = RestTemplate()
        val response = client.getForEntity("http://localhost:$port/api/reflection-test", String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("Reflection test passed") ?: false)
    }
    
    @Test
    fun `should load resources properly in native mode`() {
        val client = RestTemplate()
        val response = client.getForEntity("http://localhost:$port/api/resource-test", String::class.java)
        
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("Resource loading test passed") ?: false)
    }
}
```

### 네이티브 이미지 End-to-End 테스트

```kotlin
// Docker 기반 E2E 테스트
class NativeDockerE2ETest {
    
    companion object {
        @Container
        val applicationContainer = GenericContainer("my-native-app:latest")
            .withExposedPorts(8080)
            .withNetwork(Network.newNetwork())
            .dependsOn(
                PostgreSQLContainer<Nothing>("postgres:14-alpine").apply {
                    withDatabaseName("testdb")
                    withUsername("testuser")
                    withPassword("testpass")
                    withNetwork(Network.SHARED)
                    withNetworkAliases("postgres")
                }
            )
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/testdb")
            .withEnv("SPRING_DATASOURCE_USERNAME", "testuser")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "testpass")
    }
    
    @Test
    fun `should start and process requests`() {
        // Wait for application to be ready
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until {
            try {
                val response = RestTemplate().getForEntity(
                    "http://localhost:${applicationContainer.getMappedPort(8080)}/actuator/health",
                    String::class.java
                )
                response.statusCode == HttpStatus.OK
            } catch (e: Exception) {
                false
            }
        }
        
        // Perform test
        val response = RestTemplate().getForEntity(
            "http://localhost:${applicationContainer.getMappedPort(8080)}/api/users",
            String::class.java
        )
        
        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
```

## 5. 네이티브 이미지 디버깅 기법

### 주요 디버깅 접근법

네이티브 이미지 디버깅은 일반적인 JVM 애플리케이션보다 복잡합니다. 다음과 같은 접근법을 활용할 수 있습니다:

1. **자세한 로깅 활성화**
   - 빌드 타임 로깅: GraalVM 빌드 과정의 자세한 로그
   - 런타임 로깅: 애플리케이션 로그 레벨 상향 조정

2. **디버그 정보 포함한 네이티브 이미지 빌드**
   - 스택 트레이스 및 디버그 심볼 포함

3. **점진적 접근법**
   - 최소한의 코드부터 시작하여 점진적으로 확장
   - 문제 발생 시 이진 탐색으로 원인 식별

4. **트레이스 에이전트 활용**
   - JVM 모드에서 트레이스 에이전트 실행
   - 리플렉션, 리소스 등의 사용 패턴 추적

### 빌드 시점 디버깅

#### 빌드 로깅 활성화

```kotlin
// build.gradle.kts
graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("--verbose")
            buildArgs.add("-H:+PrintAnalysisCallTree")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:+PrintClassInitialization")
        }
    }
}
```

또는 명령줄에서 직접 실행:

```bash
./gradlew nativeCompile -Porg.gradle.logging.level=info
```

#### 조건부 클래스 초기화 디버깅

```
--initialize-at-build-time=<class-name-list> \
--initialize-at-run-time=<class-name-list> \
-H:+TraceClassInitialization
```

### 런타임 디버깅

#### 디버그 정보 포함한 네이티브 이미지 빌드

```kotlin
graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-g") // 디버그 정보 포함
            buildArgs.add("-H:+IncludeDebugInfo")
        }
    }
}
```

#### GDB 활용한 디버깅

1. 디버그 정보가 포함된 네이티브 이미지 빌드
2. GDB로 네이티브 이미지 실행:

```bash
gdb --args ./build/native/nativeCompile/my-app
```

3. 기본적인 GDB 명령어:
   - 브레이크포인트 설정: `break <function-name>`
   - 실행: `run`
   - 다음 단계: `next` 또는 `n`
   - 내부 진입: `step` 또는 `s`
   - 변수 검사: `print <variable-name>`
   - 스택 트레이스: `bt`

#### 예제: GDB를 활용한 디버깅

```bash
# GDB 시작
gdb --args ./build/native/nativeCompile/my-app

# 브레이크포인트 설정
(gdb) break com.example.demo.UserController.getUserById

# 프로그램 실행
(gdb) run

# 브레이크포인트에 도달하면
(gdb) bt      # 스택 트레이스 출력
(gdb) info locals  # 로컬 변수 정보 출력
(gdb) next     # 다음 줄로 이동
(gdb) step     # 함수 내부로 진입
(gdb) print userId  # 변수 값 출력
```

### 런타임 로깅 향상

#### 로깅 구성

```properties
# application.properties
logging.level.root=INFO
logging.level.com.example=DEBUG
logging.level.org.springframework=INFO
logging.level.org.hibernate=INFO

# 로그 패턴 설정
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

#### 코드에 진단 로깅 추가

```kotlin
@Service
class UserServiceImpl(private val userRepository: UserRepository) : UserService {
    
    private val logger = LoggerFactory.getLogger(UserServiceImpl::class.java)
    
    override fun getUserById(id: Long): User {
        logger.debug("Finding user with id: {}", id)
        
        return userRepository.findById(id)
            .also { if (it.isPresent) logger.debug("Found user: {}", it.get()) }
            .orElseThrow { 
                logger.warn("User not found with id: {}", id)
                NoSuchElementException("User not found with id: $id") 
            }
    }
}
```

### 메모리 및 성능 디버깅

#### JFR(Java Flight Recorder) 지원

GraalVM 22.2부터 네이티브 이미지에서 JFR을 제한적으로 지원합니다:

```kotlin
graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-H:+AllowVMInspection")
        }
    }
}
```

실행 시:

```bash
./my-app -XX:+FlightRecorder -XX:StartFlightRecording=filename=recording.jfr
```

#### 메모리 사용량 모니터링

네이티브 이미지의 메모리 사용량 모니터링:

```bash
ps -o pid,rss,vsz,cmd | grep my-app
```

또는 `top` 명령어 활용:

```bash
top -p $(pgrep -f my-app)
```

## 6. CI/CD 파이프라인 구성

### GitHub Actions를 활용한 CI/CD 파이프라인

#### .github/workflows/ci.yml
```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v2
    
    - name: Build and Test (JVM)
      run: ./gradlew build
    
    - name: Set up GraalVM
      uses: graalvm/setup-graalvm@v1
      with:
        version: '22.3.0'
        java-version: '17'
        components: 'native-image'
        github-token: ${{ secrets.GITHUB_TOKEN }}
    
    - name: Build Native Image
      run: ./gradlew nativeCompile
      
    - name: Test Native Image
      run: |
        ./build/native/nativeCompile/my-app --spring.profiles.active=test &
        APP_PID=$!
        sleep 10  # Wait for application to start
        
        # Run basic health check
        HEALTH_CHECK=$(curl -s http://localhost:8080/actuator/health)
        if [[ $HEALTH_CHECK != *"UP"* ]]; then
          echo "Health check failed: $HEALTH_CHECK"
          kill $APP_PID
          exit 1
        fi
        
        # Run functional tests against native image
        curl -s http://localhost:8080/api/test-all
        TEST_RESULT=$?
        
        kill $APP_PID
        exit $TEST_RESULT
    
    - name: Build Docker Image
      if: github.ref == 'refs/heads/main'
      run: |
        docker build -t my-app:latest .
        
    - name: Push Docker Image
      if: github.ref == 'refs/heads/main'
      run: |
        echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
        docker tag my-app:latest ${{ secrets.DOCKER_USERNAME }}/my-app:latest
        docker push ${{ secrets.DOCKER_USERNAME }}/my-app:latest

### Jenkins 파이프라인 구성

#### Jenkinsfile
```groovy
pipeline {
    agent {
        docker {
            image 'ghcr.io/graalvm/graalvm-ce:ol8-java17'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build and Test JVM') {
            steps {
                sh './gradlew clean build'
            }
        }
        
        stage('Build Native Image') {
            steps {
                sh 'gu install native-image'
                sh './gradlew nativeCompile'
            }
        }
        
        stage('Test Native Image') {
            steps {
                sh '''
                    ./build/native/nativeCompile/my-app --spring.profiles.active=test &
                    APP_PID=$!
                    sleep 10
                    
                    # Run tests against the native image
                    curl -s http://localhost:8080/api/test-all
                    TEST_RESULT=$?
                    
                    kill $APP_PID
                    exit $TEST_RESULT
                '''
            }
        }
        
        stage('Build Docker Image') {
            when {
                branch 'main'
            }
            steps {
                sh 'docker build -t my-app:latest .'
            }
        }
        
        stage('Push Docker Image') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials', 
                                                  usernameVariable: 'DOCKER_USERNAME', 
                                                  passwordVariable: 'DOCKER_PASSWORD')]) {
                    sh '''
                        echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin
                        docker tag my-app:latest $DOCKER_USERNAME/my-app:latest
                        docker push $DOCKER_USERNAME/my-app:latest
                    '''
                }
            }
        }
        
        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                sh '''
                    ssh deploy@production-server 'docker pull $DOCKER_USERNAME/my-app:latest'
                    ssh deploy@production-server 'docker-compose up -d'
                '''
            }
        }
    }
    
    post {
        always {
            junit '**/build/test-results/test/*.xml'
        }
    }
}
```

### GitLab CI/CD 구성

#### .gitlab-ci.yml
```yaml
image: eclipse-temurin:17-jdk

variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"

stages:
  - build
  - test
  - native-build
  - native-test
  - docker
  - deploy

before_script:
  - export GRADLE_USER_HOME=`pwd`/.gradle

build:
  stage: build
  script:
    - ./gradlew assemble
  cache:
    key: "$CI_COMMIT_REF_NAME"
    paths:
      - .gradle
      - build
  artifacts:
    paths:
      - build/libs

test:
  stage: test
  script:
    - ./gradlew test
  cache:
    key: "$CI_COMMIT_REF_NAME"
    paths:
      - .gradle
  artifacts:
    reports:
      junit: build/test-results/test/TEST-*.xml

native-build:
  stage: native-build
  image: ghcr.io/graalvm/graalvm-ce:ol8-java17
  before_script:
    - gu install native-image
  script:
    - ./gradlew nativeCompile
  cache:
    key: "$CI_COMMIT_REF_NAME-native"
    paths:
      - .gradle
      - build
  artifacts:
    paths:
      - build/native/nativeCompile
  only:
    - main
    - develop

native-test:
  stage: native-test
  image: ubuntu:20.04
  script:
    - ./build/native/nativeCompile/my-app --spring.profiles.active=test &
    - sleep 10
    - apt-get update && apt-get install -y curl
    - curl -s http://localhost:8080/actuator/health
  only:
    - main
    - develop

docker:
  stage: docker
  image: docker:20.10.16
  services:
    - docker:20.10.16-dind
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ""
  script:
    - docker build -t $CI_REGISTRY_IMAGE:$CI_COMMIT_REF_SLUG .
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker push $CI_REGISTRY_IMAGE:$CI_COMMIT_REF_SLUG
  only:
    - main
    - develop

deploy:
  stage: deploy
  image: alpine:latest
  before_script:
    - apk add --no-cache openssh-client
    - eval $(ssh-agent -s)
    - echo "$SSH_PRIVATE_KEY" | tr -d '\r' | ssh-add -
    - mkdir -p ~/.ssh
    - chmod 700 ~/.ssh
    - echo "$SSH_KNOWN_HOSTS" >> ~/.ssh/known_hosts
    - chmod 644 ~/.ssh/known_hosts
  script:
    - ssh deploy@production-server "docker pull $CI_REGISTRY_IMAGE:$CI_COMMIT_REF_SLUG"
    - ssh deploy@production-server "docker-compose up -d"
  only:
    - main
```

## 7. 성능 테스트 및 모니터링

### JMeter를 활용한 부하 테스트

#### JMeter 테스트 계획 (Test Plan)

1. **Thread Group 설정**
   - Number of Threads (users): 100
   - Ramp-up period: 30 seconds
   - Loop Count: 10

2. **HTTP Request 설정**
   - GET /api/users
   - GET /api/posts
   - POST /api/users (사용자 생성)
   - POST /api/posts (게시물 생성)

3. **결과 수집**
   - View Results Tree
   - Aggregate Report
   - Response Time Graph

#### JMeter 테스트 실행

```bash
# CLI 모드로 JMeter 실행
jmeter -n -t my-test-plan.jmx -l results.jtl
```

### Micrometer를 활용한 메트릭 수집

#### 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
```

#### 설정

```properties
# application.properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.health.show-details=always
management.metrics.export.prometheus.enabled=true
```

#### 사용자 정의 메트릭 추가

```kotlin
@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val meterRegistry: MeterRegistry
) : UserService {
    
    override fun getUserById(id: Long): User {
        val timer = meterRegistry.timer("service.user.get-by-id")
        
        return timer.record<User> {
            userRepository.findById(id)
                .orElseThrow { NoSuchElementException("User not found with id: $id") }
        }
    }
    
    override fun createUser(user: User): User {
        meterRegistry.counter("service.user.create").increment()
        return userRepository.save(user)
    }
}
```

### Prometheus 및 Grafana 설정

#### docker-compose.yml에 모니터링 스택 추가

```yaml
version: '3.8'

services:
  app:
    image: my-app:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
  
  prometheus:
    image: prom/prometheus:v2.37.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    depends_on:
      - app
  
  grafana:
    image: grafana/grafana:9.1.0
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana
    depends_on:
      - prometheus

volumes:
  grafana-data:
```

#### prometheus.yml 구성

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
```

### Chaos Engineering

#### Chaos Monkey를 활용한 복원력 테스트

```kotlin
// build.gradle.kts
dependencies {
    implementation("de.codecentric:chaos-monkey-spring-boot:2.6.1")
}
```

```properties
# application.properties
chaos.monkey.enabled=true
chaos.monkey.watcher.controller=true
chaos.monkey.watcher.service=true
chaos.monkey.watcher.repository=true
```

## 8. 실습: 전체 테스트 스위트 구현

### 단위 테스트 구현

단위 테스트는 개별 비즈니스 로직을 검증합니다. 다음 클래스에 대한 단위 테스트를 작성하세요:

1. `UserService`
2. `PostService`
3. `CommentService`

모킹 프레임워크(Mockito 또는 MockK)를 활용하여 의존성을 격리하세요.

### 통합 테스트 구현

다음 통합 지점에 대한 통합 테스트를 작성하세요:

1. 컨트롤러 계층 테스트(`MockMvc` 또는 `WebTestClient` 활용)
2. 리포지토리 계층 테스트(실제 데이터베이스 또는 TestContainers 활용)
3. 서비스 계층 통합 테스트(실제 의존성 주입)

### 통합 테스트 환경 구성

`@TestConfiguration`을 활용하여 테스트 환경 구성:

```kotlin
@TestConfiguration
class TestConfig {
    
    @Bean
    fun testDataSource(): DataSource {
        return EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:schema-test.sql")
            .addScript("classpath:data-test.sql")
            .build()
    }
    
    @Bean
    fun testClock(): Clock {
        return Clock.fixed(
            Instant.parse("2023-01-01T10:00:00Z"),
            ZoneId.of("UTC")
        )
    }
}
```

### 테스트 데이터 셋업

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class IntegrationTest {
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Autowired
    private lateinit var postRepository: PostRepository
    
    @BeforeEach
    fun setup() {
        // 데이터베이스 초기화
        postRepository.deleteAll()
        userRepository.deleteAll()
        
        // 테스트 데이터 추가
        val user1 = userRepository.save(User(name = "User 1", email = "user1@example.com"))
        val user2 = userRepository.save(User(name = "User 2", email = "user2@example.com"))
        
        postRepository.save(Post(title = "Post 1", content = "Content 1", author = user1))
        postRepository.save(Post(title = "Post 2", content = "Content 2", author = user1))
        postRepository.save(Post(title = "Post 3", content = "Content 3", author = user2))
    }
}
```

### 네이티브 이미지 테스트 스크립트

#### native-test.sh
```bash
#!/bin/bash

# 빌드 및 실행
./gradlew nativeCompile
./build/native/nativeCompile/my-app --spring.profiles.active=test &

APP_PID=$!
echo "Application started with PID: $APP_PID"

# 애플리케이션 시작 대기
echo "Waiting for application to start..."
sleep 10

# 기본 건강 체크
echo "Performing health check..."
HEALTH_CHECK=$(curl -s http://localhost:8080/actuator/health)
if [[ $HEALTH_CHECK != *"UP"* ]]; then
  echo "Health check failed: $HEALTH_CHECK"
  kill $APP_PID
  exit 1
fi
echo "Health check passed"

# 기능 테스트
echo "Running functional tests..."

# 사용자 생성 테스트
echo "Testing user creation..."
CREATE_USER_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com","password":"password123"}' \
  http://localhost:8080/api/users)

USER_ID=$(echo $CREATE_USER_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d ':' -f2)
if [ -z "$USER_ID" ]; then
  echo "Failed to create user"
  kill $APP_PID
  exit 1
fi
echo "User created with ID: $USER_ID"

# 사용자 조회 테스트
echo "Testing user retrieval..."
GET_USER_RESPONSE=$(curl -s http://localhost:8080/api/users/$USER_ID)
if [[ $GET_USER_RESPONSE != *"test@example.com"* ]]; then
  echo "Failed to retrieve user: $GET_USER_RESPONSE"
  kill $APP_PID
  exit 1
fi
echo "User retrieval successful"

# 게시물 생성 테스트
echo "Testing post creation..."
CREATE_POST_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"title":"Test Post","content":"Test Content","authorId":'$USER_ID'}' \
  http://localhost:8080/api/posts)

POST_ID=$(echo $CREATE_POST_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d ':' -f2)
if [ -z "$POST_ID" ]; then
  echo "Failed to create post"
  kill $APP_PID
  exit 1
fi
echo "Post created with ID: $POST_ID"

# 게시물 조회 테스트
echo "Testing post retrieval..."
GET_POST_RESPONSE=$(curl -s http://localhost:8080/api/posts/$POST_ID)
if [[ $GET_POST_RESPONSE != *"Test Post"* ]]; then
  echo "Failed to retrieve post: $GET_POST_RESPONSE"
  kill $APP_PID
  exit 1
fi
echo "Post retrieval successful"

# 애플리케이션 종료
echo "All tests passed successfully!"
kill $APP_PID
exit 0
```

## 9. 과제

1. 3주차에서 만든 프로젝트에 대한 단위 테스트와 통합 테스트를 작성하세요:
   - 서비스 계층에 대한 단위 테스트
   - 컨트롤러 계층에 대한 통합 테스트
   - 리포지토리 계층에 대한 테스트

2. 네이티브 이미지 디버깅 기법을 적용하여 다음 문제를 해결하세요:
   - 리플렉션 관련 문제 해결
   - 리소스 로딩 문제 해결
   - 초기화 문제 해결

3. GitHub Actions 또는 Jenkins 파이프라인을 설정하여 다음을 자동화하세요:
   - JVM 모드에서의 빌드 및 테스트
   - 네이티브 이미지 빌드 및 테스트
   - Docker 이미지 생성 및 배포

4. 성능 테스트를 구성하고 다음 항목을 측정하세요:
   - JVM 모드 vs 네이티브 이미지 시작 시간
   - JVM 모드 vs 네이티브 이미지 메모리 사용량
   - JVM 모드 vs 네이티브 이미지 처리량
   - JVM 모드 vs 네이티브 이미지 응답 시간

5. Prometheus와 Grafana를 활용하여 애플리케이션 모니터링 대시보드를 구성하세요:
   - JVM 메트릭 (JVM 모드 실행 시)
   - 시스템 메트릭
   - 애플리케이션 메트릭
   - 트래픽 패턴 분석

## 10. 참고 자료

### 공식 문서
- [GraalVM Native Image Debugging and Diagnostics](https://www.graalvm.org/latest/reference-manual/native-image/debugging-and-diagnostics/)
- [Spring Boot Testing Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/testing.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Testcontainers Documentation](https://www.testcontainers.org/quickstart/junit_5_quickstart/)

### 블로그 및 튜토리얼
- [Testing Spring Boot Applications with GraalVM Native Image](https://spring.io/blog/2021/06/09/spring-boot-native-applications-techniques-and-debugging)
- [Debugging Native Images](https://medium.com/graalvm/debugging-native-images-8f46596103e8)
- [Continuous Integration with GitHub Actions and GraalVM](https://medium.com/graalvm/using-graalvm-and-native-image-on-github-actions-73c968c2f134)

### 샘플 프로젝트
- [Spring Boot Native Testing Samples](https://github.com/spring-projects-experimental/spring-native-samples)
- [Spring PetClinic with Native Image Support](https://github.com/spring-petclinic/spring-petclinic-native)
- [GraalVM Demos with Tests](https://github.com/graalvm/graalvm-demos)

---

## 다음 주차 미리보기
- 성능 모니터링 및 최적화
- 네이티브 이미지 메모리 관리 이해
- 성능 모니터링 도구 설정
- GC 최적화 기법
- 병목 현상 분석 및 해결 Set up JDK
      uses: actions/setup-java@v2
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle
    
    - name:
