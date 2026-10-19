package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RedisCancelTransactionTest : RedisFlowIntegrationTest() {
    @Test
    fun `바깥 트랜잭션이 롤백돼도 재고 복구 전에 취소를 따로 커밋한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val orderId = flow.purchase(dropId, userId).order.id

        transactions.executeWithoutResult {
            flow.cancel(orderId, userId)
            it.setRollbackOnly()
        }

        val status = jdbc.sql("SELECT status FROM orders.orders WHERE id = :id").param("id", orderId).query(String::class.java).single()
        assertThat(status to readStock(dropId)).isEqualTo("CANCELED" to "1")
    }
}
