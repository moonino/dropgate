package dropgate.order

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.service.RedisOrderCancelService
import dropgate.order.service.RedisOrderConfirmationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import java.time.Instant
import java.util.UUID

class RedisOrderPersistenceTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var confirmations: RedisOrderConfirmationService

    @Autowired
    lateinit var cancellations: RedisOrderCancelService

    @Test
    fun `예약 확정은 주문과 원장과 outbox를 같은 주문으로 기록한다`() {
        val result = confirmations.confirm(insertDropWithStock(), UUID.randomUUID())

        val recorded = jdbc.sql(
            """
            SELECT o.status, l.entry_type, e.event_type FROM orders.orders o
            JOIN orders.order_ledger l ON l.order_id = o.id
            JOIN orders.outbox e ON e.aggregate_id = o.id WHERE o.id = :id
            """,
        ).param("id", result.order.id).query { row, _ -> listOf(row.getString(1), row.getString(2), row.getString(3)) }.single()
        assertThat(recorded).containsExactly("CONFIRMED", "CONFIRMED", "order.confirmed")
    }

    @Test
    fun `Redis 예약 확정은 DB 재고 0에서도 수량을 바꾸지 않는다`() {
        val dropId = insertDropWithStock(remainingQuantity = 0)

        confirmations.confirm(dropId, UUID.randomUUID())

        assertThat(findRemainingQuantity(dropId)).isZero()
    }

    @Test
    fun `이미 주문한 사용자의 확정은 추가 기록 없이 거절한다`() {
        val dropId = insertDropWithStock()
        val userId = UUID.randomUUID()
        confirmations.confirm(dropId, userId)

        expectCode(ErrorCode.ALREADY_PURCHASED) { confirmations.confirm(dropId, userId) }

        assertThat(listOf(countRows("orders"), countRows("order_ledger"), countRows("outbox"))).containsOnly(1L)
    }

    @Test
    fun `확정의 outbox 기록이 실패하면 주문과 원장도 롤백한다`() {
        val dropId = insertDropWithStock()
        withRejectedEvents {
            assertThatThrownBy { confirmations.confirm(dropId, UUID.randomUUID()) }.isInstanceOf(DataAccessException::class.java)
        }

        assertThat(listOf(countRows("orders"), countRows("order_ledger"), countRows("outbox"))).containsOnly(0L)
    }

    @Test
    fun `취소는 주문과 원장과 outbox에 취소를 기록한다`() {
        val userId = UUID.randomUUID()
        val orderId = confirmations.confirm(insertDropWithStock(), userId).order.id

        cancellations.cancel(orderId, userId)

        val recorded = jdbc.sql(
            """
            SELECT o.status, l.occurred_at = o.canceled_at, e.event_type FROM orders.orders o
            JOIN orders.order_ledger l ON l.order_id = o.id AND l.entry_type = 'CANCELED'
            JOIN orders.outbox e ON e.aggregate_id = o.id AND e.event_type = 'order.canceled' WHERE o.id = :id
            """,
        ).param("id", orderId).query { row, _ -> listOf(row.getString(1), row.getBoolean(2), row.getString(3)) }.single()
        assertThat(recorded).containsExactly("CANCELED", true, "order.canceled")
    }

    @Test
    fun `취소의 outbox 기록이 실패하면 주문 상태와 원장도 롤백한다`() {
        val userId = UUID.randomUUID()
        val orderId = confirmations.confirm(insertDropWithStock(), userId).order.id
        withRejectedEvents {
            assertThatThrownBy { cancellations.cancel(orderId, userId) }.isInstanceOf(DataAccessException::class.java)
        }

        assertThat(jdbc.sql("SELECT status FROM orders.orders WHERE id = :id").param("id", orderId).query(String::class.java).single()).isEqualTo("CONFIRMED")
        assertThat(listOf(countRows("orders"), countRows("order_ledger"), countRows("outbox"))).containsOnly(1L)
    }

    @Test
    fun `Redis 경로의 DB 취소는 DB 재고를 복구하지 않는다`() {
        val dropId = insertDropWithStock(remainingQuantity = 0)
        val userId = UUID.randomUUID()
        val orderId = confirmations.confirm(dropId, userId).order.id

        cancellations.cancel(orderId, userId)

        assertThat(findRemainingQuantity(dropId)).isZero()
    }

    @Test
    fun `없는 주문의 취소는 ORDER_NOT_FOUND다`() {
        expectCode(ErrorCode.ORDER_NOT_FOUND) { cancellations.cancel(UUID.randomUUID(), UUID.randomUUID()) }
    }

    @Test
    fun `남의 취소된 주문도 취소 상태보다 소유자를 먼저 검사한다`() {
        val userId = UUID.randomUUID()
        val orderId = confirmations.confirm(insertDropWithStock(), userId).order.id
        cancellations.cancel(orderId, userId)

        expectCode(ErrorCode.FORBIDDEN) { cancellations.cancel(orderId, UUID.randomUUID()) }
    }

    @Test
    fun `취소된 주문은 마감보다 취소 상태를 먼저 검사한다`() {
        val now = Instant.now()
        val dropId = insertDropWithStock(openAt = now.minusSeconds(7200), closeAt = now.plusSeconds(3600))
        val userId = UUID.randomUUID()
        val order = confirmations.confirm(dropId, userId).order
        cancellations.cancel(order.id, userId)
        jdbc.sql("UPDATE orders.drops SET close_at = now() - interval '1 hour' WHERE id = :id")
            .param("id", order.dropId).update()

        expectCode(ErrorCode.ORDER_ALREADY_CANCELED) { cancellations.cancel(order.id, userId) }
    }

    @Test
    fun `마감한 드롭의 확정 주문은 DROP_CLOSED다`() {
        val now = Instant.now()
        val dropId = insertDropWithStock(openAt = now.minusSeconds(7200), closeAt = now.minusSeconds(3600))
        val userId = UUID.randomUUID()
        val orderId = confirmations.confirm(dropId, userId).order.id

        expectCode(ErrorCode.DROP_CLOSED) { cancellations.cancel(orderId, userId) }
    }

    private fun expectCode(code: ErrorCode, action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOfSatisfying(DropgateException::class.java) { assertThat(it.errorCode).isEqualTo(code) }
    }

    private fun withRejectedEvents(action: () -> Unit) {
        jdbc.sql("CREATE FUNCTION orders.reject_event() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test failure''; END'").update()
        jdbc.sql("CREATE TRIGGER reject_event BEFORE INSERT ON orders.outbox FOR EACH ROW EXECUTE FUNCTION orders.reject_event()").update()
        try {
            action()
        } finally {
            jdbc.sql("DROP TRIGGER reject_event ON orders.outbox").update()
            jdbc.sql("DROP FUNCTION orders.reject_event()").update()
        }
    }
}
