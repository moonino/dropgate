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
