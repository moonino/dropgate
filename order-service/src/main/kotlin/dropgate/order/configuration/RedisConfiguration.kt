package dropgate.order.configuration

import io.lettuce.core.ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class RedisConfiguration {
    @Bean
    fun redisClientOptionsCustomizer(): LettuceClientOptionsBuilderCustomizer = LettuceClientOptionsBuilderCustomizer { options ->
        options.autoReconnect(true)
            .replayFilter { true }
            .disconnectedBehavior(REJECT_COMMANDS)
    }
}
