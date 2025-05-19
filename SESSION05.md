# 코틀린 스프링부트 네이티브 이미지 스터디 5주차
## 성능 모니터링 및 최적화

### 학습 목표
- 네이티브 이미지의 메모리 관리 특성 이해하기
- 성능 모니터링 도구 설정 및 활용 방법 익히기
- 네이티브 이미지 성능 최적화 기법 학습하기
- 실제 환경에서 성능 테스트 및 벤치마킹 수행하기

### 1. 네이티브 이미지 메모리 관리 이해 (40분)

#### 1.1 JVM과 네이티브 이미지의 메모리 모델 비교
- JVM 힙 구조와 GC 동작 방식 리뷰
- 네이티브 이미지의 정적 메모리 할당 특성
- 네이티브 이미지에서의 Substrate VM과 Serial GC 동작 방식
- 메모리 관리 차이점: 동적 vs 정적 분석

#### 1.2 네이티브 이미지의 힙 설정 및 튜닝
- 힙 크기 설정 옵션 (`--gc.maxHeapSize` 등)
- 스택 크기 설정 (`--stack-size`)
- 코드 캐시 크기 설정 (`--code-cache-size`)
- 다양한 GC 알고리즘 옵션 비교 (Serial, G1)

#### 1.3 메모리 누수 탐지 방법
- 네이티브 이미지에서의 메모리 누수 특성
- 메모리 프로파일링 도구 활용 방법
- 힙 덤프 분석 및 메모리 누수 패턴 식별

### 2. 성능 모니터링 도구 설정 (Micrometer, Prometheus) (60분)

#### 2.1 Micrometer 설정 및 활용
- Micrometer 의존성 추가 및 설정
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
```
- 주요 메트릭 설정 방법
```kotlin
@Configuration
class MetricsConfig {
    @Bean
    fun registerCommonMetrics(meterRegistry: MeterRegistry) {
        // CPU 사용량 메트릭 등록
        Gauge.builder("system.cpu.usage", Runtime.getRuntime()) { rt -> 
            rt.availableProcessors().toDouble() 
        }.register(meterRegistry)
        
        // 메모리 사용량 메트릭 등록
        Gauge.builder("system.memory.used", Runtime.getRuntime()) { rt -> 
            (rt.totalMemory() - rt.freeMemory()).toDouble() 
        }.register(meterRegistry)
    }
}
```
- 커스텀 메트릭 등록 방법
```kotlin
@Service
class UserService(private val meterRegistry: MeterRegistry) {
    fun registerUser(user: User) {
        meterRegistry.counter("user.registration.count").increment()
        // 회원가입 로직...
    }
}
```

#### 2.2 Prometheus 및 Grafana 설정
- Prometheus 설치 및 설정 방법
- Prometheus 스크래핑 설정
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'spring-native-app'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['localhost:8080']
```
- Grafana 연동 방법
- 대시보드 구성 예제

#### 2.3 Spring Boot Actuator 활용
- Actuator 엔드포인트 설정
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```
- 주요 모니터링 엔드포인트 활용 방법
- 네이티브 이미지에서의 Actuator 제한사항

### 3. GC 최적화 기법 (30분)

#### 3.1 네이티브 이미지의 GC 특성
- Substrate VM의 GC 동작 방식
- Serial GC와 G1 GC 비교
- 네이티브 이미지에서의 GC 튜닝 제약사항

#### 3.2 GC 튜닝 옵션
- 힙 크기 조정 (`--gc.maxHeapSize`)
```bash
./gradlew nativeCompile -Pargs=--gc.maxHeapSize=64m
```
- GC 알고리즘 선택
```bash
./gradlew nativeCompile -Pargs=-H:+UseG1GC
```
- GC 로깅 활성화
```bash
./gradlew nativeCompile -Pargs=-R:+PrintGC
```

#### 3.3 네이티브 이미지에서의 가비지 컬렉션 최적화 전략
- 객체 생성 최소화 기법
- 불필요한 객체 참조 제거 패턴
- 메모리 풀링 기법 적용
- 데이터 구조 최적화

### 4. 병목 현상 분석 및 해결 (40분)

#### 4.1 성능 병목 식별 방법
- CPU 프로파일링 도구 활용
- 응답 시간 지연 분석
- 스레드 덤프 분석
- 프로파일링 결과 해석 방법

#### 4.2 웹 서비스 병목 현상 해결 기법
- 비동기 프로세싱 적용
```kotlin
@Service
class AsyncService {
    @Async
    fun processData(data: List<Data>): CompletableFuture<Result> {
        // 비동기 처리 로직
        return CompletableFuture.completedFuture(Result())
    }
}
```
- 커넥션 풀 최적화
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
```
- 캐싱 전략 적용
```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager {
        return ConcurrentMapCacheManager("users", "products")
    }
}
```

#### 4.3 코틀린 코드 최적화 기법
- 불변 객체 활용
- 확장 함수 최적화
- 시퀀스 활용 방법
```kotlin
// 대량 데이터 처리 시 시퀀스 활용
val result = items.asSequence()
    .filter { it.isActive }
    .map { it.process() }
    .take(10)
    .toList()
```

### 5. 실습: 성능 테스트 및 벤치마킹 (60분)

#### 5.1 성능 테스트 환경 설정
- JMeter 설치 및 설정
- 테스트 시나리오 작성
- 부하 테스트 계획 수립

#### 5.2 실제 벤치마킹 수행
- 동일 애플리케이션의 JVM 버전 vs 네이티브 이미지 버전 성능 비교
- 다양한 부하 조건에서의 성능 측정
- 시작 시간, 메모리 사용량, 응답 시간 비교

#### 5.3 성능 테스트 결과 분석
- 수집된 메트릭 분석 방법
- 성능 문제 원인 식별
- 성능 향상을 위한 최적화 방안 도출

### 6. 마무리 및 Q&A (20분)
- 주요 내용 요약
- 질의응답 시간
- 다음 주차 과제 안내

### 실습 과제
1. 작은 규모의 REST API 애플리케이션에 Micrometer와 Prometheus를 연동하여 성능 모니터링 환경 구축하기
2. JMeter를 사용하여 동일 애플리케이션의 JVM 버전과 네이티브 이미지 버전의 성능 비교 보고서 작성하기
3. 선택한 애플리케이션에서 성능 병목 현상을 하나 이상 식별하고 최적화하기

### 참고 자료
- Spring Native 공식 문서: https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/
- GraalVM 메모리 관리 가이드: https://www.graalvm.org/latest/reference-manual/native-image/memory-management/
- Micrometer 공식 문서: https://micrometer.io/docs
- Prometheus 공식 문서: https://prometheus.io/docs/introduction/overview/
- JMeter 튜토리얼: https://jmeter.apache.org/usermanual/

### 준비물
- 노트북 (개발 환경 설정 완료)
- 실습용 애플리케이션 코드
- JMeter 설치
- Prometheus 및 Grafana 설치 (Docker 환경 권장)
