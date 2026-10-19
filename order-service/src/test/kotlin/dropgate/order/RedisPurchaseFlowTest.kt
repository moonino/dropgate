package dropgate.order

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class RedisPurchaseFlowTest : RedisFlowIntegrationTest() {
    @Test
    fun `모든 예약이 확정되면 DB 재고를 유지하며 품절로 동기화한다`() {
        val dropId = initializeStock()

        flow.purchase(dropId, UUID.randomUUID())

        assertThat(findDropStatus(dropId)).isEqualTo("SOLD_OUT")
        assertThat(findRemainingQuantity(dropId)).isEqualTo(1)
    }

    @Test
    fun `DB 저장 실패는 Redis 예약을 보상하고 원래 수량을 다시 판매한다`() {
        val dropId = initializeStock()
        jdbc.sql("CREATE FUNCTION orders.reject_order() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test failure''; END'").update()
        jdbc.sql("CREATE TRIGGER reject_order BEFORE INSERT ON orders.orders FOR EACH ROW EXECUTE FUNCTION orders.reject_order()").update()
        try {
            expectCode(ErrorCode.ORDER_PERSIST_FAILED) { flow.purchase(dropId, UUID.randomUUID()) }
        } finally {
            jdbc.sql("DROP TRIGGER reject_order ON orders.orders").update()
            jdbc.sql("DROP FUNCTION orders.reject_order()").update()
        }

        flow.purchase(dropId, UUID.randomUUID())

        assertThat(countRows("orders")).isEqualTo(1)
        assertThat(readStock(dropId)).isEqualTo("0")
    }

    @Test
    fun `품절 주문 취소는 재고와 DB 판매 상태를 복구한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val orderId = flow.purchase(dropId, userId).order.id

        flow.cancel(orderId, userId)

        assertThat(readStock(dropId)).isEqualTo("1")
        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
    }

    @Test
    fun `취소한 사용자의 재구매는 DB 중복을 보상하고 원래 재고를 유지한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        flow.cancel(flow.purchase(dropId, userId).order.id, userId)

        expectCode(ErrorCode.ALREADY_PURCHASED) { flow.purchase(dropId, userId) }

        assertThat(readStock(dropId)).isEqualTo("1")
        assertThat(redis.opsForSet().members("drop:$dropId:buyers")).isEmpty()
    }

    @Test
    fun `없는 드롭은 Redis 거절 뒤 DB 존재 확인으로 구분한다`() {
        expectCode(ErrorCode.DROP_NOT_FOUND) { flow.purchase(UUID.randomUUID(), UUID.randomUUID()) }
    }

    @Test
    fun `존재하지만 초기화하지 않은 드롭은 DROP_NOT_OPEN이다`() {
        val dropId = insertDropWithStock()

        expectCode(ErrorCode.DROP_NOT_OPEN) { flow.purchase(dropId, UUID.randomUUID()) }
    }

    private fun expectCode(code: ErrorCode, action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOfSatisfying(DropgateException::class.java) { assertThat(it.errorCode).isEqualTo(code) }
    }
}
