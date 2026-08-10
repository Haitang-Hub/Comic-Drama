# ---- 多阶段构建：Spring Boot 微服务 ----
# Usage: docker build --build-arg SERVICE=comic-gateway -f docker/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS builder

ARG SERVICE=comic-gateway
WORKDIR /build

COPY pom.xml .
COPY comic-common/pom.xml comic-common/
COPY ${SERVICE}/pom.xml ${SERVICE}/

RUN mvn dependency:go-offline -pl ${SERVICE} -am -DskipTests -q || true

COPY comic-common/ comic-common/
COPY ${SERVICE}/ ${SERVICE}/

RUN mvn clean package -pl ${SERVICE} -am -DskipTests -q

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine

ARG SERVICE=comic-gateway
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m"

COPY --from=builder /build/${SERVICE}/target/${SERVICE}.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]