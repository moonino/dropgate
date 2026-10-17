package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import java.util.UUID

class PurchaseTest : OrderApiIntegrationTest() {
    @Test
    fun `OPEN 드롭을 사면 201과 Location과 CONFIRMED 주문을 돌려준다`() {
        val dropId = insertDropWithStock()

        purchase(dropId, UUID.randomUUID())
            .expectStatus().isCreated
            .expectHeader().valueMatches(HttpHeaders.LOCATION, "/api/orders/[0-9a-f-]{36}")
            .expectBody()
            .jsonPath("$.dropId").isEqualTo(dropId.toString())
            .jsonPath("$.dropName").isEqualTo(DROP_NAME)
            .jsonPath("$.quantity").isEqualTo(1)
            .jsonPath("$.status").isEqualTo("CONFIRMED")
            .jsonPath("$.confirmedAt").value<String> { assertThat(it).matches(UTC_MILLIS_REGEX) }
            .jsonPath("$.canceledAt").isEmpty
    }

    @Test
    fun `구매하면 남은 수량이 1 줄어든다`() {
        val dropId = insertDropWithStock(totalQuantity = 10)

        purchase(dropId, UUID.randomUUID()).expectStatus().isCreated

        assertThat(findRemainingQuantity(dropId)).isEqualTo(9)
    }

    @Test
    fun `구매하면 원장에 확정 시각과 같은 시각의 CONFIRMED 항목을 남긴다`() {
        val dropId = insertDropWithStock()
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)

        val entry = jdbc.sql(
            """
            SELECT l.drop_id, l.user_id, l.entry_type, l.occurred_at = o.confirmed_at AS same_time
            FROM orders.order_ledger l JOIN orders.orders o ON o.id = l.order_id
            WHERE l.order_id = :orderId
            """,
        ).param("orderId", orderId)
            .query { rs, _ -> listOf(rs.getObject("drop_id"), rs.getObject("user_id"), rs.getString("entry_type"), rs.getBoolean("same_time")) }
            .single()
        assertThat(entry).containsExactly(dropId, userId, "CONFIRMED", true)
    }

    @Test
    fun `구매하면 outbox에 order confirmed 이벤트를 PENDING으로 남긴다`() {
        val orderId = purchaseOrderId(insertDropWithStock(), UUID.randomUUID())

        val event = jdbc.sql("SELECT aggregate_type, aggregate_id, event_type, status FROM orders.outbox")
            .query { rs, _ -> listOf(rs.getString("aggregate_type"), rs.getObject("aggregate_id"), rs.getString("event_type"), rs.getString("status")) }
            .single()
        assertThat(event).containsExactly("order", orderId, "order.confirmed", "PENDING")
    }

    @Test
    fun `order confirmed 페이로드는 이벤트 명세 v1 필드를 담는다`() {
        val dropId = insertDropWithStock()
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)

        val payload = OutboxPayload.findByEventType(jdbc, "order.confirmed")
        assertThat(payload.readKeys()).containsExactlyInAnyOrder("orderId", "dropId", "dropName", "userId", "quantity", "status", "confirmedAt", "canceledAt")
        assertThat(payload.readText("orderId")).isEqualTo(orderId.toString())
        assertThat(payload.readText("dropId")).isEqualTo(dropId.toString())
        assertThat(payload.readText("dropName")).isEqualTo(DROP_NAME)
        assertThat(payload.readText("userId")).isEqualTo(userId.toString())
        assertThat(payload.readNumber("quantity")).isEqualTo(1)
        assertThat(payload.readText("status")).isEqualTo("CONFIRMED")
        assertThat(payload.readText("confirmedAt")).matches(UTC_MILLIS_REGEX)
        assertThat(payload.isNull("canceledAt")).isTrue()
    }

    @Test
    fun `남은 수량이 0인 OPEN 드롭은 409 SOLD_OUT을 돌려준다`() {
        val dropId = insertDropWithStock(totalQuantity = 1, remainingQuantity = 0)

        expectConflict(dropId, "SOLD_OUT")
    }

    @Test
    fun `SOLD_OUT 드롭은 409 SOLD_OUT을 돌려준다`() {
        val dropId = insertDropWithStock(status = "SOLD_OUT", totalQuantity = 1, remainingQuantity = 0)

        expectConflict(dropId, "SOLD_OUT")
    }

    @ParameterizedTest
    @ValueSource(strings = ["SCHEDULED", "CLOSED"])
    fun `SCHEDULED와 CLOSED 드롭은 409 DROP_NOT_OPEN을 돌려준다`(status: String) {
        val dropId = insertDropWithStock(status = status)

        expectConflict(dropId, "DROP_NOT_OPEN")
    }

    @Test
    fun `없는 드롭을 사면 404 DROP_NOT_FOUND를 돌려준다`() {
        purchase(UUID.randomUUID(), UUID.randomUUID())
            .expectStatus().isNotFound
            .expectBody().jsonPath("$.code").isEqualTo("DROP_NOT_FOUND")
    }

    @Test
    fun `같은 사용자가 다시 사면 409 ALREADY_PURCHASED를 돌려준다`() {
        val dropId = insertDropWithStock()
        val userId = UUID.randomUUID()
        purchase(dropId, userId).expectStatus().isCreated

        expectConflict(dropId, "ALREADY_PURCHASED", userId)
    }

    @Test
    fun `ALREADY_PURCHASED로 거절하면 차감도 되돌린다`() {
        val dropId = insertDropWithStock(totalQuantity = 10)
        val userId = UUID.randomUUID()
        purchase(dropId, userId).expectStatus().isCreated

        purchase(dropId, userId).expectStatus().isEqualTo(409)

        assertThat(findRemainingQuantity(dropId)).isEqualTo(9)
    }

    @Test
    fun `이미 산 사용자가 품절 뒤 다시 사면 차감이 먼저라 SOLD_OUT을 돌려준다`() {
        val dropId = insertDropWithStock(totalQuantity = 1)
        val userId = UUID.randomUUID()
        purchase(dropId, userId).expectStatus().isCreated

        expectConflict(dropId, "SOLD_OUT", userId)
    }

    @Test
    fun `거절한 구매는 주문과 원장과 outbox를 남기지 않는다`() {
        val dropId = insertDropWithStock(totalQuantity = 1, remainingQuantity = 0)

        purchase(dropId, UUID.randomUUID()).expectStatus().isEqualTo(409)

        assertThat(listOf(countRows("orders"), countRows("order_ledger"), countRows("outbox"))).containsOnly(0L)
    }

    @Test
    fun `dropId가 UUID가 아니면 400 VALIDATION_FAILED를 돌려준다`() {
        http.post().uri("/api/drops/not-a-uuid/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.issueAccessToken()}")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("dropId")
    }

    @Test
    fun `토큰 없이 사면 401 UNAUTHENTICATED를 돌려준다`() {
        http.post().uri("/api/drops/${insertDropWithStock()}/orders").exchange()
            .expectStatus().isUnauthorized
            .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
    }

    private fun expectConflict(dropId: UUID, code: String, userId: UUID = UUID.randomUUID()) {
        purchase(dropId, userId)
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo(code)
    }
}
