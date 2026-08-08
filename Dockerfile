# ── build stage ──────────────────────────────────────
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src/ src/
RUN mvn -B package -DskipTests

# ── runtime stage ────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S labwatch && adduser -S labwatch -G labwatch
WORKDIR /app
COPY --from=build /build/target/labwatch.jar .
USER labwatch
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s \
  CMD wget -qO- http://localhost:8080/healthz || exit 1
ENTRYPOINT ["java", "-jar", "labwatch.jar"]
