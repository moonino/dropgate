plugins {
    id("dropgate.kotlin-conventions")
    id("org.springframework.boot")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation(libs.findLibrary("spring-boot-starter-webmvc").get())
    implementation(libs.findLibrary("spring-boot-starter-actuator").get())
    implementation(libs.findLibrary("kotlin-reflect").get())
    implementation(libs.findLibrary("jackson-module-kotlin").get())
    implementation(project(":common"))
    testImplementation(libs.findLibrary("spring-boot-starter-webmvc-test").get())
}
