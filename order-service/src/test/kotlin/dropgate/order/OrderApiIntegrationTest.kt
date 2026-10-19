package dropgate.order

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@TestPropertySource(properties = ["dropgate.order.purchase-path=db"])
abstract class OrderApiIntegrationTest : DropApiIntegrationTest() {
    fun insertDropWithStock(
        status: String = "OPEN",
        totalQuantity: Int = 10,
        remainingQuantity: Int = totalQuantity,
        openAt: Instant = OPEN_AT,
        closeAt: Instant = openAt.plusSeconds(3600),
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            """
            INSERT INTO orders.drops (id, name, total_quantity, remaining_quantity, open_at, close_at, status, created_by)
            VALUES (:id, :name, :totalQuantity, :remainingQuantity, :openAt, :closeAt, :status, :createdBy)
            """,
        ).param("id", id)
            .param("name", DROP_NAME)
            .param("totalQuantity", totalQuantity)
            .param("remainingQuantity", remainingQuantity)
            .param("openAt", openAt.atOffset(ZoneOffset.UTC))
            .param("closeAt", closeAt.atOffset(ZoneOffset.UTC))
            .param("status", status)
            .param("createdBy", UUID.randomUUID())
            .update()
        return id
    }

    fun purchase(dropId: UUID, userId: UUID): RestTestClient.ResponseSpec = purchaseWithToken(dropId, tokens.issueAccessToken(userId))

    fun purchaseWithToken(dropId: UUID, token: String): RestTestClient.ResponseSpec = http.post().uri("/api/drops/$dropId/orders")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .exchange()

    fun purchaseOrderId(dropId: UUID, userId: UUID): UUID {
        val body = purchase(dropId, userId).expectStatus().isCreated.expectBody(CreatedOrder::class.java).returnResult().responseBody
        return checkNotNull(body) { "구매 응답 본문이 없습니다" }.id
    }

    fun cancel(orderId: UUID, userId: UUID): RestTestClient.ResponseSpec = cancelWithToken(orderId, tokens.issueAccessToken(userId))

    fun cancelWithToken(orderId: UUID, token: String): RestTestClient.ResponseSpec = http.post().uri("/api/orders/$orderId/cancel")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .exchange()

    fun findRemainingQuantity(dropId: UUID): Int = jdbc.sql("SELECT remaining_quantity FROM orders.drops WHERE id = :id").param("id", dropId).query(Int::class.java).single()

    fun countRows(table: String): Long = jdbc.sql("SELECT count(*) FROM orders.$table").query(Long::class.java).single()

    companion object {
        const val DROP_NAME = "한정판 스니커즈"
        const val UTC_MILLIS_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CreatedOrder(val id: UUID)
}
