package dropgate.order

import io.lettuce.core.ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.IntegerOutput
import io.lettuce.core.protocol.Command
import io.lettuce.core.protocol.CommandType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Import(PostgresTestcontainersConfiguration::class)
class RedisConnectionTest {
    @Autowired
    lateinit var connections: LettuceConnectionFactory

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Test
    fun `실제 클라이언트는 재연결을 유지하고 모든 쓰기 재전송과 단절 중 명령을 막는다`() {
        val options = connections.requiredNativeClient.options

        assertThat(options.isAutoReconnect).isTrue()
        assertThat(options.disconnectedBehavior).isEqualTo(REJECT_COMMANDS)
        listOf(CommandType.EVAL, CommandType.EVALSHA, CommandType.INCR).forEach { type ->
            val command = Command(type, IntegerOutput(StringCodec.UTF8))
            assertThat(options.replayFilter.test(command)).isTrue()
        }
    }

    @Test
    fun `Boot의 명령 타임아웃과 소켓 연결 타임아웃을 보존한다`() {
        val configuration = connections.clientConfiguration
        val options = connections.requiredNativeClient.options

        assertThat(configuration.commandTimeout).isEqualTo(Duration.ofMillis(200))
        assertThat(options.socketOptions.connectTimeout).isEqualTo(Duration.ofSeconds(1))
        assertThat(options.timeoutOptions.isTimeoutCommands).isTrue()
    }

    @Test
    fun `서비스 연결로 Redis 값을 쓰고 읽는다`() {
        val key = "connection-test:${UUID.randomUUID()}"

        redis.opsForValue().set(key, "connected", Duration.ofSeconds(10))

        assertThat(redis.opsForValue().get(key)).isEqualTo("connected")
        redis.delete(key)
    }
}
