package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.util.UUID

class DropRegistrationTest : DropApiIntegrationTest() {
    @Test
    fun `ADMIN이 드롭을 등록하면 201과 등록한 드롭을 가리키는 Location을 돌려준다`() {
        val response = postDrop(VALID_BODY, issueAdminToken())
            .expectStatus().isCreated
            .expectHeader().valueMatches(HttpHeaders.LOCATION, "/api/drops/[0-9a-f-]{36}")
            .expectBody().returnResult()

        assertThat(response.responseHeaders.location.toString()).endsWith(readJsonId(response.responseBodyContent))
    }

    @Test
    fun `등록한 드롭을 요청 값대로 SCHEDULED 상태로 돌려준다`() {
        postDrop(VALID_BODY, issueAdminToken()).expectStatus().isCreated
            .expectBody().jsonPath("$.name").isEqualTo("한정판 스니커즈")
            .jsonPath("$.totalQuantity").isEqualTo(100)
            .jsonPath("$.openAt").isEqualTo("2030-11-01T10:00:00.000Z")
            .jsonPath("$.closeAt").isEqualTo("2030-11-01T11:00:00.000Z")
            .jsonPath("$.status").isEqualTo("SCHEDULED")
            .jsonPath("$.admissionRatePerSec").isEqualTo(50)
            .jsonPath("$.remainingQuantity").isEmpty
    }

    @Test
    fun `등록한 드롭의 식별자는 UUID v7이다`() {
        postDrop(VALID_BODY, issueAdminToken()).expectStatus().isCreated

        assertThat(findStoredColumn("id", UUID::class.java).version()).isEqualTo(UUID_VERSION_7)
    }

    @Test
    fun `등록한 드롭의 남은 수량은 총수량이다`() {
        postDrop(VALID_BODY, issueAdminToken()).expectStatus().isCreated

        assertThat(findStoredColumn("remaining_quantity", Int::class.java)).isEqualTo(100)
    }

    @Test
    fun `등록한 드롭은 등록한 관리자를 created_by로 저장한다`() {
        val adminId = UUID.randomUUID()
        postDrop(VALID_BODY, issueAdminToken(adminId)).expectStatus().isCreated

        assertThat(findStoredColumn("created_by", UUID::class.java)).isEqualTo(adminId)
    }

    @Test
    fun `입장 속도를 주면 그 값으로 등록한다`() {
        val body = """{"name":"드롭","totalQuantity":1,"openAt":"2030-11-01T10:00:00Z","closeAt":"2030-11-01T11:00:00Z","admissionRatePerSec":200}"""

        postDrop(body, issueAdminToken()).expectStatus().isCreated
            .expectBody().jsonPath("$.admissionRatePerSec").isEqualTo(200)
    }

    private fun <T : Any> findStoredColumn(column: String, type: Class<T>): T = jdbc.sql("SELECT $column FROM orders.drops").query(type).single()

    private fun readJsonId(body: ByteArray): String = Regex("\"id\":\"([0-9a-f-]{36})\"").find(String(body))!!.groupValues[1]

    companion object {
        private const val UUID_VERSION_7 = 7
        private const val VALID_BODY = """{"name":"한정판 스니커즈","totalQuantity":100,"openAt":"2030-11-01T10:00:00Z","closeAt":"2030-11-01T11:00:00Z"}"""
    }
}
