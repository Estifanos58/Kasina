# Build stage
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root group and user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create uploads directory and set permissions
RUN mkdir -p /app/uploads/files && chown -R appuser:appgroup /app

USER appuser

# Copy JAR from build stage
COPY --from=builder --chown=appuser:appgroup /app/target/ChatAPP-0.0.1-SNAPSHOT.jar app.jar

# Readiness health check (verifies MongoDB connectivity and storage availability)
HEALTHCHECK --interval=20s --timeout=10s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/api/health/ready || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
