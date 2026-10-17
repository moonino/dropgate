plugins {
    id("dropgate.spring-boot-service")
}

dependencies {
    testImplementation(testFixtures(project(":common")))
    testImplementation(libs.wiremock)
}
