# 로컬에서 빌드

#./gradlew nativeCompile

FROM ghcr.io/graalvm/graalvm-community:21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew -Dorg.gradle.jvmargs="-Xmx4g" nativeCompile
## 바이너리 정보 출력
RUN file build/native/nativeCompile/native-demo || echo "file command not found"

# 빌드된 바이너리만 포함하는 Docker 이미지
FROM --platform=linux/arm64 alpine:latest
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/native-demo .
EXPOSE 8080
ENTRYPOINT ["./native-demo"]
