# 6주차: 클라우드 배포 및 컨테이너화

## 📋 학습 목표
- Docker와 네이티브 이미지 통합 방법 이해
- Kubernetes에서 네이티브 이미지 배포 전략 학습
- 클라우드 환경에서의 최적화 기법 습득
- 12 Factor App 원칙을 네이티브 이미지에 적용

## 📚 이론 강의 (40분)

### 1. Docker와 네이티브 이미지 통합

#### 1.1 멀티스테이지 Docker 빌드 전략
```dockerfile
# Dockerfile.native
FROM ghcr.io/graalvm/graalvm-ce:ol7-java17 AS builder

# 빌드 도구 설치
RUN microdnf install -y tar gzip

# 애플리케이션 코드 복사
WORKDIR /app
COPY . .

# 네이티브 이미지 빌드
RUN ./gradlew nativeCompile

# 런타임 이미지
FROM gcr.io/distroless/base:latest
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/myapp .
EXPOSE 8080
ENTRYPOINT ["./myapp"]
```

#### 1.2 최적화된 Dockerfile 구성
```dockerfile
# 최적화된 빌드를 위한 Dockerfile
FROM ghcr.io/graalvm/graalvm-ce:ol7-java17 AS builder

# 필요한 도구만 설치
RUN microdnf install -y tar gzip findutils

# 의존성과 소스코드 분리 복사 (캐시 최적화)
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY src/ src/

# 네이티브 이미지 빌드 (힙 크기 조정)
RUN ./gradlew nativeCompile \
    -Dorg.gradle.jvmargs="-Xmx4g" \
    --no-daemon

# 최종 실행 이미지 (scratch 또는 distroless)
FROM scratch
COPY --from=builder /app/build/native/nativeCompile/myapp /myapp
EXPOSE 8080
ENTRYPOINT ["/myapp"]
```

### 2. Kubernetes 배포 전략

#### 2.1 Deployment 매니페스트
```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kotlin-native-app
  labels:
    app: kotlin-native-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: kotlin-native-app
  template:
    metadata:
      labels:
        app: kotlin-native-app
    spec:
      containers:
      - name: app
        image: myregistry/kotlin-native-app:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        resources:
          requests:
            memory: "64Mi"
            cpu: "50m"
          limits:
            memory: "256Mi"
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 15
          periodSeconds: 20
```

#### 2.2 Service 및 Ingress 설정
```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: kotlin-native-service
spec:
  selector:
    app: kotlin-native-app
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
  type: ClusterIP

---
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: kotlin-native-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - myapp.example.com
    secretName: myapp-tls
  rules:
  - host: myapp.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: kotlin-native-service
            port:
              number: 80
```

### 3. 클라우드 환경 최적화

#### 3.1 AWS 최적화 전략
```yaml
# aws/task-definition.json (AWS Fargate)
{
  "family": "kotlin-native-app",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "kotlin-native-app",
      "image": "123456789012.dkr.ecr.us-east-1.amazonaws.com/kotlin-native-app:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "aws"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/kotlin-native-app",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### 3.2 GCP Cloud Run 설정
```yaml
# gcp/cloudrun.yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: kotlin-native-app
  annotations:
    run.googleapis.com/ingress: all
spec:
  template:
    metadata:
      annotations:
        autoscaling.knative.dev/maxScale: "100"
        run.googleapis.com/cpu-throttling: "false"
    spec:
      containerConcurrency: 1000
      containers:
      - image: gcr.io/my-project/kotlin-native-app:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "gcp"
        resources:
          limits:
            cpu: "1"
            memory: "512Mi"
        startupProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 0
          timeoutSeconds: 240
          periodSeconds: 240
          failureThreshold: 1
```

### 4. 12 Factor App 원칙 적용

#### 4.1 환경 설정 관리
```kotlin
// src/main/kotlin/config/CloudConfig.kt
@Configuration
@ConfigurationProperties("app")
data class AppConfig(
    var name: String = "",
    var version: String = "",
    var database: DatabaseConfig = DatabaseConfig(),
    var cache: CacheConfig = CacheConfig()
)

data class DatabaseConfig(
    var url: String = "",
    var maxPoolSize: Int = 10
)

data class CacheConfig(
    var ttl: Duration = Duration.ofMinutes(5),
    var maxSize: Long = 1000
)
```

#### 4.2 외부 설정 주입
```yaml
# application-k8s.yml
app:
  name: ${APP_NAME:kotlin-native-app}
  version: ${APP_VERSION:1.0.0}
  database:
    url: ${DATABASE_URL}
    max-pool-size: ${DB_POOL_SIZE:20}
  cache:
    ttl: ${CACHE_TTL:PT10M}
    max-size: ${CACHE_MAX_SIZE:5000}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
```

## 💻 실습 과제 (70분)

### 실습 1: 네이티브 이미지 Docker 빌드 (20분)
1. 기존 스프링 부트 애플리케이션을 위한 멀티스테이지 Dockerfile 작성
2. 네이티브 이미지 빌드 및 실행 테스트
3. 이미지 크기 및 시작 시간 측정

### 실습 2: Kubernetes 배포 (25분)
1. Deployment, Service, Ingress 매니페스트 작성
2. ConfigMap과 Secret을 활용한 설정 관리
3. 로컬 Kubernetes 클러스터(minikube/kind)에 배포

### 실습 3: 헬스체크 및 모니터링 설정 (25분)
1. Spring Boot Actuator 헬스체크 엔드포인트 구성
2. Prometheus 메트릭 노출 설정
3. Grafana 대시보드 구성

## 🔧 실습 코드

### Docker Compose로 전체 스택 구성
```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile.native
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DATABASE_URL=jdbc:postgresql://postgres:5432/myapp
    depends_on:
      - postgres
      - prometheus

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: user
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin

volumes:
  postgres_data:
```

### Gradle 네이티브 빌드 구성
```kotlin
// build.gradle.kts
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.graalvm.buildtools.native") version "0.9.28"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    kotlin("plugin.jpa") version "1.9.20"
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("kotlin-native-app")
            mainClass.set("com.example.ApplicationKt")

            buildArgs.addAll(
                "--no-fallback",
                "--enable-preview",
                "--install-exit-handlers",
                "-H:+ReportExceptionStackTraces",
                "-H:+AddAllCharsets",
                "-H:IncludeResources=.*\\.properties$",
                "-H:IncludeResources=.*\\.yml$",
                "-H:IncludeResources=.*\\.yaml$"
            )
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<BuildNativeImageTask>("nativeCompile") {
    dependsOn("test")
}
```

### 헬스체크 구성
```kotlin
// src/main/kotlin/health/CustomHealthIndicator.kt
@Component
class DatabaseHealthIndicator(
    private val dataSource: DataSource
) : HealthIndicator {

    override fun health(): Health {
        return try {
            dataSource.connection.use { connection ->
                val valid = connection.isValid(1000)
                if (valid) {
                    Health.up()
                        .withDetail("database", "Available")
                        .build()
                } else {
                    Health.down()
                        .withDetail("database", "Connection not valid")
                        .build()
                }
            }
        } catch (ex: SQLException) {
            Health.down(ex)
                .withDetail("database", "Connection failed")
                .build()
        }
    }
}
```

## 📝 과제 및 체크리스트

### 과제 1: 컨테이너 최적화
- [ ] 멀티스테이지 빌드를 활용한 이미지 크기 최소화
- [ ] 빌드 캐시 최적화로 빌드 시간 단축
- [ ] 보안 스캔 도구를 활용한 이미지 보안 검증

### 과제 2: Kubernetes 고급 설정
- [ ] HorizontalPodAutoscaler 구성
- [ ] PodDisruptionBudget 설정
- [ ] NetworkPolicy를 통한 네트워크 보안 강화

### 과제 3: 클라우드 네이티브 패턴 적용
- [ ] Circuit Breaker 패턴 구현
- [ ] Distributed Tracing 설정 (Zipkin/Jaeger)
- [ ] Blue-Green 또는 Canary 배포 전략 구현

## 🎯 주요 포인트

### 성능 최적화 팁
1. **메모리 설정**: 네이티브 이미지는 JVM 대비 낮은 메모리 요구사항
2. **시작 시간**: Cold Start 성능 향상을 위한 프로파일 가이드 최적화
3. **이미지 크기**: Distroless 또는 Scratch 기반 이미지 사용

### 운영 고려사항
1. **로깅**: 구조화된 로깅과 로그 수집 파이프라인
2. **모니터링**: 메트릭 수집 및 알람 설정
3. **보안**: 이미지 스캔 및 런타임 보안 정책

### 트러블슈팅
1. **AOT 컴파일 이슈**: Reflection 설정 및 클래스패스 문제 해결
2. **리소스 문제**: 네이티브 이미지에서 리소스 접근 방법
3. **의존성 충돌**: 네이티브 이미지 호환성 문제 해결

## 🔗 참고 자료
- [Spring Boot Native Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
- [12 Factor App](https://12factor.net/)

## 📅 다음 주 예고
7주차에서는 WebFlux와 네이티브 이미지를 활용한 리액티브 마이크로서비스 구현에 대해 학습합니다.
    
