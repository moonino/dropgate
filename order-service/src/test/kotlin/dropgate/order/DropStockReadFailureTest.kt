package dropgate.order

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dropgate.order.configuration.RedisConfiguration
import dropgate.order.repository.DropStockReader
import dropgate.order.repository.RemainingStock
import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import java.net.ServerSocket
import java.time.Duration
import java.util.UUID
import kotlin.time.measureTime

private const val REDIS_PORT = 6379
private val COMMAND_TIMEOUT = Duration.ofMillis(200)

class DropStockReadFailureTest {
    @Test
    fun `단건은 연결 거절을 모름으로 돌려준다`() {
        withUnavailableRedis { reader -> assertThat(reader.readStock(UUID.randomUUID())).isEqualTo(RemainingStock.Unknown) }
    }

    @Test
    fun `목록은 연결 거절이면 모든 수량을 모름으로 돌려준다`() {
        withUnavailableRedis { reader -> assertThat(reader.readStocks(List(3) { UUID.randomUUID() }).values).containsOnly(RemainingStock.Unknown) }
    }

    @Test
    fun `단건은 응답 없는 Redis에서 200ms 근처에 모름으로 돌려준다`() {
        withUnresponsiveRedis { reader -> assertThat(reader.readStock(UUID.randomUUID())).isEqualTo(RemainingStock.Unknown) }
    }

    @Test
    fun `목록은 응답 없는 Redis에서 200ms 근처에 모든 수량을 모름으로 돌려준다`() {
        withUnresponsiveRedis { reader -> assertThat(reader.readStocks(List(3) { UUID.randomUUID() }).values).containsOnly(RemainingStock.Unknown) }
    }

    @Test
    fun `목록 장애는 항목 수와 무관하게 경고 한 번만 남긴다`() {
        val logger = LoggerFactory.getLogger(DropStockReader::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            withUnavailableRedis { it.readStocks(List(3) { UUID.randomUUID() }) }
            assertThat(appender.list).hasSize(1)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun withUnavailableRedis(read: (DropStockReader) -> Unit) {
        withReader("127.0.0.1", ServerSocket(0).use { it.localPort }, read)
    }

    private fun withUnresponsiveRedis(read: (DropStockReader) -> Unit) {
        RedisTestContainer().use { container ->
            container.start()
            withReader(container.host, container.getMappedPort(REDIS_PORT)) { reader ->
                reader.readStock(UUID.randomUUID())
                val docker = DockerClientFactory.instance().client()
                docker.pauseContainerCmd(container.containerId).exec()
                try {
                    val elapsed = measureTime { read(reader) }
                    println("재고 조회 명령 타임아웃: $elapsed, 설정: $COMMAND_TIMEOUT")
                    assertThat(elapsed.inWholeMilliseconds).isBetween(150L, 2000L)
                } finally {
                    docker.unpauseContainerCmd(container.containerId).exec()
                }
            }
        }
    }

    private fun withReader(host: String, port: Int, read: (DropStockReader) -> Unit) {
        val options = ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build())
        RedisConfiguration().redisClientOptionsCustomizer().customize(options)
        val client = LettuceClientConfiguration.builder().commandTimeout(COMMAND_TIMEOUT).clientOptions(options.build()).build()
        val connections = LettuceConnectionFactory(RedisStandaloneConfiguration(host, port), client).apply { afterPropertiesSet() }
        try {
            read(DropStockReader(StringRedisTemplate(connections)))
        } finally {
            connections.destroy()
        }
    }

    private class RedisTestContainer : GenericContainer<RedisTestContainer>("redis:7.4-alpine") {
        init {
            withExposedPorts(REDIS_PORT)
        }
    }
}
