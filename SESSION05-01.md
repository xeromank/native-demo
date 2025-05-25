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
- ```
  ## JVM 힙 구조
    JVM 힙은 자바 객체들이 저장되는 메모리 영역으로, 크게 **Young Generation**과 **Old Generation**으로 나뉨
    
    ### Young Generation
    - **Eden Space**: 새로 생성된 객체들이 처음 할당되는 공간
      - **Survivor Space (S0, S1)**: Eden에서 살아남은 객체들이 이동하는 두 개의 공간
      - 대부분의 객체가 여기서 생성되고 빠르게 소멸됨
    
    ### Old Generation (Tenured Space)
    - Young Generation에서 오래 살아남은 객체들이 승격되는 공간
      - 크기가 크고 GC 빈도가 낮음
      - 장기간 사용되는 객체들이 저장됨
    
    ### Metaspace (Java 8 이후)
    - 클래스 메타데이터가 저장되는 영역
      - 이전의 Permanent Generation을 대체함
    
    ## GC 동작 방식
    
    ### Minor GC (Young Generation GC)
    1. Eden 영역이 가득 차면 발생
       2. 살아있는 객체들을 Survivor 영역으로 이동
       3. Eden과 한쪽 Survivor 영역을 비움
       4. 빠르고 자주 발생
    
    ### Major GC (Old Generation GC)
    1. Old Generation이 가득 찰 때 발생
       2. 전체 힙을 대상으로 하는 경우가 많음
       3. 시간이 오래 걸리고 애플리케이션 중단이 발생할 수 있음
    
    ### Full GC
    - Young과 Old Generation을 모두 정리하는 GC
      - 가장 오래 걸리고 성능에 큰 영향을 줌
    
    ## 객체 생존 과정
    
    새 객체 → Eden → Survivor (여러 번 이동) → Old Generation
    
    객체가 Minor GC를 여러 번 살아남으면 "나이"가 증가하고, 특정 임계값에 도달하면 Old Generation으로 승격됨. 이 과정을 통해 단기 객체와 장기 객체를 효율적으로 분리 관리할 수 있음.
  ```
- 네이티브 이미지의 정적 메모리 할당 특성
- ```
  ## 네이티브 이미지의 정적 메모리 할당 특성
    
    네이티브 이미지는 컴파일 타임에 메모리 구조가 결정되는 특성을 가짐
    
    ### 컴파일 타임 초기화
    - 모든 클래스와 객체가 빌드 시점에 초기화됨
      - 힙 스냅샷(Heap Snapshot)이 실행 파일에 포함됨
      - 런타임에 동적으로 로딩할 수 없는 구조
    
    ### 힙 구조의 변화
    - **Young/Old Generation 구분이 없음**
      - 전체 힙이 하나의 연속된 메모리 블록으로 구성
      - GC 알고리즘이 Serial GC로 제한됨
    
    ### 메모리 할당 방식
    전통적 JVM: 동적 할당 → Eden → Survivor → Old
    네이티브 이미지: 정적 할당 → 단일 힙 영역
    
    ### 클래스 로딩 제약
    - 리플렉션 사용 시 사전 등록 필요
      - 동적 프록시 생성 제한
      - 클래스패스 스캐닝 불가
    
    ### 메모리 최적화 효과
    - **시작 시간 단축**: 클래스 로딩 과정 생략
      - **메모리 사용량 감소**: 불필요한 메타데이터 제거
      - **예측 가능한 성능**: 컴파일 타임 최적화
    
    ### 제약사항
    - 런타임 코드 생성 불가
      - 동적 클래스 로딩 제한
      - 일부 Java 라이브러리 호환성 문제
    
    ### GC 동작 변화
    - **Stop-the-World 시간 단축**: 단순한 힙 구조
      - **처리량 감소**: 최적화된 GC 알고리즘 사용 불가
      - **메모리 압축 효과**: 사전 최적화된 객체 배치
    
    네이티브 이미지는 빠른 시작과 낮은 메모리 사용량을 위해 동적 특성을 포기하고 정적 최적화를 선택한 접근 방식임
  ```
- 네이티브 이미지에서의 Substrate VM과 Serial GC 동작 방식
- ```
  ## Substrate VM 개요

    Substrate VM은 GraalVM 네이티브 이미지의 핵심 런타임으로, 전통적인 HotSpot JVM을 대체함
    
    ### Substrate VM 특징
    - **AOT 컴파일**: 바이트코드를 네이티브 코드로 사전 컴파일
      - **Closed World Assumption**: 모든 코드가 컴파일 타임에 알려져야 함
      - **단순화된 런타임**: JIT 컴파일러, 동적 클래스 로딩 제거
      - **직접 메모리 관리**: OS 메모리 관리자와 직접 상호작용
    
    ## Serial GC 동작 방식
    
    ### 기본 구조
    네이티브 이미지에서는 Serial GC만 사용 가능하며, 단일 스레드로 동작함
    
    ### 힙 구성
    [Young Generation] [Old Generation]
         |                    |
       Eden + Survivor     Tenured Space
    
    ### 컬렉션 알고리즘
    
    #### Minor GC (Young Generation)
    1. **Mark Phase**: Eden과 Survivor에서 살아있는 객체 표시
       2. **Copy Phase**: 살아있는 객체를 다른 Survivor나 Old Generation으로 복사
       3. **Sweep Phase**: 사용하지 않는 영역 정리
    
    #### Major GC (Old Generation)  
    1. **Mark Phase**: 루트부터 도달 가능한 모든 객체 표시
       2. **Compact Phase**: 살아있는 객체들을 연속된 메모리로 이동
       3. **Sweep Phase**: 빈 공간 정리
    
    ### 네이티브 이미지에서의 최적화
    
    #### 정적 분석 기반 최적화
    - 컴파일 타임에 객체 생존 패턴 분석
      - 불필요한 할당 제거
      - 객체 풀링 자동 적용
    
    #### 메모리 레이아웃 최적화
    컴파일 타임 최적화:
    - 자주 함께 사용되는 객체를 인접 위치에 배치
    - 캐시 친화적인 메모리 구조 생성
    - 메모리 단편화 최소화
    
    ### Serial GC의 장단점
    
    #### 장점
    - **단순성**: 구현이 간단하고 예측 가능
      - **메모리 효율성**: 오버헤드가 적음
      - **결정적 동작**: 항상 동일한 방식으로 동작
    
    #### 단점
    - **Stop-the-World**: GC 실행 중 애플리케이션 중단
      - **단일 스레드**: 멀티코어 활용 불가
      - **스케일링 한계**: 큰 힙에서 성능 저하
    
    ### 최적화 전략
    
    #### 메모리 사용량 제어
    - **-Xmx** 옵션으로 힙 크기 제한
      - **-XX:MaxNewSize** 로 Young Generation 크기 조정
      - **객체 풀링** 패턴 활용
    
    ### 모니터링과 튜닝
    
    #### GC 로그 활성화
    -XX:+PrintGC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps
    
    #### 성능 측정 지표
    - **GC 빈도**: Minor/Major GC 발생 횟수
      - **GC 시간**: Stop-the-World 지속 시간
      - **메모리 사용률**: 힙 사용량 패턴
    
    Substrate VM과 Serial GC는 빠른 시작 시간과 낮은 메모리 사용량을 위해 단순성을 선택한 설계로, 마이크로서비스나 서버리스 환경에 최적화됨
  ```
  - [G1GC 및 Epsilon GC 사용가능](https://www.graalvm.org/latest/reference-manual/native-image/optimizations-and-performance/MemoryManagement/)

- 메모리 관리 차이점: 동적 vs 정적 분석
- ```
  ## 동적 vs 정적 메모리 관리 분석

    ### 동적 분석 (Runtime Analysis)
    
    #### 기본 원리
    - **실행 중 분석**: 애플리케이션이 실행되면서 실시간으로 메모리 사용 패턴 분석
      - **적응적 최적화**: 런타임 정보를 바탕으로 GC 전략 조정
      - **프로파일링**: 실제 객체 생존 패턴과 할당 빈도 측정
    
    실행 → 프로파일링 → 최적화 → 재최적화
    
    #### 동적 최적화 기법
    - **Adaptive Sizing**: 힙 영역 크기를 실행 중 자동 조정
      - **Generational Hypothesis**: 실제 객체 생존 패턴 관찰
      - **Hot Path Detection**: 자주 사용되는 코드 경로 식별
      - **GC 알고리즘 전환**: 워크로드에 따른 GC 전략 변경
    
    ### 정적 분석 (Compile-time Analysis)
    
    #### 기본 원리
    - **사전 분석**: 컴파일 타임에 모든 코드 경로와 메모리 사용 패턴 분석
      - **고정된 최적화**: 빌드 시점에 결정된 최적화가 런타임에 적용
      - **Closed World Assumption**: 모든 가능한 실행 경로가 컴파일 타임에 알려짐
    
    #### 네이티브 이미지 (GraalVM) 방식
    소스코드 → 정적분석 → AOT컴파일 → 최적화된바이너리
    
    #### 정적 최적화 기법
    - **Dead Code Elimination**: 사용되지 않는 코드 제거
      - **Escape Analysis**: 객체 생존 범위 사전 결정
      - **Memory Layout Optimization**: 객체 배치 최적화
      - **Constant Folding**: 컴파일 타임 상수 계산
    
    ### 장단점 비교
    
    #### 동적 분석의 장점
    - **적응성**: 실제 워크로드에 맞춘 최적화
      - **유연성**: 실행 환경 변화에 대응
      - **정확성**: 실제 사용 패턴 기반 최적화
      - **복잡한 시나리오 처리**: 예측하기 어려운 패턴도 처리
    
    #### 동적 분석의 단점
    - **Warm-up 시간**: 최적화까지 시간 필요
      - **런타임 오버헤드**: 프로파일링 비용
      - **메모리 사용량**: 메타데이터와 프로파일 정보 저장
      - **예측 불가능성**: 성능이 워크로드에 따라 변동
    
    #### 정적 분석의 장점
    - **즉시 최적화**: 시작부터 최적화된 성능
      - **예측 가능성**: 일관된 성능 특성
      - **낮은 오버헤드**: 런타임 분석 비용 없음
      - **작은 메모리 풋프린트**: 메타데이터 최소화
    
    #### 정적 분석의 단점
    - **제한된 적응성**: 고정된 최적화
      - **불완전한 분석**: 모든 실행 경로 예측 어려움
      - **동적 기능 제약**: 리플렉션, 동적 로딩 제한
      - **최적화 한계**: 컴파일 타임 정보 의존
    
    ### 실제 적용 사례
    
    #### 동적 분석 적합한 환경
    - 장시간 실행되는 서버 애플리케이션
    - 다양한 워크로드를 처리하는 시스템
    - 복잡한 비즈니스 로직
    - 외부 데이터에 따라 동작이 변하는 애플리케이션
    
    #### 정적 분석 적합한 환경
    - 마이크로서비스, 서버리스 함수
    - 짧은 실행 시간의 애플리케이션
    - 일정한 워크로드 패턴
    - 빠른 시작 시간이 중요한 경우
    
    ### 하이브리드 접근법
    
    #### Profile-Guided Optimization (PGO)
    1. 프로파일 수집 빌드로 실행
    2. 실행 중 프로파일 데이터 수집
    3. 프로파일 기반으로 재컴파일
    4. 최적화된 바이너리 생성
    
    #### JIT + AOT 혼합
    - 핫 메서드는 JIT 컴파일
    - 콜드 코드는 AOT 컴파일
    - 런타임에 선택적 최적화
    
    동적 분석은 **적응성과 정확성**을, 정적 분석은 **예측성과 효율성**을 추구하는 서로 다른 철학의 메모리 관리 접근법임
  ```
Dead Code Elimination
```
Dead Code Elimination(데드 코드 제거)는 컴파일러 최적화 기법 중 하나로, 
프로그램에서 실행되지 않거나 결과에 영향을 주지 않는 코드를 자동으로 제거하는 과정이야.
```

#### 1.2 네이티브 이미지의 힙 설정 및 튜닝
- 힙 크기 설정 옵션 (`--gc.maxHeapSize` 등)
  - 네이티브 이미지 생성시 xmx xms 설정 가능 
- 스택 크기 설정 (`--stack-size`)
  - 네이티브 코드는 JVM 바이트코드보다 스택을 많이 사용
  - 최소 1m의 스택 할당 필요
  - JNI 및 네이티브 호출 LIB 사용시 2m 권장
- 코드 캐시 크기 설정 (`--code-cache-size`)
  - 불필요: 네이티브 이미지에서는 모든 코드가 이미 AOT 컴파일되어 바이너리에 포함되어 
- 다양한 GC 알고리즘 옵션 비교 (Serial, G1)
  - Serial GC -> 소규모 마이크로 서비스
  - G1 GC -> 웹 API 
  - Epsilon GC -> 배치

#### 1.3 메모리 누수 탐지 방법
- 네이티브 이미지에서의 메모리 누수 특성
  - 정적 분석의 한계로 인한 누수
    - 사용자 입력에 따른 동적 할당
    - JNI 사용시 메모리 미 회수
- 메모리 프로파일링 도구 활용 방법
  - GC 로그 분석
  - 힙 덤프 생성 및 분석
  - Java Flight Recorder (JFR)
    - native-image --enable-monitoring=jfr MyApp
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
