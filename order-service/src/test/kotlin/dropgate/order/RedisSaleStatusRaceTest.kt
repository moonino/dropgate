package dropgate.order

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.ConfigurableTransactionManager
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

private const val COMPLETION_TIMEOUT_SECONDS = 5L

class RedisSaleStatusRaceTest : RedisFlowIntegrationTest() {
    @Autowired
    lateinit var manager: ConfigurableTransactionManager

    @Test
    fun `남은 수량 1 예약이 마지막 예약보다 늦게 확정돼도 최종 상태는 품절이다`() {
        val dropId = initializeStock(total = 2)
        TransactionGate(manager, TransactionGate.Point.BEFORE_BEGIN).use { gate ->
            val delayed = gate.start { flow.purchase(dropId, UUID.randomUUID()) }
            gate.awaitPaused()
            flow.purchase(dropId, UUID.randomUUID())

            gate.release()
            delayed.get(COMPLETION_TIMEOUT_SECONDS, SECONDS)
        }

        assertThat(findDropStatus(dropId)).isEqualTo("SOLD_OUT")
    }

    @Test
    fun `마지막 구매의 상태 동기화가 취소보다 늦어도 복구한 재고를 판매할 수 있다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        TransactionGate(manager, TransactionGate.Point.AFTER_COMMIT).use { gate ->
            val delayed = gate.start { flow.purchase(dropId, userId) }
            gate.awaitPaused()
            val orderId = jdbc.sql("SELECT id FROM orders.orders WHERE drop_id = :id").param("id", dropId).query(UUID::class.java).single()
            flow.cancel(orderId, userId)

            gate.release()
            delayed.get(COMPLETION_TIMEOUT_SECONDS, SECONDS)
        }

        assertThat(readStock(dropId)).isEqualTo("1")
        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
    }

    @Test
    fun `다른 구매가 마지막 재고를 확정한 뒤 지연된 DB 실패를 보상해도 판매 상태가 맞는다`() {
        val dropId = initializeStock(total = 2)
        val userId = UUID.randomUUID()
        jdbc.sql(
            """
            CREATE FUNCTION orders.reject_delayed_order() RETURNS trigger LANGUAGE plpgsql AS '
            BEGIN
                IF NEW.user_id = ''$userId''::uuid THEN RAISE EXCEPTION ''test failure''; END IF;
                RETURN NEW;
            END'
            """,
        ).update()
        jdbc.sql("CREATE TRIGGER reject_delayed_order BEFORE INSERT ON orders.orders FOR EACH ROW EXECUTE FUNCTION orders.reject_delayed_order()").update()
        try {
            TransactionGate(manager, TransactionGate.Point.BEFORE_BEGIN).use { gate ->
                val delayed = gate.start { runCatching { flow.purchase(dropId, userId) } }
                gate.awaitPaused()
                flow.purchase(dropId, UUID.randomUUID())

                gate.release()
                assertThat(delayed.get(COMPLETION_TIMEOUT_SECONDS, SECONDS).exceptionOrNull())
                    .isInstanceOfSatisfying(DropgateException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.ORDER_PERSIST_FAILED) }
            }
        } finally {
            jdbc.sql("DROP TRIGGER reject_delayed_order ON orders.orders").update()
            jdbc.sql("DROP FUNCTION orders.reject_delayed_order()").update()
        }

        assertThat(readStock(dropId)).isEqualTo("1")
        assertThat(redis.opsForHash<String, String>().get("drop:$dropId:meta", "status")).isEqualTo("OPEN")
        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
    }
}
