package dropgate.order

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.service.OrderConfirmationRolledBackException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.ConfigurableTransactionManager
import java.util.UUID

class RedisCommitUnknownTest : RedisFlowIntegrationTest() {
    @Autowired
    lateinit var manager: ConfigurableTransactionManager

    @Test
    fun `실제 커밋 후 오류가 나도 확정된 주문의 재고를 다시 팔지 않는다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        failNextCommit(manager).use {
            expectPersistenceFailure { flow.purchase(dropId, userId) }
        }

        val failure = runCatching { flow.purchase(dropId, UUID.randomUUID()) }.exceptionOrNull()

        val confirmed = jdbc.sql("SELECT count(*) FROM orders.orders WHERE drop_id = :id AND status = 'CONFIRMED'").param("id", dropId).query(Long::class.java).single()
        assertThat(confirmed).isEqualTo(1L)
        assertThat(readStock(dropId)).isEqualTo("0")
        assertThat(redis.opsForSet().members("drop:$dropId:buyers")).containsExactly(userId.toString())
        assertThat(failure)
            .isInstanceOfSatisfying(DropgateException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.SOLD_OUT) }
    }

    @Test
    fun `롤백 실패가 전용 예외를 감싸도 예약을 보상하지 않는다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val wrapped = OrderConfirmationRolledBackException(ErrorCode.ALREADY_PURCHASED, dropId, userId, IllegalStateException("롤백 대상"))
        jdbc.withRejectedInserts("orders") {
            failNextRollback(manager, wrapped).use {
                expectPersistenceFailure { flow.purchase(dropId, userId) }
            }
        }

        assertThat(readStock(dropId)).isEqualTo("0")
    }

    private fun expectPersistenceFailure(action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOfSatisfying(DropgateException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.ORDER_PERSIST_FAILED) }
    }
}
