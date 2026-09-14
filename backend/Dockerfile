FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --home-dir /app --create-home --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/target/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:InitialRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError"

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
