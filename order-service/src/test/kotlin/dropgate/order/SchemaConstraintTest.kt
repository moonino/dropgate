package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(classes = [OrderServiceApplication::class])
@Import(PostgresTestcontainersConfiguration::class)
class SchemaConstraintTest {
    @Autowired
    lateinit var jdbc: JdbcClient

    @Test
    fun `총수량이 0인 드롭은 넣을 수 없다`() {
        assertThatThrownBy { insertDrop(totalQuantity = 0, remainingQuantity = 0) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `남은 수량이 총수량보다 큰 드롭은 넣을 수 없다`() {
        assertThatThrownBy { insertDrop(totalQuantity = 10, remainingQuantity = 11) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `남은 수량이 음수인 드롭은 넣을 수 없다`() {
        assertThatThrownBy { insertDrop(totalQuantity = 10, remainingQuantity = -1) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `마감 시각이 오픈 시각보다 늦지 않은 드롭은 넣을 수 없다`() {
        assertThatThrownBy { insertDrop(closeAt = OPEN_AT) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `입장 속도는 1부터 10000까지만 허용한다`() {
        assertThatThrownBy { insertDropWithAdmissionRate(0) }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertDropWithAdmissionRate(10_001) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `SCHEDULED, OPEN, SOLD_OUT, CLOSED 밖의 드롭 상태는 넣을 수 없다`() {
        assertThatThrownBy { insertDropWithStatus("PAUSED") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `상태와 입장 속도를 주지 않은 드롭은 SCHEDULED와 50이 된다`() {
        val dropId = insertDrop()

        val row =
            jdbc.sql("SELECT status, admission_rate_per_sec FROM orders.drops WHERE id = :id")
                .param("id", dropId)
                .query { rs, _ -> rs.getString("status") to rs.getInt("admission_rate_per_sec") }
                .single()
        assertThat(row).isEqualTo("SCHEDULED" to 50)
    }

    @Test
    fun `주문 수량은 1만 허용한다`() {
        assertThatThrownBy { insertOrder(insertDrop(), quantity = 2) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `같은 사용자가 같은 드롭을 두 번 주문할 수 없다`() {
        val dropId = insertDrop()
        val userId = UUID.randomUUID()
        insertOrder(dropId, userId)

        assertThatThrownBy { insertOrder(dropId, userId) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `CONFIRMED와 CANCELED 밖의 주문 상태는 넣을 수 없다`() {
        assertThatThrownBy { insertOrder(insertDrop(), status = "REFUNDED") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `취소 시각은 CANCELED 주문에만 있다`() {
        val dropId = insertDrop()

        assertThatThrownBy { insertOrder(dropId, canceledAt = OPEN_AT) }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertOrder(dropId, status = "CANCELED") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `없는 드롭으로 주문을 넣을 수 없다`() {
        assertThatThrownBy { insertOrder(UUID.randomUUID()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `원장 항목 종류는 CONFIRMED와 CANCELED만 허용한다`() {
        assertThatThrownBy { insertLedgerEntry(entryType = "REFUNDED") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `없는 주문으로 원장 항목을 넣을 수 없다`() {
        assertThatThrownBy { insertLedgerEntry(orderId = UUID.randomUUID(), dropId = UUID.randomUUID(), userId = UUID.randomUUID()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `outbox 상태는 PENDING과 PUBLISHED와 FAILED만 허용한다`() {
        assertThatThrownBy { insertOutboxEventWithStatus("SENT") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `상태와 시도 횟수를 주지 않은 outbox 이벤트는 PENDING과 0이 된다`() {
        val eventId = insertOutboxEvent()

        val row =
            jdbc.sql("SELECT status, attempts FROM orders.outbox WHERE id = :id")
                .param("id", eventId)
                .query { rs, _ -> rs.getString("status") to rs.getInt("attempts") }
                .single()
        assertThat(row).isEqualTo("PENDING" to 0)
    }

    @Test
    fun `설계한 인덱스가 이름 그대로 있다`() {
        assertThat(readOrdersIndexDefinitions()).containsKeys(*DESIGNED_INDEXES)
    }

    @Test
    fun `outbox 대기 인덱스는 PENDING 행만 담는 부분 인덱스다`() {
        assertThat(readOrdersIndexDefinitions()["outbox_pending_idx"])
            .contains("WHERE")
            .contains("status")
            .contains("'PENDING'")
    }

    @Test
    fun `드롭은 허용된 전이를 따라 CLOSED까지 간다`() {
        val dropId = insertDrop()

        assertThatCode {
            changeDropStatus(dropId, "OPEN")
            changeDropStatus(dropId, "SOLD_OUT")
            changeDropStatus(dropId, "CLOSED")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `허용되지 않은 드롭 상태 전이는 거절한다`() {
        val dropId = insertDrop()

        assertThatThrownBy { changeDropStatus(dropId, "SOLD_OUT") }.hasMessageContaining(FORBIDDEN_DROP_TRANSITION)
        changeDropStatus(dropId, "OPEN")
        changeDropStatus(dropId, "CLOSED")
        assertThatThrownBy { changeDropStatus(dropId, "OPEN") }.hasMessageContaining(FORBIDDEN_DROP_TRANSITION)
    }

    @Test
    fun `OPEN 이후에는 총수량과 오픈 시각을 바꿀 수 없다`() {
        val dropId = insertDrop()
        changeDropStatus(dropId, "OPEN")

        assertThatThrownBy { updateDrop(dropId, "total_quantity = 20") }.hasMessageContaining(FIXED_AFTER_OPEN)
        assertThatThrownBy { updateDrop(dropId, "open_at = open_at - interval '1 minute'") }.hasMessageContaining(FIXED_AFTER_OPEN)
    }

    @Test
    fun `드롭을 바꾸면 updated_at이 갱신된다`() {
        val dropId = insertDrop()
        val insertedAt = findDropUpdatedAt(dropId)

        updateDrop(dropId, "name = 'renamed'")

        assertThat(findDropUpdatedAt(dropId)).isAfter(insertedAt)
    }

    @Test
    fun `CONFIRMED 주문은 취소 시각과 함께 CANCELED로 바꿀 수 있다`() {
        val orderId = insertOrder(insertDrop())

        assertThatCode { cancelOrder(orderId) }.doesNotThrowAnyException()
    }

    @Test
    fun `주문의 상태와 취소 시각 외 컬럼은 바꿀 수 없다`() {
        val orderId = insertOrder(insertDrop())

        assertThatThrownBy { updateOrder(orderId, "status = 'CANCELED', canceled_at = now(), user_id = gen_random_uuid()") }.hasMessageContaining(FORBIDDEN_ORDER_UPDATE)
    }

    @Test
    fun `취소한 주문은 CONFIRMED로 되돌릴 수 없다`() {
        val orderId = insertOrder(insertDrop())
        cancelOrder(orderId)

        assertThatThrownBy { updateOrder(orderId, "status = 'CONFIRMED', canceled_at = NULL") }.hasMessageContaining(FORBIDDEN_ORDER_UPDATE)
    }

    @Test
    fun `주문은 삭제할 수 없다`() {
        val orderId = insertOrder(insertDrop())

        assertThatThrownBy { jdbc.sql("DELETE FROM orders.orders WHERE id = :id").param("id", orderId).update() }
            .hasMessageContaining(FORBIDDEN_ORDER_DELETE)
    }

    @Test
    fun `원장은 수정과 삭제와 TRUNCATE를 모두 거절한다`() {
        val entryId = insertLedgerEntry()

        assertThatThrownBy { jdbc.sql("UPDATE orders.order_ledger SET entry_type = 'CANCELED' WHERE id = :id").param("id", entryId).update() }
            .hasMessageContaining(IMMUTABLE_LEDGER)
        assertThatThrownBy { jdbc.sql("DELETE FROM orders.order_ledger WHERE id = :id").param("id", entryId).update() }
            .hasMessageContaining(IMMUTABLE_LEDGER)
        assertThatThrownBy { jdbc.sql("TRUNCATE orders.order_ledger").update() }
            .hasMessageContaining(IMMUTABLE_LEDGER)
    }

    private fun readOrdersIndexDefinitions(): Map<String, String> = jdbc.sql("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'orders'")
        .query { rs, _ -> rs.getString("indexname") to rs.getString("indexdef") }
        .list()
        .toMap()

    private fun insertDrop(
        totalQuantity: Int = 10,
        remainingQuantity: Int = totalQuantity,
        closeAt: OffsetDateTime = OPEN_AT.plusHours(1),
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            """
            INSERT INTO orders.drops (id, name, total_quantity, remaining_quantity, open_at, close_at, created_by)
            VALUES (:id, 'drop', :totalQuantity, :remainingQuantity, :openAt, :closeAt, :createdBy)
            """,
        ).param("id", id)
            .param("totalQuantity", totalQuantity)
            .param("remainingQuantity", remainingQuantity)
            .param("openAt", OPEN_AT)
            .param("closeAt", closeAt)
            .param("createdBy", UUID.randomUUID())
            .update()
        return id
    }

    private fun insertDropWithAdmissionRate(admissionRatePerSec: Int) {
        jdbc.sql(
            """
            INSERT INTO orders.drops (id, name, total_quantity, remaining_quantity, open_at, close_at, admission_rate_per_sec, created_by)
            VALUES (gen_random_uuid(), 'drop', 10, 10, :openAt, :closeAt, :admissionRatePerSec, gen_random_uuid())
            """,
        ).param("openAt", OPEN_AT)
            .param("closeAt", OPEN_AT.plusHours(1))
            .param("admissionRatePerSec", admissionRatePerSec)
            .update()
    }

    private fun insertDropWithStatus(status: String) {
        jdbc.sql(
            """
            INSERT INTO orders.drops (id, name, total_quantity, remaining_quantity, open_at, close_at, status, created_by)
            VALUES (gen_random_uuid(), 'drop', 10, 10, :openAt, :closeAt, :status, gen_random_uuid())
            """,
        ).param("openAt", OPEN_AT)
            .param("closeAt", OPEN_AT.plusHours(1))
            .param("status", status)
            .update()
    }

    private fun insertOrder(
        dropId: UUID,
        userId: UUID = UUID.randomUUID(),
        quantity: Int = 1,
        status: String = "CONFIRMED",
        canceledAt: OffsetDateTime? = null,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            """
            INSERT INTO orders.orders (id, drop_id, user_id, quantity, status, confirmed_at, canceled_at)
            VALUES (:id, :dropId, :userId, :quantity, :status, :confirmedAt, :canceledAt)
            """,
        ).param("id", id)
            .param("dropId", dropId)
            .param("userId", userId)
            .param("quantity", quantity)
            .param("status", status)
            .param("confirmedAt", OPEN_AT)
            .param("canceledAt", canceledAt, Types.TIMESTAMP_WITH_TIMEZONE)
            .update()
        return id
    }

    private fun insertLedgerEntry(entryType: String = "CONFIRMED"): Long {
        val dropId = insertDrop()
        val userId = UUID.randomUUID()
        val orderId = insertOrder(dropId, userId)
        return insertLedgerEntry(orderId, dropId, userId, entryType)
    }

    private fun insertLedgerEntry(
        orderId: UUID,
        dropId: UUID,
        userId: UUID,
        entryType: String = "CONFIRMED",
    ): Long = jdbc.sql(
        """
            INSERT INTO orders.order_ledger (order_id, drop_id, user_id, entry_type)
            VALUES (:orderId, :dropId, :userId, :entryType)
            RETURNING id
            """,
    ).param("orderId", orderId)
        .param("dropId", dropId)
        .param("userId", userId)
        .param("entryType", entryType)
        .query(Long::class.java)
        .single()

    private fun insertOutboxEvent(): Long = jdbc.sql(
        """
            INSERT INTO orders.outbox (aggregate_type, aggregate_id, event_type, payload)
            VALUES ('order', gen_random_uuid(), 'order.confirmed', '{}'::jsonb)
            RETURNING id
            """,
    ).query(Long::class.java)
        .single()

    private fun insertOutboxEventWithStatus(status: String) {
        jdbc.sql(
            """
            INSERT INTO orders.outbox (aggregate_type, aggregate_id, event_type, payload, status)
            VALUES ('order', gen_random_uuid(), 'order.confirmed', '{}'::jsonb, :status)
            """,
        ).param("status", status)
            .update()
    }

    private fun changeDropStatus(
        dropId: UUID,
        status: String,
    ) {
        jdbc.sql("UPDATE orders.drops SET status = :status WHERE id = :id").param("status", status).param("id", dropId).update()
    }

    private fun updateDrop(
        dropId: UUID,
        assignment: String,
    ) {
        jdbc.sql("UPDATE orders.drops SET $assignment WHERE id = :id").param("id", dropId).update()
    }

    private fun findDropUpdatedAt(dropId: UUID): OffsetDateTime = jdbc.sql("SELECT updated_at FROM orders.drops WHERE id = :id")
        .param("id", dropId)
        .query(OffsetDateTime::class.java)
        .single()

    private fun cancelOrder(orderId: UUID) {
        updateOrder(orderId, "status = 'CANCELED', canceled_at = now()")
    }

    private fun updateOrder(
        orderId: UUID,
        assignment: String,
    ) {
        jdbc.sql("UPDATE orders.orders SET $assignment WHERE id = :id").param("id", orderId).update()
    }

    companion object {
        private val OPEN_AT = OffsetDateTime.parse("2026-11-01T10:00:00Z")
        private val DESIGNED_INDEXES =
            arrayOf(
                "drops_status_open_at_idx",
                "orders_user_confirmed_idx",
                "orders_drop_status_idx",
                "order_ledger_occurred_idx",
                "order_ledger_drop_occurred_idx",
                "outbox_pending_idx",
            )
        private const val FORBIDDEN_DROP_TRANSITION = "허용되지 않은 드롭 상태 전이"
        private const val FIXED_AFTER_OPEN = "OPEN 이후에는 총수량과 오픈 시각을 바꿀 수 없습니다"
        private const val FORBIDDEN_ORDER_UPDATE = "주문은 CONFIRMED에서 CANCELED로 바꾸는 것만 허용합니다"
        private const val FORBIDDEN_ORDER_DELETE = "주문은 삭제할 수 없습니다"
        private const val IMMUTABLE_LEDGER = "주문 원장은 바꾸거나 지울 수 없습니다"
    }
}
