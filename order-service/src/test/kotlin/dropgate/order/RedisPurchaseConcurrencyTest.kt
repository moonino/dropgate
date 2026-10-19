package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RedisPurchaseConcurrencyTest : RedisOrderApiIntegrationTest() {
    @Test
    fun `총재고보다 많은 동시 구매도 Redis 구매자와 DB 확정 수가 총재고와 같다`() {
        val dropId = initializeStock(total = STOCK)
        val userTokens = List(REQUESTS) { tokens.issueAccessToken(UUID.randomUUID()) }

        val statuses = purchaseConcurrently(dropId, userTokens)

        assertThat(statuses.count { it == CREATED }).isEqualTo(STOCK)
        assertThat(statuses.filter { it != CREATED }).hasSize(REQUESTS - STOCK).containsOnly(CONFLICT)
        assertThat(readStock(dropId)).isEqualTo("0")
        assertThat(redis.opsForSet().size("drop:$dropId:buyers")).isEqualTo(STOCK.toLong())
        assertThat(countRows("orders")).isEqualTo(STOCK.toLong())
    }

    @Test
    fun `같은 사용자의 동시 구매는 한 번만 차감하고 확정한다`() {
        val dropId = initializeStock(total = STOCK)
        val token = tokens.issueAccessToken(UUID.randomUUID())

        val statuses = purchaseConcurrently(dropId, List(REQUESTS) { token })

        assertThat(statuses.count { it == CREATED }).isEqualTo(1)
        assertThat(statuses.filter { it != CREATED }).hasSize(REQUESTS - 1).containsOnly(CONFLICT)
        assertThat(readStock(dropId)).isEqualTo((STOCK - 1).toString())
        assertThat(redis.opsForSet().size("drop:$dropId:buyers")).isEqualTo(1)
        assertThat(countRows("orders")).isEqualTo(1)
    }

    @Test
    fun `같은 주문의 동시 취소도 한 번만 성공하고 재고를 한 번만 복구한다`() {
        val dropId = initializeStock(total = STOCK)
        val userId = UUID.randomUUID()
        val orderId = purchaseOrderId(dropId, userId)
        val token = tokens.issueAccessToken(userId)

        val statuses = ConcurrentRequests.run(REQUESTS) { cancelWithToken(orderId, token).returnResult(String::class.java).status.value() }

        assertThat(statuses.count { it == OK }).isEqualTo(1)
        assertThat(statuses.filter { it != OK }).hasSize(REQUESTS - 1).containsOnly(CONFLICT)
        assertThat(readStock(dropId)).isEqualTo(STOCK.toString())
        assertThat(redis.opsForSet().size("drop:$dropId:buyers")).isZero()
    }

    private fun purchaseConcurrently(dropId: UUID, userTokens: List<String>): List<Int> = ConcurrentRequests.run(userTokens.size) { index ->
        purchaseWithToken(dropId, userTokens[index]).returnResult(String::class.java).status.value()
    }

    private companion object {
        const val STOCK = 10
        const val REQUESTS = 20
        const val OK = 200
        const val CREATED = 201
        const val CONFLICT = 409
    }
}
