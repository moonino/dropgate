package dropgate.order

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.DropEntity
import dropgate.order.entity.DropStatus
import dropgate.order.repository.DropStockRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(PostgresTestcontainersConfiguration::class)
class StockRestorationTest @Autowired constructor(private val stocks: DropStockRepository, private val redis: StringRedisTemplate) {
    @Test
    fun `DB 실패 보상은 재고와 구매자 기록을 되돌린다`() {
        val (dropId, userId) = reserve(totalQuantity = 2)

        stocks.compensate(dropId, userId)

        assertRestored(dropId, 2)
    }

    @Test
    fun `마지막 재고의 DB 실패 보상은 품절 메타데이터를 연다`() {
        val (dropId, userId) = reserve()

        stocks.compensate(dropId, userId)

        assertRestored(dropId, 1)
    }

    @Test
    fun `취소는 재고와 구매자를 되돌리고 품절 메타데이터를 연다`() {
        val (dropId, userId) = reserve()

        stocks.cancel(dropId, userId)

        assertRestored(dropId, 1)
    }

    @Test
    fun `이미 열려 있는 드롭의 취소도 성공한다`() {
        val (dropId, userId) = reserve(totalQuantity = 2)

        stocks.cancel(dropId, userId)

        assertRestored(dropId, 2)
    }

    @ParameterizedTest
    @ValueSource(strings = ["stock", "meta"])
    fun `키가 유실되면 DB 실패 보상은 모든 키와 만료 시각을 그대로 둔다`(missing: String) {
        val (dropId, userId) = reserve()
        redis.delete("drop:$dropId:$missing")
        val before = captureKeyStates(dropId)

        assertThatThrownBy { stocks.compensate(dropId, userId) }.isInstanceOfSatisfying(DropgateException::class.java) {
            assertThat(it.errorCode).isEqualTo(ErrorCode.STOCK_UNAVAILABLE)
        }

        assertThat(captureKeyStates(dropId)).isEqualTo(before)
    }

    @ParameterizedTest
    @ValueSource(strings = ["stock", "meta"])
    fun `키가 유실되면 취소는 모든 키와 만료 시각을 그대로 둔다`(missing: String) {
        val (dropId, userId) = reserve()
        redis.delete("drop:$dropId:$missing")
        val before = captureKeyStates(dropId)

        assertThatThrownBy { stocks.cancel(dropId, userId) }.isInstanceOfSatisfying(DropgateException::class.java) {
            assertThat(it.errorCode).isEqualTo(ErrorCode.STOCK_UNAVAILABLE)
        }

        assertThat(captureKeyStates(dropId)).isEqualTo(before)
    }

    private fun reserve(totalQuantity: Int = 1): Pair<UUID, UUID> {
        val now = Instant.now()
        val drop = DropEntity(UUID.randomUUID(), "drop", totalQuantity, totalQuantity, now, now.plusSeconds(3600), DropStatus.OPEN, 50, UUID.randomUUID())
        val userId = UUID.randomUUID()
        stocks.initialize(drop)
        stocks.purchase(drop.id, userId, now)
        return drop.id to userId
    }

    private fun assertRestored(dropId: UUID, quantity: Int) {
        assertThat(redis.opsForValue().get("drop:$dropId:stock")).isEqualTo(quantity.toString())
        assertThat(redis.opsForSet().members("drop:$dropId:buyers")).isEmpty()
        assertThat(redis.opsForHash<String, String>().get("drop:$dropId:meta", "status")).isEqualTo("OPEN")
    }

    private fun captureKeyStates(dropId: UUID): Map<String, KeySnapshot> {
        val expiration = DefaultRedisScript("return redis.call('PEXPIRETIME', KEYS[1])", Long::class.java)
        return listOf("stock", "meta", "buyers").associateWith { suffix ->
            val key = "drop:$dropId:$suffix"
            KeySnapshot(redis.dump(key)?.toList() ?: emptyList(), checkNotNull(redis.execute(expiration, listOf(key))))
        }
    }

    private data class KeySnapshot(val value: List<Byte>, val expiresAt: Long)
}
