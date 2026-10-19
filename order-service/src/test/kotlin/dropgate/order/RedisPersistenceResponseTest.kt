package dropgate.order

import dropgate.order.repository.DropRepository
import dropgate.order.repository.DropStockRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.transaction.ConfigurableTransactionManager
import java.util.UUID

@Import(RedisPurchaseHttpConfiguration::class)
class RedisPersistenceResponseTest @Autowired constructor(
    private val stocks: DropStockRepository,
    private val drops: DropRepository,
    private val redis: StringRedisTemplate,
    private val manager: ConfigurableTransactionManager,
) : OrderApiIntegrationTest() {
    @Test
    fun `DB 본문 실패는 503과 재시도 헤더를 돌려준다`() {
        val dropId = initializeStock()
        jdbc.sql("CREATE FUNCTION orders.reject_order() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test failure''; END'").update()
        jdbc.sql("CREATE TRIGGER reject_order BEFORE INSERT ON orders.orders FOR EACH ROW EXECUTE FUNCTION orders.reject_order()").update()
        try {
            expectPersistenceFailure(dropId)
        } finally {
            jdbc.sql("DROP TRIGGER reject_order ON orders.orders").update()
            jdbc.sql("DROP FUNCTION orders.reject_order()").update()
        }
    }

    @Test
    fun `실제 커밋 뒤 오류는 503과 재시도 헤더를 돌려준다`() {
        val dropId = initializeStock()

        failNextCommit(manager).use { expectPersistenceFailure(dropId) }
    }

    @Test
    fun `취소 뒤 재고 키 유실로 복구하지 못해도 HTTP 200을 유지한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)
        redis.delete("drop:$dropId:stock")

        cancel(orderId, userId).expectStatus().isOk.expectBody().jsonPath("$.status").isEqualTo("CANCELED")
    }

    private fun initializeStock(): UUID {
        val dropId = insertDropWithStock(totalQuantity = 1)
        stocks.initialize(drops.findById(dropId).orElseThrow())
        return dropId
    }

    private fun expectPersistenceFailure(dropId: UUID) {
        purchase(dropId, UUID.randomUUID()).expectStatus().isEqualTo(503)
            .expectHeader().valueEquals("Retry-After", "2")
            .expectBody().jsonPath("$.code").isEqualTo("ORDER_PERSIST_FAILED")
    }
}
