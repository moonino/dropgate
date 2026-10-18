package dropgate.order.configuration

import dropgate.common.UuidV7Generator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class IdentifierConfiguration {
    @Bean
    fun uuidV7Generator(clock: Clock): UuidV7Generator = UuidV7Generator(clock, SecureRandom())
}
