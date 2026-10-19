package dropgate.auth

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer

@TestConfiguration(proxyBeanMethods = false)
class RedisTestcontainersConfiguration {
    @Bean
    @ServiceConnection(name = "redis")
    fun redis(): GenericContainer<*> = GenericContainer<Nothing>("redis:7.4-alpine").apply { withExposedPorts(6379) }
}
