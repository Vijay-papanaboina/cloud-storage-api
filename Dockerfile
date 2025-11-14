# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:25-jre-alpine

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy the JAR file from build stage
COPY --from=build /app/target/cloud-storage-api-0.0.1-SNAPSHOT.jar app.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose port (default 8000, but Render will override with PORT env variable)
EXPOSE 8000

# Health check (Render can also configure health checks separately)
# Install wget for health check
USER root
RUN apk add --no-cache wget
# Create health check script that uses PORT env variable
RUN echo '#!/bin/sh' > /app/healthcheck.sh && \
    echo 'PORT=${PORT:-8000}' >> /app/healthcheck.sh && \
    echo 'wget --no-verbose --tries=1 --spider http://localhost:$PORT/api/health || exit 1' >> /app/healthcheck.sh && \
    chmod +x /app/healthcheck.sh && \
    chown spring:spring /app/healthcheck.sh
USER spring:spring

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD /app/healthcheck.sh

# Set JVM options for production
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
# Render provides PORT environment variable, so we use it if available, otherwise default to 8000
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8000} -jar app.jar"]

