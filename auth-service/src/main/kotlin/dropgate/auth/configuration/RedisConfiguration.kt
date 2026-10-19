package dropgate.auth.configuration

import dropgate.common.authentication.RedisRevokedAccessTokens
import dropgate.common.authentication.RevokedAccessTokens
import io.lettuce.core.ClientOptions.DisconnectedBehavior
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration(proxyBeanMethods = false)
class RedisConfiguration {
    @Bean
    fun revokedAccessTokens(redis: StringRedisTemplate): RevokedAccessTokens = RedisRevokedAccessTokens(redis)

    @Bean
    fun redisClientOptions(): LettuceClientOptionsBuilderCustomizer = LettuceClientOptionsBuilderCustomizer {
        it.autoReconnect(true).replayFilter { true }.disconnectedBehavior(DisconnectedBehavior.REJECT_COMMANDS)
    }
}
