package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class PurchaseConcurrencyTest : OrderApiIntegrationTest() {
    @Test
    fun `재고 100개에 200명이 동시에 사면 정확히 100건만 확정한다`() {
        val dropId = insertDropWithStock(totalQuantity = STOCK)

        val statuses = purchaseConcurrently(dropId, issueTokensForDistinctUsers())

        assertThat(statuses.count { it == CREATED }).isEqualTo(STOCK)
        assertThat(countOrders(dropId)).isEqualTo(STOCK.toLong())
    }

    @Test
    fun `재고를 넘긴 동시 구매는 전부 409로 거절하고 서버 오류를 내지 않는다`() {
        val dropId = insertDropWithStock(totalQuantity = STOCK)

        val statuses = purchaseConcurrently(dropId, issueTokensForDistinctUsers())

        assertThat(statuses.filter { it != CREATED }).hasSize(REQUESTS - STOCK).containsOnly(CONFLICT)
    }

    @Test
    fun `동시 구매가 끝나면 남은 수량은 0이고 드롭은 SOLD_OUT이다`() {
        val dropId = insertDropWithStock(totalQuantity = STOCK)

        purchaseConcurrently(dropId, issueTokensForDistinctUsers())

        assertThat(findRemainingQuantity(dropId) to findDropStatus(dropId)).isEqualTo(0 to "SOLD_OUT")
    }

    @Test
    fun `같은 사용자가 200번 동시에 사도 주문은 1건이고 재고는 1개만 줄어든다`() {
        val dropId = insertDropWithStock(totalQuantity = STOCK)
        val token = tokens.issueAccessToken(UUID.randomUUID())

        val statuses = purchaseConcurrently(dropId, List(REQUESTS) { token })

        assertThat(statuses.count { it == CREATED }).isEqualTo(1)
        assertThat(countOrders(dropId)).isEqualTo(1)
        assertThat(findRemainingQuantity(dropId)).isEqualTo(STOCK - 1)
    }

    @Test
    fun `동시 구매 뒤 원장 CONFIRMED와 outbox 건수는 확정 주문 수와 같다`() {
        val dropId = insertDropWithStock(totalQuantity = STOCK)

        purchaseConcurrently(dropId, issueTokensForDistinctUsers())

        assertThat(listOf(countRows("order_ledger"), countRows("outbox"))).containsOnly(countOrders(dropId))
    }

    private fun issueTokensForDistinctUsers(): List<String> = List(REQUESTS) { tokens.issueAccessToken(UUID.randomUUID()) }

    private fun purchaseConcurrently(dropId: UUID, userTokens: List<String>): List<Int> = ConcurrentRequests.run(userTokens.size) { index ->
        purchaseWithToken(dropId, userTokens[index]).returnResult(String::class.java).status.value()
    }

    private fun countOrders(dropId: UUID): Long = jdbc.sql("SELECT count(*) FROM orders.orders WHERE drop_id = :dropId").param("dropId", dropId).query(Long::class.java).single()

    private companion object {
        const val STOCK = 100
        const val REQUESTS = 200
        const val CREATED = 201
        const val CONFLICT = 409
    }
}
