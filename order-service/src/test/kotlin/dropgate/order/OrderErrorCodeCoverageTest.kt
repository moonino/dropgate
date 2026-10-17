package dropgate.order

import dropgate.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Instant
import java.util.UUID

class OrderErrorCodeCoverageTest : OrderApiIntegrationTest() {
    @ParameterizedTest(name = "{0}")
    @MethodSource("producedCodes")
    fun `주문 서비스 통합 테스트가 범위 안의 오류 코드를 HTTP 응답으로 낸다`(code: ErrorCode) {
        produce(code)
            .expectStatus().isEqualTo(code.status.value())
            .expectBody().jsonPath("$.code").isEqualTo(code.name)
    }

    @Test
    fun `오류 코드표의 코드는 전부 여기서 내거나 다른 테스트에서 내거나 범위 밖인 이유가 있다`() {
        val accounted = PRODUCED_CODES + PRODUCED_IN_OTHER_TEST.keys + OUT_OF_SCOPE.keys

        assertThat(accounted).containsExactlyInAnyOrderElementsOf(ErrorCode.entries)
    }

    private fun produce(code: ErrorCode): RestTestClient.ResponseSpec = when (code) {
        ErrorCode.VALIDATION_FAILED -> postWithToken("/api/drops/not-a-uuid/orders", UUID.randomUUID())
        ErrorCode.UNAUTHENTICATED -> http.get().uri("/api/orders/me").exchange()
        ErrorCode.FORBIDDEN -> cancel(purchaseOrderId(insertDropWithStock(), UUID.randomUUID()), UUID.randomUUID())
        ErrorCode.DROP_NOT_FOUND -> purchase(UUID.randomUUID(), UUID.randomUUID())
        ErrorCode.ORDER_NOT_FOUND -> cancel(UUID.randomUUID(), UUID.randomUUID())
        ErrorCode.DROP_NOT_OPEN -> purchase(insertDropWithStock(status = "SCHEDULED"), UUID.randomUUID())
        ErrorCode.SOLD_OUT -> purchase(insertDropWithStock(status = "SOLD_OUT", totalQuantity = 1, remainingQuantity = 0), UUID.randomUUID())
        ErrorCode.ALREADY_PURCHASED -> purchaseTwice()
        ErrorCode.ORDER_ALREADY_CANCELED -> cancelTwice()
        ErrorCode.DROP_CLOSED -> cancelAfterClose()
        else -> error("이 테스트가 내지 않는 코드입니다: $code")
    }

    private fun postWithToken(path: String, userId: UUID): RestTestClient.ResponseSpec = http.post().uri(path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.issueAccessToken(userId)}")
        .exchange()

    private fun purchaseTwice(): RestTestClient.ResponseSpec {
        val dropId = insertDropWithStock()
        val userId = UUID.randomUUID()
        purchaseOrderId(dropId, userId)
        return purchase(dropId, userId)
    }

    private fun cancelTwice(): RestTestClient.ResponseSpec {
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(insertDropWithStock(), userId)
        cancel(orderId, userId).expectStatus().isOk
        return cancel(orderId, userId)
    }

    private fun cancelAfterClose(): RestTestClient.ResponseSpec {
        val now = Instant.now()
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(insertDropWithStock(openAt = now.minusSeconds(7200), closeAt = now.minusSeconds(3600)), userId)
        return cancel(orderId, userId)
    }

    companion object {
        private val PRODUCED_CODES = listOf(
            ErrorCode.VALIDATION_FAILED,
            ErrorCode.UNAUTHENTICATED,
            ErrorCode.FORBIDDEN,
            ErrorCode.DROP_NOT_FOUND,
            ErrorCode.ORDER_NOT_FOUND,
            ErrorCode.DROP_NOT_OPEN,
            ErrorCode.SOLD_OUT,
            ErrorCode.ALREADY_PURCHASED,
            ErrorCode.ORDER_ALREADY_CANCELED,
            ErrorCode.DROP_CLOSED,
        )
        private val PRODUCED_IN_OTHER_TEST = mapOf(
            ErrorCode.AUTH_UNAVAILABLE to "JWKS를 막은 컨텍스트가 따로 필요해 OrderJwksUnavailableTest가 낸다",
        )
        private val OUT_OF_SCOPE = mapOf(
            ErrorCode.NOT_ADMITTED to "입장 확인은 3주 차 대기열과 함께 붙는다(ADR 0005)",
            ErrorCode.STOCK_UNAVAILABLE to "Redis 재고와 대기열은 2주 차와 3주 차다",
            ErrorCode.ORDER_PERSIST_FAILED to "Lua 성공 뒤 DB 실패 보상은 2주 차 Redis 경로다",
            ErrorCode.JOB_EXECUTION_NOT_FOUND to "settlement-batch의 코드다",
            ErrorCode.JOB_ALREADY_RUNNING to "settlement-batch의 코드다",
            ErrorCode.KAKAO_UNAVAILABLE to "auth-service의 코드다",
            ErrorCode.NOTIFICATION_UNAVAILABLE to "notification-service의 코드다",
        )

        @JvmStatic
        fun producedCodes(): List<ErrorCode> = PRODUCED_CODES
    }
}
