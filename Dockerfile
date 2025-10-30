# ================================
# Stage 1: Cache Gradle dependencies 🧩
# ================================
FROM gradle:jdk22 AS cache
WORKDIR /home/gradle/app

# Copy Gradle build files (for dependency caching)
COPY build.gradle* gradle.properties gradle/ ./

# Use a local Gradle home for caching
ENV GRADLE_USER_HOME=/home/gradle/.gradle

# Pre-download and cache dependencies (ignore build/test failures)
RUN gradle build -x test --no-daemon || true

# ================================
# Stage 2: Build Application 🏗️
# ================================
FROM gradle:jdk22 AS build
WORKDIR /home/gradle/app

# Copy cached Gradle home
COPY --from=cache /home/gradle/.gradle /home/gradle/.gradle

# Copy the full source
COPY . .

# Build fat JAR (Ktor supports shadowJar or buildFatJar)
RUN gradle buildFatJar --no-daemon

# ================================
# Stage 3: Runtime 🚀
# ================================
FROM amazoncorretto:22 AS runtime
WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /home/gradle/app/build/libs/*.jar ./onedrop-backend.jar

# Expose app port
EXPOSE 8080

# Run the app
ENTRYPOINT ["java","-jar","onedrop-backend.jar"]
