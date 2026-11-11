# ============================================================
# RelayOps 一体化镜像：前端构建产物打进后端 jar，单容器同时提供 API 与控制台
# 相比前后端分开部署：少一个 Nginx 容器、少一层反代、内存占用更低
# ============================================================
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
# 前端产物直接作为静态资源打进 jar
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R appuser:appuser /app
USER 10001

EXPOSE 8080
ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:MaxRAMPercentage=60 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
