plugins {
    id("dropgate.spring-boot-service")
}

dependencies {
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.security.oauth2.client)
    implementation(libs.spring.security.oauth2.jose)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.wiremock)
}
