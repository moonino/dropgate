package dropgate.order

import dropgate.order.service.DropSaleStatusService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

private const val LOCK_WAIT_SECONDS = 5L
private const val LOCK_POLL_MILLIS = 10L

class DropSaleStatusTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var statuses: DropSaleStatusService

    @Test
    fun `확정 주문이 총수량과 같으면 DB 재고를 유지하고 판매 상태만 품절로 맞춘다`() {
        val dropId = insertDropWithStock(totalQuantity = 1)
        insertConfirmedOrder(dropId)

        statuses.synchronizeSaleStatus(dropId)

        assertThat(findDropStatus(dropId)).isEqualTo("SOLD_OUT")
        assertThat(findRemainingQuantity(dropId)).isEqualTo(1)
    }

    @Test
    fun `확정 주문이 적으면 DB 재고 0을 유지하고 판매 상태만 연다`() {
        val dropId = insertDropWithStock(status = "SOLD_OUT", totalQuantity = 1, remainingQuantity = 0)

        statuses.synchronizeSaleStatus(dropId)

        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
        assertThat(findRemainingQuantity(dropId)).isZero()
    }

    @ParameterizedTest
    @ValueSource(strings = ["SCHEDULED", "CLOSED"])
    fun `판매 상태가 아니면 주문 수와 관계없이 유지한다`(status: String) {
        val dropId = insertDropWithStock(status = status, totalQuantity = 1)
        insertConfirmedOrder(dropId)

        statuses.synchronizeSaleStatus(dropId)

        assertThat(findDropStatus(dropId)).isEqualTo(status)
    }

    @Test
    fun `호출한 바깥 트랜잭션이 롤백돼도 상태 동기화는 따로 커밋된다`() {
        val dropId = insertDropWithStock(totalQuantity = 1)
        insertConfirmedOrder(dropId)

        transactions.executeWithoutResult {
            statuses.synchronizeSaleStatus(dropId)
            it.setRollbackOnly()
        }

        assertThat(findDropStatus(dropId)).isEqualTo("SOLD_OUT")
    }

    @Test
    fun `행 잠금을 기다린 뒤 새 스냅샷으로 취소된 주문을 제외한다`() {
        val dropId = insertDropWithStock(status = "SOLD_OUT", totalQuantity = 1, remainingQuantity = 0)
        val orderId = insertConfirmedOrder(dropId)

        Executors.newSingleThreadExecutor().use { executor ->
            val pending = checkNotNull(
                transactions.execute {
                    jdbc.sql("SELECT id FROM orders.drops WHERE id = :id FOR UPDATE").param("id", dropId).query(UUID::class.java).single()
                    val synchronization = executor.submit { statuses.synchronizeSaleStatus(dropId) }
                    awaitBlockedSynchronization()
                    jdbc.sql("UPDATE orders.orders SET status = 'CANCELED', canceled_at = now() WHERE id = :id").param("id", orderId).update()
                    synchronization
                },
            )
            pending.get(LOCK_WAIT_SECONDS, SECONDS)
        }

        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
    }

    private fun insertConfirmedOrder(dropId: UUID): UUID {
        val orderId = UUID.randomUUID()
        jdbc.sql(
            """
            INSERT INTO orders.orders (id, drop_id, user_id, quantity, status, confirmed_at)
            VALUES (:id, :dropId, :userId, 1, 'CONFIRMED', now())
            """,
        ).param("id", orderId).param("dropId", dropId).param("userId", UUID.randomUUID()).update()
        return orderId
    }

    private fun awaitBlockedSynchronization() {
        val deadline = System.nanoTime() + SECONDS.toNanos(LOCK_WAIT_SECONDS)
        while (System.nanoTime() < deadline) {
            jdbc.sql("SELECT pg_stat_clear_snapshot()").query { _, _ -> Unit }.single()
            val blocked = jdbc.sql(
                """
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock' AND query ILIKE '%orders.drops%'
                """,
            ).query(Long::class.java).single()
            if (blocked > 0) return
            Thread.sleep(LOCK_POLL_MILLIS)
        }
        throw AssertionError("판매 상태 동기화가 드롭 행 잠금을 기다리지 않았습니다")
    }
}
