plugins {
    `java-library`
    id("dropgate.kotlin-conventions")
}

dependencies {
    compileOnly(libs.spring.boot.starter.webmvc)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.jackson.module.kotlin)
}
