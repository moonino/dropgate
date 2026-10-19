package dropgate.order

import dropgate.common.error.ErrorCode
import dropgate.order.service.OrderConfirmationRolledBackException
import dropgate.order.service.RedisOrderConfirmationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.TransientDataAccessResourceException
import org.springframework.transaction.ConfigurableTransactionManager
import java.util.UUID

class OrderConfirmationFailureTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var confirmations: RedisOrderConfirmationService

    @Autowired
    lateinit var manager: ConfigurableTransactionManager

    @ParameterizedTest
    @ValueSource(strings = ["orders", "order_ledger", "outbox"])
    fun `확정 본문의 기록 실패는 롤백 완료 전용 예외다`(table: String) {
        val dropId = insertDropWithStock()
        withRejectedWrites(table) {
            assertThatThrownBy { confirmations.confirm(dropId, UUID.randomUUID()) }
                .isInstanceOfSatisfying(OrderConfirmationRolledBackException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.ORDER_PERSIST_FAILED) }
        }
        assertThat(listOf(countRows("orders"), countRows("order_ledger"), countRows("outbox"))).containsOnly(0L)
    }

    @Test
    fun `기존 트랜잭션 안에서도 롤백을 마친 뒤 실패가 전파된다`() {
        val dropId = insertDropWithStock()
        withRejectedWrites("outbox") {
            transactions.executeWithoutResult {
                assertThatThrownBy { confirmations.confirm(dropId, UUID.randomUUID()) }.isInstanceOf(OrderConfirmationRolledBackException::class.java)
                assertThat(countRows("orders")).isZero()
            }
        }
    }

    @Test
    fun `실제 커밋 후 오류는 롤백 완료로 표시하지 않는다`() {
        val dropId = insertDropWithStock()
        failNextCommit(manager).use {
            assertThatThrownBy { confirmations.confirm(dropId, UUID.randomUUID()) }
                .isInstanceOf(TransientDataAccessResourceException::class.java)
        }

        assertThat(listOf(countRows("orders"), countRows("order_ledger"), countRows("outbox"))).containsOnly(1L)
    }

    private fun withRejectedWrites(table: String, action: () -> Unit) {
        jdbc.sql("CREATE FUNCTION orders.reject_confirmation() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test failure''; END'").update()
        jdbc.sql("CREATE TRIGGER reject_confirmation BEFORE INSERT ON orders.$table FOR EACH ROW EXECUTE FUNCTION orders.reject_confirmation()").update()
        try {
            action()
        } finally {
            jdbc.sql("DROP TRIGGER reject_confirmation ON orders.$table").update()
            jdbc.sql("DROP FUNCTION orders.reject_confirmation()").update()
        }
    }
}
