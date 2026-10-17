package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.UUID

class DropRegistrationTest : DropApiIntegrationTest() {
    @Test
    fun `ADMIN이 드롭을 등록하면 201과 등록한 드롭을 가리키는 Location을 돌려준다`() {
        val response = postDrop(VALID_BODY, issueAdminToken())
            .expectStatus().isCreated
            .expectBody().returnResult()

        assertThat(response.responseHeaders.location.toString()).isEqualTo("/api/drops/${readJsonId(response.responseBodyContent)}")
    }

    @Test
    fun `등록한 드롭을 요청 값대로 SCHEDULED 상태로 돌려준다`() {
        postDrop(VALID_BODY, issueAdminToken()).expectStatus().isCreated
            .expectBody().jsonPath("$.name").isEqualTo("한정판 스니커즈")
            .jsonPath("$.totalQuantity").isEqualTo(100)
            .jsonPath("$.openAt").isEqualTo("2030-11-01T10:00:00.000Z")
            .jsonPath("$.closeAt").isEqualTo("2030-11-01T11:00:00.000Z")
            .jsonPath("$.status").isEqualTo("SCHEDULED")
    }

    @Test
    fun `입장 속도를 주지 않으면 50으로 등록한다`() {
        postDrop(VALID_BODY, issueAdminToken()).expectStatus().isCreated
            .expectBody().jsonPath("$.admissionRatePerSec").isEqualTo(50)
    }

    @Test
    fun `등록 응답은 남은 수량을 숨긴다`() {
        postDrop(VALID_BODY, issueAdminToken()).expectStatus().isCreated
            .expectBody().jsonPath("$.remainingQuantity").isEmpty
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
    fun `시각은 다른 오프셋으로 받아도 UTC 밀리초로 저장하고 돌려준다`() {
        val body = """{"name":"드롭","totalQuantity":1,"openAt":"2030-11-01T19:00:00.123456+09:00","closeAt":"2030-11-01T20:00:00+09:00"}"""

        postDrop(body, issueAdminToken()).expectStatus().isCreated
            .expectBody()
            .jsonPath("$.openAt").isEqualTo("2030-11-01T10:00:00.123Z")
            .jsonPath("$.closeAt").isEqualTo("2030-11-01T11:00:00.000Z")
    }

    @Test
    fun `입장 속도를 주면 그 값으로 등록한다`() {
        val body = """{"name":"드롭","totalQuantity":1,"openAt":"2030-11-01T10:00:00Z","closeAt":"2030-11-01T11:00:00Z","admissionRatePerSec":200}"""

        postDrop(body, issueAdminToken()).expectStatus().isCreated
            .expectBody().jsonPath("$.admissionRatePerSec").isEqualTo(200)
    }

    @Test
    fun `USER 토큰으로 등록하면 403 FORBIDDEN을 돌려주고 저장하지 않는다`() {
        postDrop(VALID_BODY, tokens.issueAccessToken(role = "USER"))
            .expectStatus().isForbidden
            .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN")

        assertThat(countDrops()).isZero()
    }

    @Test
    fun `토큰 없이 등록하면 401 UNAUTHENTICATED를 돌려준다`() {
        http.post().uri("/api/admin/drops").contentType(MediaType.APPLICATION_JSON).body(VALID_BODY).exchange()
            .expectStatus().isUnauthorized
            .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
    }

    @Test
    fun `필수 필드가 빠지면 빠진 필드마다 errors에 담아 400 VALIDATION_FAILED를 돌려준다`() {
        postDrop("{}", issueAdminToken())
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[*].field").value<List<String>> { assertThat(it).containsExactlyInAnyOrder("name", "totalQuantity", "openAt", "closeAt") }
    }

    @ParameterizedTest
    @MethodSource("outOfRangeFields")
    fun `범위를 벗어난 필드는 그 필드를 errors에 담아 400 VALIDATION_FAILED를 돌려준다`(field: String, value: String) {
        val body = """{"name":"드롭","totalQuantity":1,"openAt":"2030-11-01T10:00:00Z","closeAt":"2030-11-01T11:00:00Z","$field":$value}"""

        postDrop(body, issueAdminToken())
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[*].field").value<List<String>> { assertThat(it).containsExactly(field) }
    }

    @Test
    fun `이름이 너무 길면 최대 길이를 담은 메시지를 돌려준다`() {
        val body = """{"name":"${"가".repeat(MAX_NAME_LENGTH + 1)}","totalQuantity":1,"openAt":"2030-11-01T10:00:00Z","closeAt":"2030-11-01T11:00:00Z"}"""

        postDrop(body, issueAdminToken())
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.errors[0].message").isEqualTo("이름은 ${MAX_NAME_LENGTH}자 이하여야 합니다")
    }

    @Test
    fun `마감 시각이 오픈 시각보다 늦지 않으면 closeAt을 errors에 담아 400 VALIDATION_FAILED를 돌려준다`() {
        val body = """{"name":"드롭","totalQuantity":1,"openAt":"2030-11-01T10:00:00Z","closeAt":"2030-11-01T10:00:00.0004Z"}"""

        postDrop(body, issueAdminToken())
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("closeAt")
    }

    @Test
    fun `마감 시각 오류는 다른 필드 오류와 함께 errors에 담는다`() {
        val body = """{"name":"   ","totalQuantity":1,"openAt":"2030-11-01T11:00:00Z","closeAt":"2030-11-01T10:00:00Z"}"""

        postDrop(body, issueAdminToken())
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.errors[*].field").value<List<String>> { assertThat(it).containsExactlyInAnyOrder("name", "closeAt") }
    }

    private fun countDrops(): Long = jdbc.sql("SELECT count(*) FROM orders.drops").query(Long::class.java).single()

    private fun <T : Any> findStoredColumn(column: String, type: Class<T>): T = jdbc.sql("SELECT $column FROM orders.drops").query(type).single()

    private fun readJsonId(body: ByteArray): String = Regex("\"id\":\"([0-9a-f-]{36})\"").find(String(body))!!.groupValues[1]

    companion object {
        private const val UUID_VERSION_7 = 7
        private const val MAX_NAME_LENGTH = 100
        private const val VALID_BODY = """{"name":"한정판 스니커즈","totalQuantity":100,"openAt":"2030-11-01T10:00:00Z","closeAt":"2030-11-01T11:00:00Z"}"""

        @JvmStatic
        fun outOfRangeFields(): List<Arguments> = listOf(
            Arguments.of("name", "\"   \""),
            Arguments.of("name", "\"${"가".repeat(MAX_NAME_LENGTH + 1)}\""),
            Arguments.of("totalQuantity", "0"),
            Arguments.of("admissionRatePerSec", "0"),
            Arguments.of("admissionRatePerSec", "10001"),
            Arguments.of("openAt", "\"0000-12-31T23:59:59.999Z\""),
            Arguments.of("closeAt", "\"+10000-01-01T00:00:00Z\""),
            Arguments.of("closeAt", "\"+300000-01-01T00:00:00Z\""),
        )
    }
}
