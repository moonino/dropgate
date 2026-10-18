package dropgate.order

import dropgate.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class OrderErrorCodeCoverageTest {
    @Test
    fun `오류 코드표의 코드는 전부 내는 통합 테스트가 있거나 범위 밖인 이유가 있다`() {
        val accounted = PRODUCING_TESTS.keys.toList() + OUT_OF_SCOPE.keys

        assertThat(accounted).containsExactlyInAnyOrderElementsOf(ErrorCode.entries)
    }

    private companion object {
        val PRODUCING_TESTS: Map<ErrorCode, KClass<*>> = mapOf(
            ErrorCode.VALIDATION_FAILED to PurchaseTest::class,
            ErrorCode.UNAUTHENTICATED to PurchaseTest::class,
            ErrorCode.DROP_NOT_FOUND to PurchaseTest::class,
            ErrorCode.DROP_NOT_OPEN to PurchaseTest::class,
            ErrorCode.SOLD_OUT to PurchaseTest::class,
            ErrorCode.ALREADY_PURCHASED to PurchaseTest::class,
            ErrorCode.FORBIDDEN to OrderCancelTest::class,
            ErrorCode.ORDER_NOT_FOUND to OrderCancelTest::class,
            ErrorCode.ORDER_ALREADY_CANCELED to OrderCancelTest::class,
            ErrorCode.DROP_CLOSED to OrderCancelTest::class,
            ErrorCode.AUTH_UNAVAILABLE to OrderJwksUnavailableTest::class,
        )
        val OUT_OF_SCOPE = mapOf(
            ErrorCode.NOT_ADMITTED to "입장 확인은 3주 차 대기열과 함께 붙는다(ADR 0005)",
            ErrorCode.STOCK_UNAVAILABLE to "Redis 재고와 대기열은 2주 차와 3주 차다",
            ErrorCode.ORDER_PERSIST_FAILED to "Lua 성공 뒤 DB 실패 보상은 2주 차 Redis 경로다",
            ErrorCode.JOB_EXECUTION_NOT_FOUND to "settlement-batch의 코드다",
            ErrorCode.JOB_ALREADY_RUNNING to "settlement-batch의 코드다",
            ErrorCode.KAKAO_UNAVAILABLE to "auth-service의 코드다",
            ErrorCode.NOTIFICATION_UNAVAILABLE to "notification-service의 코드다",
        )
    }
}
