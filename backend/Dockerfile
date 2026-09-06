# Multi-stage: the build toolchain never ships in the runtime image.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Wrapper and build scripts first: dependency resolution is cached as long as
# these do not change, so ordinary source edits skip the download.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Spring Boot layered jars: dependencies, which rarely change, land in an
# earlier image layer than application classes, which change every build.
RUN java -Djarmode=tools -jar build/libs/*.jar extract --launcher --layers --destination extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Unprivileged: a container process has no business running as root.
RUN addgroup -S elearning && adduser -S -G elearning elearning
USER elearning

COPY --from=build --chown=elearning:elearning /build/extracted/dependencies/ ./
COPY --from=build --chown=elearning:elearning /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=elearning:elearning /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=elearning:elearning /build/extracted/application/ ./

EXPOSE 8081

# Container-aware heap sizing beats a hardcoded -Xmx that ignores the cgroup limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

# JarLauncher, not `-jar`: a layered extract leaves a directory layout, not a jar.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
