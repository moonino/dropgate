package dropgate.order

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.client.RestTestClient
import java.sql.Types
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class MyOrdersTest : OrderApiIntegrationTest() {
    private val userId = UUID.randomUUID()

    @Test
    fun `내 주문을 확정 시각 최신순으로 돌려준다`() {
        val oldest = insertOrder(CONFIRMED_AT)
        val newest = insertOrder(CONFIRMED_AT.plusSeconds(20))
        val middle = insertOrder(CONFIRMED_AT.plusSeconds(10))

        val page = readPage(getMyOrders())

        assertThat(page.ids).containsExactly(newest, middle, oldest)
    }

    @Test
    fun `남의 주문은 돌려주지 않는다`() {
        val mine = insertOrder(CONFIRMED_AT)
        insertOrder(CONFIRMED_AT, owner = UUID.randomUUID())

        assertThat(readPage(getMyOrders()).ids).containsExactly(mine)
    }

    @Test
    fun `주문마다 드롭 이름과 확정 시각을 담는다`() {
        insertOrder(CONFIRMED_AT)

        getMyOrders().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[0].dropName").isEqualTo(DROP_NAME)
            .jsonPath("$.items[0].quantity").isEqualTo(1)
            .jsonPath("$.items[0].confirmedAt").isEqualTo("2030-11-01T10:30:00.000Z")
    }

    @Test
    fun `취소한 주문도 CANCELED와 취소 시각을 담아 돌려준다`() {
        insertOrder(CONFIRMED_AT, canceledAt = CONFIRMED_AT.plusSeconds(60))

        getMyOrders().expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[0].status").isEqualTo("CANCELED")
            .jsonPath("$.items[0].canceledAt").isEqualTo("2030-11-01T10:31:00.000Z")
    }

    @Test
    fun `주문이 없으면 빈 목록과 null nextCursor를 돌려준다`() {
        val page = readPage(getMyOrders())

        assertThat(page.ids).isEmpty()
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `limit을 주지 않으면 20개씩 돌려준다`() {
        repeat(DEFAULT_LIMIT + 1) { insertOrder(CONFIRMED_AT.plusSeconds(it.toLong())) }

        val page = readPage(getMyOrders())

        assertThat(page.ids).hasSize(DEFAULT_LIMIT)
        assertThat(page.nextCursor).isNotNull()
    }

    @Test
    fun `nextCursor로 다음 페이지를 이어 받고 마지막 페이지의 nextCursor는 null이다`() {
        val orderIds = (4L downTo 0L).map { insertOrder(CONFIRMED_AT.plusSeconds(it)) }

        val first = readPage(getMyOrders("limit=2"))
        val second = readPage(getMyOrders("limit=2&cursor=${first.nextCursor}"))
        val last = readPage(getMyOrders("limit=2&cursor=${second.nextCursor}"))

        assertThat(first.ids + second.ids + last.ids).containsExactlyElementsOf(orderIds)
        assertThat(last.nextCursor).isNull()
    }

    @Test
    fun `확정 시각이 같은 주문도 커서로 빠짐없이 한 번씩 돌려준다`() {
        val orderIds = List(3) { insertOrder(CONFIRMED_AT) }

        val first = readPage(getMyOrders("limit=2"))
        val second = readPage(getMyOrders("limit=2&cursor=${first.nextCursor}"))

        assertThat(first.ids + second.ids).containsExactlyInAnyOrderElementsOf(orderIds)
        assertThat(second.nextCursor).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 101])
    fun `limit이 1에서 100 밖이면 400 VALIDATION_FAILED를 돌려준다`(limit: Int) {
        getMyOrders("limit=$limit")
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("limit")
    }

    @Test
    fun `cursor 형식이 올바르지 않으면 400 VALIDATION_FAILED를 돌려준다`() {
        getMyOrders("cursor=broken")
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("cursor")
    }

    private fun getMyOrders(query: String = ""): RestTestClient.ResponseSpec = http.get().uri("/api/orders/me?$query")
        .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.issueAccessToken(userId)}")
        .exchange()

    private fun insertOrder(confirmedAt: Instant, owner: UUID = userId, canceledAt: Instant? = null): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            """
            INSERT INTO orders.orders (id, drop_id, user_id, quantity, status, confirmed_at, canceled_at)
            VALUES (:id, :dropId, :userId, 1, :status, :confirmedAt, :canceledAt)
            """,
        ).param("id", id)
            .param("dropId", insertDropWithStock())
            .param("userId", owner)
            .param("status", if (canceledAt == null) "CONFIRMED" else "CANCELED")
            .param("confirmedAt", confirmedAt.atOffset(ZoneOffset.UTC))
            .param("canceledAt", canceledAt?.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .update()
        return id
    }

    private fun readPage(response: RestTestClient.ResponseSpec): OrderPage {
        val body = response.expectStatus().isOk.expectBody(OrderPage::class.java).returnResult().responseBody
        return checkNotNull(body) { "내 주문 응답 본문이 없습니다" }
    }

    private data class OrderPage(val items: List<OrderItem>, val nextCursor: String?) {
        val ids: List<UUID> get() = items.map { it.id }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class OrderItem(val id: UUID)

    private companion object {
        const val DEFAULT_LIMIT = 20
        val CONFIRMED_AT: Instant = Instant.parse("2030-11-01T10:30:00Z")
    }
}
