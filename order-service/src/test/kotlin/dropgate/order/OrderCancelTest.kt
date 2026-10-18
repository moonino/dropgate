package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Instant
import java.util.UUID

class OrderCancelTest : OrderApiIntegrationTest() {
    @Test
    fun `본인의 CONFIRMED 주문을 취소하면 200과 CANCELED 주문을 돌려준다`() {
        val dropId = insertDropWithStock()
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)

        cancel(orderId, userId)
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(orderId.toString())
            .jsonPath("$.dropName").isEqualTo(DROP_NAME)
            .jsonPath("$.status").isEqualTo("CANCELED")
            .jsonPath("$.canceledAt").value<String> { assertThat(it).matches(UTC_MILLIS_REGEX) }
    }

    @Test
    fun `취소하면 남은 수량을 1 되돌린다`() {
        val dropId = insertDropWithStock(totalQuantity = 10)
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)

        cancel(orderId, userId).expectStatus().isOk

        assertThat(findRemainingQuantity(dropId)).isEqualTo(10)
    }

    @Test
    fun `SOLD_OUT 드롭의 주문을 취소하면 드롭이 OPEN으로 돌아간다`() {
        val dropId = insertDropWithStock(totalQuantity = 1)
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)

        cancel(orderId, userId).expectStatus().isOk

        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
    }

    @Test
    fun `취소로 돌아온 재고는 다른 사용자가 살 수 있다`() {
        val dropId = insertDropWithStock(totalQuantity = 1)
        val userId = UUID.randomUUID()
        cancel(purchaseOrderId(dropId, userId), userId).expectStatus().isOk

        purchase(dropId, UUID.randomUUID()).expectStatus().isCreated
    }

    @Test
    fun `취소한 사용자는 같은 드롭을 다시 살 수 없다`() {
        val dropId = insertDropWithStock()
        val userId = UUID.randomUUID()
        cancel(purchaseOrderId(dropId, userId), userId).expectStatus().isOk

        purchase(dropId, userId)
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo("ALREADY_PURCHASED")
    }

    @Test
    fun `취소하면 확정 항목은 그대로 두고 원장에 CANCELED 항목을 더한다`() {
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(insertDropWithStock(), userId)

        cancel(orderId, userId).expectStatus().isOk

        val entryTypes = jdbc.sql("SELECT entry_type FROM orders.order_ledger WHERE order_id = :orderId ORDER BY id").param("orderId", orderId).query(String::class.java).list()
        assertThat(entryTypes).containsExactly("CONFIRMED", "CANCELED")
    }

    @Test
    fun `취소 원장 항목의 시각은 주문의 취소 시각과 같다`() {
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(insertDropWithStock(), userId)

        cancel(orderId, userId).expectStatus().isOk

        val sameTime = jdbc.sql(
            """
            SELECT l.occurred_at = o.canceled_at FROM orders.order_ledger l JOIN orders.orders o ON o.id = l.order_id
            WHERE l.order_id = :orderId AND l.entry_type = 'CANCELED'
            """,
        ).param("orderId", orderId).query(Boolean::class.java).single()
        assertThat(sameTime).isTrue()
    }

    @Test
    fun `order canceled 페이로드는 CANCELED 상태와 취소 시각을 담는다`() {
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(insertDropWithStock(), userId)

        cancel(orderId, userId).expectStatus().isOk

        val payload = OutboxPayload.findByEventType(jdbc, "order.canceled")
        assertThat(payload.readText("orderId")).isEqualTo(orderId.toString())
        assertThat(payload.readText("status")).isEqualTo("CANCELED")
        assertThat(payload.readText("canceledAt")).matches(UTC_MILLIS_REGEX)
    }

    @Test
    fun `남의 주문을 취소하면 403 FORBIDDEN을 돌려주고 주문은 그대로 둔다`() {
        val orderId = purchaseOrderId(insertDropWithStock(), UUID.randomUUID())

        cancel(orderId, UUID.randomUUID())
            .expectStatus().isForbidden
            .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN")

        assertThat(findOrderStatus(orderId)).isEqualTo("CONFIRMED")
    }

    @Test
    fun `없는 주문을 취소하면 404 ORDER_NOT_FOUND를 돌려준다`() {
        cancel(UUID.randomUUID(), UUID.randomUUID())
            .expectStatus().isNotFound
            .expectBody().jsonPath("$.code").isEqualTo("ORDER_NOT_FOUND")
    }

    @Test
    fun `이미 취소한 주문을 다시 취소하면 409 ORDER_ALREADY_CANCELED를 돌려준다`() {
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(insertDropWithStock(), userId)
        cancel(orderId, userId).expectStatus().isOk

        cancel(orderId, userId)
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo("ORDER_ALREADY_CANCELED")
    }

    @Test
    fun `마감 시각이 지난 드롭의 주문을 취소하면 409 DROP_CLOSED를 돌려주고 재고를 되돌리지 않는다`() {
        val now = Instant.now()
        val dropId = insertDropWithStock(totalQuantity = 10, openAt = now.minusSeconds(7200), closeAt = now.minusSeconds(3600))
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)

        cancel(orderId, userId)
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo("DROP_CLOSED")

        assertThat(findRemainingQuantity(dropId)).isEqualTo(9)
    }

    @Test
    fun `같은 주문을 동시에 20번 취소해도 한 번만 취소하고 재고는 1만 되돌린다`() {
        val dropId = insertDropWithStock(totalQuantity = 10)
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)
        val token = tokens.issueAccessToken(userId)

        val statuses = ConcurrentRequests.run(CONCURRENT_CANCELS) { cancelWithToken(orderId, token).returnResult(String::class.java).status.value() }

        assertThat(statuses.filter { it != OK }).hasSize(CONCURRENT_CANCELS - 1).containsOnly(CONFLICT)
        assertThat(findRemainingQuantity(dropId)).isEqualTo(10)
    }

    @Test
    fun `같은 사용자가 취소와 재구매를 동시에 보내도 취소는 200, 재구매는 409 ALREADY_PURCHASED만 돌려준다`() {
        val outcomes = List(CANCEL_REPURCHASE_ROUNDS) { raceCancelAgainstRepurchases() }.flatten()

        assertThat(outcomes).containsOnly("cancel 200", "purchase 409 ALREADY_PURCHASED")
    }

    @Test
    fun `orderId가 UUID가 아니면 400 VALIDATION_FAILED를 돌려준다`() {
        http.post().uri("/api/orders/not-a-uuid/cancel")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.issueAccessToken()}")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.errors[0].field").isEqualTo("orderId")
    }

    private fun raceCancelAgainstRepurchases(): List<String> {
        val dropId = insertDropWithStock(totalQuantity = 10)
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)
        val token = tokens.issueAccessToken(userId)
        return ConcurrentRequests.run(REPURCHASES_PER_ROUND + 1) { index ->
            if (index == 0) "cancel ${readStatus(cancelWithToken(orderId, token))}" else "purchase ${readStatus(purchaseWithToken(dropId, token))}"
        }
    }

    private fun readStatus(response: RestTestClient.ResponseSpec): String {
        val result = response.returnResult(String::class.java)
        val code = ERROR_CODE_PATTERN.find(result.responseBody.orEmpty())?.groupValues?.get(1)
        return listOfNotNull(result.status.value().toString(), code).joinToString(" ")
    }

    private fun findOrderStatus(orderId: UUID): String = jdbc.sql("SELECT status FROM orders.orders WHERE id = :id").param("id", orderId).query(String::class.java).single()

    private companion object {
        const val CONCURRENT_CANCELS = 20
        const val OK = 200
        const val CONFLICT = 409
        const val CANCEL_REPURCHASE_ROUNDS = 30
        const val REPURCHASES_PER_ROUND = 10
        val ERROR_CODE_PATTERN = Regex("\"code\":\"([A-Z_]+)\"")
    }
}
