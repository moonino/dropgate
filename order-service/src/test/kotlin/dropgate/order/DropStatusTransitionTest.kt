package dropgate.order

import dropgate.order.service.DropStatusTransitionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class DropStatusTransitionTest : DropApiIntegrationTest() {
    @Autowired
    lateinit var transitions: DropStatusTransitionService

    @Test
    fun `오픈 시각이 된 SCHEDULED 드롭은 OPEN이 된다`() {
        val dropId = insertDrop(OPEN_AT)

        transitions.transitionDueDrops(OPEN_AT)

        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
    }

    @Test
    fun `오픈 시각 전의 SCHEDULED 드롭은 그대로 둔다`() {
        val dropId = insertDrop(OPEN_AT)

        transitions.transitionDueDrops(OPEN_AT.minusMillis(1))

        assertThat(findDropStatus(dropId)).isEqualTo("SCHEDULED")
    }

    @Test
    fun `마감 시각이 된 OPEN과 SOLD_OUT 드롭은 CLOSED가 된다`() {
        val open = insertDrop(OPEN_AT, "OPEN", closeAt = CLOSE_AT)
        val soldOut = insertDrop(OPEN_AT, "SOLD_OUT", closeAt = CLOSE_AT)

        transitions.transitionDueDrops(CLOSE_AT)

        assertThat(listOf(findDropStatus(open), findDropStatus(soldOut))).containsOnly("CLOSED")
    }

    @Test
    fun `마감 시각 전의 OPEN 드롭은 그대로 둔다`() {
        val dropId = insertDrop(OPEN_AT, "OPEN", closeAt = CLOSE_AT)

        transitions.transitionDueDrops(CLOSE_AT.minusMillis(1))

        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
    }

    @Test
    fun `열리지 못한 채 마감 시각이 된 SCHEDULED 드롭은 CLOSED가 된다`() {
        val dropId = insertDrop(OPEN_AT, closeAt = CLOSE_AT)

        transitions.transitionDueDrops(CLOSE_AT)

        assertThat(findDropStatus(dropId)).isEqualTo("CLOSED")
    }

    private companion object {
        val CLOSE_AT: Instant = OPEN_AT.plusSeconds(3600)
    }
}
