plugins {
    `java-library`
    `java-test-fixtures`
    id("dropgate.kotlin-conventions")
}

dependencies {
    compileOnly(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.security.oauth2.jose)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.wiremock)
}
