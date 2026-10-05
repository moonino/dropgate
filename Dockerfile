FROM eclipse-temurin:21-jdk AS build

WORKDIR /build
ARG MODULE

COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/
COPY common/build.gradle.kts common/
COPY auth-service/build.gradle.kts auth-service/
COPY order-service/build.gradle.kts order-service/
COPY notification-service/build.gradle.kts notification-service/
COPY settlement-batch/build.gradle.kts settlement-batch/
RUN ./gradlew :${MODULE}:dependencies --no-daemon

COPY common/ common/
COPY ${MODULE}/src/ ${MODULE}/src/
RUN ./gradlew :${MODULE}:bootJar --no-daemon \
    && cp ${MODULE}/build/libs/*.jar /build/app.jar

FROM eclipse-temurin:21-jre AS runtime

RUN groupadd --system dropgate \
    && useradd --system --gid dropgate --no-create-home dropgate
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER dropgate:dropgate
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
