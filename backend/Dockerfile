# ============ 构建阶段 ============
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷贝 pom，利用镜像层缓存预下载依赖
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -q -DskipTests package

# ============ 运行阶段 ============
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar app.jar

# 版本库与导出目录（compose 会挂载出来）
RUN mkdir -p /app/data/versions /app/data/exports

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
