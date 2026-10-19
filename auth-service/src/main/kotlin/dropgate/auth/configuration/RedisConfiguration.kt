package dropgate.auth.configuration

import io.lettuce.core.ClientOptions.DisconnectedBehavior
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class RedisConfiguration {
    @Bean
    fun redisClientOptions(): LettuceClientOptionsBuilderCustomizer = LettuceClientOptionsBuilderCustomizer {
        it.autoReconnect(true).replayFilter { true }.disconnectedBehavior(DisconnectedBehavior.REJECT_COMMANDS)
    }
}
