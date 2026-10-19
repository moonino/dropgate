package dropgate.order

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.OrderStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.ConfigurableTransactionManager
import org.springframework.transaction.TransactionExecution
import org.springframework.transaction.TransactionExecutionListener
import java.util.UUID

class RedisFlowFailureTest : RedisFlowIntegrationTest() {
    @Autowired
    lateinit var manager: ConfigurableTransactionManager

    @Test
    fun `재고 키가 없어 취소 복구에 실패해도 취소 결과를 유지한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val orderId = flow.purchase(dropId, userId).order.id
        redis.delete("drop:$dropId:stock")

        val canceled = flow.cancel(orderId, userId)

        assertThat(canceled.order.status).isEqualTo(OrderStatus.CANCELED)
    }

    @Test
    fun `취소 복구 실패는 해당 드롭의 보상 실패를 한 번 센다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val orderId = flow.purchase(dropId, userId).order.id
        redis.delete("drop:$dropId:stock")

        flow.cancel(orderId, userId)

        assertThat(countCompensationFailures(dropId)).isEqualTo(1.0)
    }

    @Test
    fun `메타 키가 없어 취소 복구에 실패하면 DB 판매 상태를 동기화하지 않는다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val orderId = flow.purchase(dropId, userId).order.id
        redis.delete("drop:$dropId:meta")

        flow.cancel(orderId, userId)

        assertThat(findDropStatus(dropId)).isEqualTo("SOLD_OUT")
    }

    @Test
    fun `DB 롤백 뒤 재고 키가 유실돼도 원래 중복 오류를 유지한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        flow.cancel(flow.purchase(dropId, userId).order.id, userId)

        val failure = failCompensation(dropId) { flow.purchase(dropId, userId) }

        assertThat(failure).isInstanceOfSatisfying(DropgateException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.ALREADY_PURCHASED) }
    }

    @Test
    fun `DB 실패 보상에서 키가 유실돼도 해당 드롭의 실패를 한 번 센다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        flow.cancel(flow.purchase(dropId, userId).order.id, userId)

        failCompensation(dropId) { flow.purchase(dropId, userId) }

        assertThat(countCompensationFailures(dropId)).isEqualTo(1.0)
    }

    @Test
    fun `상태 동기화가 실패해도 확정된 구매를 성공으로 반환한다`() {
        val dropId = initializeStock()

        val purchased = rejectSaleStatusUpdates().use { flow.purchase(dropId, UUID.randomUUID()) }

        assertThat(purchased.order.status).isEqualTo(OrderStatus.CONFIRMED)
    }

    @Test
    fun `상태 동기화가 실패해도 취소된 주문을 성공으로 반환한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val orderId = flow.purchase(dropId, userId).order.id

        val canceled = rejectSaleStatusUpdates().use { flow.cancel(orderId, userId) }

        assertThat(canceled.order.status).isEqualTo(OrderStatus.CANCELED)
    }

    @Test
    fun `보상 뒤 상태 동기화가 실패해도 원래 중복 오류를 유지한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        flow.cancel(flow.purchase(dropId, userId).order.id, userId)

        val failure = rejectSaleStatusUpdates().use { runCatching { flow.purchase(dropId, userId) }.exceptionOrNull() }

        assertThat(failure).isInstanceOfSatisfying(DropgateException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.ALREADY_PURCHASED) }
    }

    private fun failCompensation(dropId: UUID, action: () -> Unit): Throwable {
        val previous = manager.transactionExecutionListeners.toList()
        manager.setTransactionExecutionListeners(
            previous + object : TransactionExecutionListener {
                override fun beforeRollback(transaction: TransactionExecution) {
                    redis.delete("drop:$dropId:stock")
                }
            },
        )
        try {
            return checkNotNull(runCatching(action).exceptionOrNull())
        } finally {
            manager.setTransactionExecutionListeners(previous)
        }
    }

    private fun countCompensationFailures(dropId: UUID): Double = metrics.get("dropgate_stock_compensation_failed_total").tag("drop", dropId.toString()).counter().count()

    private fun rejectSaleStatusUpdates(): AutoCloseable {
        jdbc.sql("CREATE FUNCTION orders.reject_sale_status() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test failure''; END'").update()
        jdbc.sql("CREATE TRIGGER reject_sale_status BEFORE UPDATE OF status ON orders.drops FOR EACH ROW EXECUTE FUNCTION orders.reject_sale_status()").update()
        return AutoCloseable {
            jdbc.sql("DROP TRIGGER reject_sale_status ON orders.drops").update()
            jdbc.sql("DROP FUNCTION orders.reject_sale_status()").update()
        }
    }
}
