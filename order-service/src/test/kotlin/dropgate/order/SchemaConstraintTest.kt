package dropgate.order

import org.assertj.core.api.Assertions.assertThat
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

    companion object {
        private val OPEN_AT = OffsetDateTime.parse("2026-11-01T10:00:00Z")
    }
}
