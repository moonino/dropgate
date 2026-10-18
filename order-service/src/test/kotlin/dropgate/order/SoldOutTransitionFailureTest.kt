package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SoldOutTransitionFailureTest : OrderApiIntegrationTest() {
    @BeforeEach
    fun createRejectingFunction() {
        jdbc.sql(
            """
            CREATE FUNCTION orders.reject_sold_out_transition() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN
                RAISE EXCEPTION '테스트가 SOLD_OUT 전이를 거절합니다';
            END;
            $$
            """,
        ).update()
    }

    @AfterEach
    fun dropRejectingTriggers() {
        jdbc.sql("DROP FUNCTION orders.reject_sold_out_transition() CASCADE").update()
    }

    @Test
    fun `SOLD_OUT 전이 갱신이 실패해도 마지막 재고 구매는 201을 돌려준다`() {
        rejectSoldOutTransitionOnUpdate()
        val dropId = insertDropWithStock(totalQuantity = 1)

        purchase(dropId, UUID.randomUUID()).expectStatus().isCreated
    }

    @Test
    fun `SOLD_OUT 전이 커밋이 실패해도 마지막 재고 구매는 201을 돌려준다`() {
        rejectSoldOutTransitionOnCommit()
        val dropId = insertDropWithStock(totalQuantity = 1)

        purchase(dropId, UUID.randomUUID()).expectStatus().isCreated
    }

    @Test
    fun `SOLD_OUT 전이가 실패해도 확정 주문과 원장과 outbox는 남는다`() {
        rejectSoldOutTransitionOnUpdate()

        purchase(insertDropWithStock(totalQuantity = 1), UUID.randomUUID()).expectStatus().isCreated

        assertThat(listOf(countRows("orders"), countRows("order_ledger"), countRows("outbox"))).containsOnly(1L)
    }

    @Test
    fun `SOLD_OUT 전이가 실패하면 드롭은 남은 수량 0인 OPEN으로 남는다`() {
        rejectSoldOutTransitionOnUpdate()
        val dropId = insertDropWithStock(totalQuantity = 1)

        purchase(dropId, UUID.randomUUID()).expectStatus().isCreated

        assertThat(findDropStatus(dropId) to findRemainingQuantity(dropId)).isEqualTo("OPEN" to 0)
    }

    private fun rejectSoldOutTransitionOnUpdate() {
        jdbc.sql(
            """
            CREATE TRIGGER reject_sold_out_on_update BEFORE UPDATE ON orders.drops
            FOR EACH ROW WHEN (OLD.status = 'OPEN' AND NEW.status = 'SOLD_OUT')
            EXECUTE FUNCTION orders.reject_sold_out_transition()
            """,
        ).update()
    }

    private fun rejectSoldOutTransitionOnCommit() {
        jdbc.sql(
            """
            CREATE CONSTRAINT TRIGGER reject_sold_out_on_commit AFTER UPDATE ON orders.drops
            DEFERRABLE INITIALLY DEFERRED
            FOR EACH ROW WHEN (OLD.status = 'OPEN' AND NEW.status = 'SOLD_OUT')
            EXECUTE FUNCTION orders.reject_sold_out_transition()
            """,
        ).update()
    }
}
