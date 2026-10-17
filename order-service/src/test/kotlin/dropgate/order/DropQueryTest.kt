package dropgate.order

import org.junit.jupiter.api.Test
import java.util.UUID

class DropQueryTest : DropApiIntegrationTest() {
    @Test
    fun `드롭 단건은 토큰 없이 조회한다`() {
        val dropId = insertDrop()

        http.get().uri("/api/drops/$dropId").exchange()
            .expectStatus().isOk
    }

    @Test
    fun `드롭 단건은 저장한 드롭의 id와 status와 openAt을 돌려준다`() {
        val dropId = insertDrop()

        http.get().uri("/api/drops/$dropId").exchange()
            .expectBody()
            .jsonPath("$.id").isEqualTo(dropId.toString())
            .jsonPath("$.status").isEqualTo("SCHEDULED")
            .jsonPath("$.openAt").isEqualTo("2030-11-01T10:00:00.000Z")
    }

    @Test
    fun `Redis 재고를 아직 읽지 않으므로 단건의 remainingQuantity는 null이다`() {
        val dropId = insertDrop()

        http.get().uri("/api/drops/$dropId").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.remainingQuantity").isEmpty
    }

    @Test
    fun `없는 드롭을 조회하면 404 DROP_NOT_FOUND를 돌려준다`() {
        http.get().uri("/api/drops/${UUID.randomUUID()}").exchange()
            .expectStatus().isNotFound
            .expectBody().jsonPath("$.code").isEqualTo("DROP_NOT_FOUND")
    }

    @Test
    fun `dropId가 UUID가 아니면 400 VALIDATION_FAILED를 돌려준다`() {
        http.get().uri("/api/drops/not-a-uuid").exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("dropId")
    }
}
