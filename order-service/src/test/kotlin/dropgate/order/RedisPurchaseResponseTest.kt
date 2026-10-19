package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RedisPurchaseResponseTest : RedisOrderApiIntegrationTest() {
    @Test
    fun `구매는 201을 반환하고 DB 기준선 수량을 유지하며 Redis 재고를 조회한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()

        purchase(dropId, userId).expectStatus().isCreated.expectBody().jsonPath("$.status").isEqualTo("CONFIRMED")

        assertThat(findRemainingQuantity(dropId)).isEqualTo(1)
        assertThat(readStock(dropId)).isEqualTo("0")
        assertThat(redis.opsForSet().members("drop:$dropId:buyers")).containsExactly(userId.toString())
        assertThat(countRows("orders")).isEqualTo(1)
        assertThat(findDropStatus(dropId)).isEqualTo("SOLD_OUT")
        http.get().uri("/api/drops/$dropId").exchange().expectStatus().isOk.expectBody().jsonPath("$.remainingQuantity").isEqualTo(0)
    }

    @Test
    fun `품절 뒤 기존 구매자는 ALREADY_PURCHASED이고 신규 사용자는 SOLD_OUT이다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        purchase(dropId, userId).expectStatus().isCreated

        purchase(dropId, userId).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("ALREADY_PURCHASED")
        purchase(dropId, UUID.randomUUID()).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("SOLD_OUT")
    }

    @Test
    fun `품절 주문을 취소하면 200과 복구된 재고로 다른 사용자가 살 수 있다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)

        cancel(orderId, userId).expectStatus().isOk.expectBody().jsonPath("$.status").isEqualTo("CANCELED")

        purchase(dropId, UUID.randomUUID()).expectStatus().isCreated
    }

    @Test
    fun `취소한 사용자의 재구매는 원래 재고를 유지하며 중복으로 거절한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        cancel(purchaseOrderId(dropId, userId), userId).expectStatus().isOk

        purchase(dropId, userId).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("ALREADY_PURCHASED")

        assertThat(readStock(dropId)).isEqualTo("1")
    }

    @Test
    fun `초기화하지 않은 드롭은 409 DROP_NOT_OPEN이다`() {
        purchase(insertDropWithStock(), UUID.randomUUID()).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("DROP_NOT_OPEN")
    }

    @Test
    fun `없는 드롭은 404 DROP_NOT_FOUND다`() {
        purchase(UUID.randomUUID(), UUID.randomUUID()).expectStatus().isNotFound.expectBody().jsonPath("$.code").isEqualTo("DROP_NOT_FOUND")
    }

    @Test
    fun `마감한 OPEN 드롭은 Redis 메타데이터로 거절한다`() {
        val now = Instant.now()
        val dropId = insertDropWithStock(openAt = now.minusSeconds(7200), closeAt = now.minusSeconds(3600))
        stocks.initialize(drops.findById(dropId).orElseThrow())

        purchase(dropId, UUID.randomUUID()).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("DROP_NOT_OPEN")
    }
}
