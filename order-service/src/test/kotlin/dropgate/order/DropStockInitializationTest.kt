package dropgate.order

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.DropEntity
import dropgate.order.entity.DropStatus
import dropgate.order.repository.DropStockRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(PostgresTestcontainersConfiguration::class)
class DropStockInitializationTest {
    @Autowired
    lateinit var stocks: DropStockRepository

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var connections: LettuceConnectionFactory

    @Test
    fun `초기화는 전체 수량과 판매 메타데이터를 만든다`() {
        val drop = createDrop()

        stocks.initialize(drop)

        assertThat(redis.opsForValue().get("drop:${drop.id}:stock")).isEqualTo("10")
        assertThat(redis.opsForHash<String, String>().entries("drop:${drop.id}:meta")).isEqualTo(
            mapOf("status" to "OPEN", "open_at" to drop.openAt.toEpochMilli().toString(), "close_at" to drop.closeAt.toEpochMilli().toString(), "rate" to "50"),
        )
    }

    @Test
    fun `두 키는 마감 시각에서 7일 뒤에 만료된다`() {
        val drop = createDrop()

        stocks.initialize(drop)

        val expirationScript = DefaultRedisScript("return redis.call('PEXPIRETIME', KEYS[1])", Long::class.java)
        listOf("stock", "meta").forEach { suffix ->
            val expiration = redis.execute(expirationScript, listOf("drop:${drop.id}:$suffix"))
            assertThat(expiration).isEqualTo(drop.closeAt.plus(Duration.ofDays(7)).toEpochMilli())
        }
    }

    @Test
    fun `이미 초기화한 드롭의 재고와 메타데이터를 덮어쓰지 않는다`() {
        val drop = createDrop()
        stocks.initialize(drop)
        redis.opsForValue().decrement("drop:${drop.id}:stock")
        redis.opsForHash<String, String>().put("drop:${drop.id}:meta", "status", "SOLD_OUT")

        stocks.initialize(drop)

        assertThat(redis.opsForValue().get("drop:${drop.id}:stock")).isEqualTo("9")
        assertThat(redis.opsForHash<String, String>().get("drop:${drop.id}:meta", "status")).isEqualTo("SOLD_OUT")
    }

    @Test
    fun `메타데이터가 없어도 이미 있는 재고 수량은 유지한다`() {
        val drop = createDrop()
        redis.opsForValue().set("drop:${drop.id}:stock", "3")

        stocks.initialize(drop)

        assertThat(redis.opsForValue().get("drop:${drop.id}:stock")).isEqualTo("3")
        assertThat(redis.hasKey("drop:${drop.id}:meta")).isTrue()
    }

    @Test
    fun `Redis 연결 실패를 드롭 맥락을 담은 재고 불가 오류로 바꾼다`() {
        val port = ServerSocket(0).use { it.localPort }
        val unavailable = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port), connections.clientConfiguration)
        unavailable.afterPropertiesSet()
        val drop = createDrop()
        try {
            val repository = DropStockRepository(StringRedisTemplate(unavailable))

            assertThatThrownBy { repository.initialize(drop) }.isInstanceOfSatisfying(DropgateException::class.java) { failure ->
                assertThat(failure.errorCode).isEqualTo(ErrorCode.STOCK_UNAVAILABLE)
                assertThat(failure).hasMessageContaining(drop.id.toString()).hasCauseInstanceOf(org.springframework.dao.DataAccessException::class.java)
            }
        } finally {
            unavailable.destroy()
        }
    }

    private fun createDrop(): DropEntity {
        val openAt = Instant.now()
        return DropEntity(UUID.randomUUID(), "drop", 10, 10, openAt, openAt.plusSeconds(3600), DropStatus.OPEN, 50, UUID.randomUUID())
    }
}
