package dropgate.order

import dropgate.order.configuration.RedisConfiguration
import io.lettuce.core.ClientOptions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.testcontainers.containers.GenericContainer
import java.time.Duration
import java.util.UUID

private const val REDIS_PORT = 6379
private const val RECONNECT_READ_ATTEMPTS = 50
private const val RECONNECT_READ_DELAY_MILLIS = 100L
private val COMMAND_TIMEOUT = Duration.ofSeconds(2)

class RedisCommandReplayTest {
    @Test
    fun `응답이 유실된 Lua는 재연결 뒤 다시 실행되지 않는다`() {
        RedisTestContainer().use { redis ->
            redis.start()
            RedisReplyDropper(redis.host, redis.getMappedPort(REDIS_PORT)).use { proxy ->
                val connections = createConnectionFactory(proxy.port)
                try {
                    val template = StringRedisTemplate(connections)
                    val key = "replay-test:${UUID.randomUUID()}"
                    val script = DefaultRedisScript("return redis.call('INCR', KEYS[1])", Long::class.javaObjectType)
                    template.execute(script, listOf(key))
                    template.delete(key)
                    proxy.dropNextReply()

                    assertThatThrownBy { template.execute(script, listOf(key)) }.isInstanceOf(DataAccessException::class.java)
                    assertThat(readAfterReconnect(template, key)).isEqualTo("1")
                } finally {
                    connections.destroy()
                }
            }
        }
    }

    private fun createConnectionFactory(port: Int): LettuceConnectionFactory {
        val client = LettuceClientConfiguration.builder().commandTimeout(COMMAND_TIMEOUT).clientOptions(createClientOptions()).build()
        return LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port), client).apply { afterPropertiesSet() }
    }

    private fun createClientOptions(): ClientOptions {
        val options = ClientOptions.builder()
        RedisConfiguration().redisClientOptionsCustomizer().customize(options)
        return options.build()
    }

    private fun readAfterReconnect(template: StringRedisTemplate, key: String): String {
        var lastFailure: DataAccessException? = null
        repeat(RECONNECT_READ_ATTEMPTS) {
            try {
                return template.opsForValue().get(key) ?: error("실행한 Lua의 카운터 키가 없습니다: $key")
            } catch (exception: DataAccessException) {
                lastFailure = exception
                Thread.sleep(RECONNECT_READ_DELAY_MILLIS)
            }
        }
        throw IllegalStateException("Redis 재연결 뒤 카운터를 읽지 못했습니다", lastFailure)
    }

    private class RedisTestContainer : GenericContainer<RedisTestContainer>("redis:7.4-alpine") {
        init {
            withExposedPorts(REDIS_PORT)
        }
    }
}
