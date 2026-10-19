package dropgate.order

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.configuration.OrderProperties
import dropgate.order.entity.DropEntity
import dropgate.order.entity.DropStatus
import dropgate.order.repository.DropStockRepository
import dropgate.order.repository.StockReservation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(PostgresTestcontainersConfiguration::class)
class StockReservationTest @Autowired constructor(private val stocks: DropStockRepository, private val redis: StringRedisTemplate) {
    @Test
    fun `구매는 재고 차감과 구매자 기록과 보존 기한을 함께 적용한다`() {
        val drop = initializeDrop(totalQuantity = 2)
        val userId = UUID.randomUUID()

        assertThat(reserve(drop, userId)).isEqualTo(StockReservation.Reserved(1))

        assertThat(redis.opsForValue().get("drop:${drop.id}:stock")).isEqualTo("1")
        assertThat(redis.opsForSet().members("drop:${drop.id}:buyers")).containsExactly(userId.toString())
        val expiration = DefaultRedisScript("return redis.call('PEXPIRETIME', KEYS[1])", Long::class.java)
        assertThat(redis.execute(expiration, listOf("drop:${drop.id}:buyers"))).isEqualTo(drop.closeAt.plus(Duration.ofDays(7)).toEpochMilli())
    }

    @Test
    fun `마지막 구매는 메타데이터를 품절로 바꾼다`() {
        val drop = initializeDrop()

        assertThat(reserve(drop)).isEqualTo(StockReservation.Reserved(0))

        assertThat(redis.opsForHash<String, String>().get("drop:${drop.id}:meta", "status")).isEqualTo("SOLD_OUT")
    }

    @Test
    fun `초기화 전 구매는 판매 중이 아님으로 거절한다`() {
        assertThat(stocks.purchase(UUID.randomUUID(), UUID.randomUUID(), Instant.now())).isEqualTo(StockReservation.Rejected(ErrorCode.DROP_NOT_OPEN))
    }

    @Test
    fun `메타데이터가 OPEN이어도 마감 시각이면 거절한다`() {
        val closeAt = Instant.now()
        val drop = initializeDrop(closeAt = closeAt)

        assertThat(stocks.purchase(drop.id, UUID.randomUUID(), closeAt)).isEqualTo(StockReservation.Rejected(ErrorCode.DROP_NOT_OPEN))
    }

    @Test
    fun `품절 뒤 기존 구매자와 신규 사용자를 구분한다`() {
        val drop = initializeDrop()
        val buyer = UUID.randomUUID()
        reserve(drop, buyer)

        assertThat(reserve(drop, buyer)).isEqualTo(StockReservation.Rejected(ErrorCode.ALREADY_PURCHASED))
        assertThat(reserve(drop)).isEqualTo(StockReservation.Rejected(ErrorCode.SOLD_OUT))
    }

    @Test
    fun `입장 확인을 끄면 입장 키를 소비하지 않는다`() {
        val drop = initializeDrop()
        val userId = UUID.randomUUID()
        val admission = "drop:${drop.id}:admit:$userId"
        redis.opsForValue().set(admission, "1")

        assertThat(reserve(drop, userId)).isEqualTo(StockReservation.Reserved(0))

        assertThat(redis.hasKey(admission)).isTrue()
    }

    @Test
    fun `입장 확인을 켜면 입장 키가 없는 구매를 거절한다`() {
        val drop = initializeDrop()
        val admittedStocks = DropStockRepository(redis, OrderProperties(queue = OrderProperties.Queue(enabled = true)))

        assertThat(admittedStocks.purchase(drop.id, UUID.randomUUID(), Instant.now())).isEqualTo(StockReservation.Rejected(ErrorCode.NOT_ADMITTED))
        assertThat(redis.opsForValue().get("drop:${drop.id}:stock")).isEqualTo("1")
    }

    @Test
    fun `입장 키를 소비한 구매자의 재요청은 구매자 검사 전에 입장 확인으로 거절한다`() {
        val drop = initializeDrop()
        val userId = UUID.randomUUID()
        val admission = "drop:${drop.id}:admit:$userId"
        val admittedStocks = DropStockRepository(redis, OrderProperties(queue = OrderProperties.Queue(enabled = true)))
        redis.opsForValue().set(admission, "1")

        assertThat(admittedStocks.purchase(drop.id, userId, Instant.now())).isEqualTo(StockReservation.Reserved(0))

        assertThat(redis.hasKey(admission)).isFalse()
        assertThat(admittedStocks.purchase(drop.id, userId, Instant.now())).isEqualTo(StockReservation.Rejected(ErrorCode.NOT_ADMITTED))
    }

    @Test
    fun `서로 다른 사용자가 경합해도 재고 수만큼만 구매한다`() {
        val quantity = 10
        val drop = initializeDrop(totalQuantity = quantity)

        val results = ConcurrentRequests.run(30) { reserve(drop) }

        assertThat(results.filterIsInstance<StockReservation.Reserved>()).hasSize(quantity)
        assertThat(redis.opsForValue().get("drop:${drop.id}:stock")).isEqualTo("0")
        assertThat(redis.opsForSet().size("drop:${drop.id}:buyers")).isEqualTo(quantity.toLong())
    }

    @Test
    fun `같은 사용자의 동시 구매는 하나만 성공한다`() {
        val drop = initializeDrop(totalQuantity = 10)
        val buyer = UUID.randomUUID()

        val results = ConcurrentRequests.run(10) { reserve(drop, buyer) }

        assertThat(results.filterIsInstance<StockReservation.Reserved>()).hasSize(1)
        assertThat(results.filterIsInstance<StockReservation.Rejected>().map { it.errorCode }).containsOnly(ErrorCode.ALREADY_PURCHASED)
    }

    @Test
    fun `Redis 구매 명령 실패는 사용자와 드롭 맥락을 담는다`() {
        val dropId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        redis.opsForValue().set("drop:$dropId:meta", "invalid", Duration.ofSeconds(10))

        assertThatThrownBy { stocks.purchase(dropId, userId, Instant.now()) }.isInstanceOfSatisfying(DropgateException::class.java) { failure ->
            assertThat(failure.errorCode).isEqualTo(ErrorCode.STOCK_UNAVAILABLE)
            assertThat(failure).hasMessageContaining(dropId.toString()).hasMessageContaining(userId.toString())
        }
    }

    private fun reserve(drop: DropEntity, userId: UUID = UUID.randomUUID()): StockReservation = stocks.purchase(drop.id, userId, Instant.now())

    private fun initializeDrop(totalQuantity: Int = 1, closeAt: Instant = Instant.now().plusSeconds(3600)): DropEntity {
        val drop = DropEntity(UUID.randomUUID(), "drop", totalQuantity, totalQuantity, closeAt.minusSeconds(3600), closeAt, DropStatus.OPEN, 50, UUID.randomUUID())
        stocks.initialize(drop)
        return drop
    }
}
