package dropgate.order

import dropgate.common.error.DropgateException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.ConfigurableTransactionManager
import java.util.UUID

class RedisCommitUnknownObservabilityTest : RedisFlowIntegrationTest() {
    @Autowired
    lateinit var manager: ConfigurableTransactionManager

    @Test
    fun `실제 확정 뒤 응답 실패는 커밋 불확실 카운터를 올린다`() {
        val dropId = confirmOrderWithUnknownCommit()

        assertThat(metrics.get("dropgate_order_commit_unknown_total").tag("drop", dropId.toString()).counter().count()).isEqualTo(1.0)
    }

    @Test
    fun `실제 확정 뒤 응답 실패는 판매 상태를 동기화하지 않는다`() {
        val dropId = confirmOrderWithUnknownCommit()

        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
    }

    private fun confirmOrderWithUnknownCommit(): UUID {
        val dropId = initializeStock()
        failNextCommit(manager).use {
            assertThatThrownBy { flow.purchase(dropId, UUID.randomUUID()) }
                .isInstanceOf(DropgateException::class.java)
        }
        val confirmed = jdbc.sql("SELECT count(*) FROM orders.orders WHERE drop_id = :id AND status = 'CONFIRMED'").param("id", dropId).query(Long::class.java).single()
        assertThat(confirmed).isEqualTo(1L)
        return dropId
    }
}
