package dropgate.order

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.web.servlet.client.RestTestClient
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

    @Test
    fun `목록은 CLOSED 드롭을 빼고 openAt 순으로 돌려준다`() {
        val soldOut = insertDrop(OPEN_AT.plusSeconds(30), "SOLD_OUT")
        val scheduled = insertDrop(OPEN_AT.plusSeconds(20), "SCHEDULED")
        insertDrop(OPEN_AT.plusSeconds(15), "CLOSED")
        val open = insertDrop(OPEN_AT.plusSeconds(10), "OPEN")

        val page = readPage(http.get().uri("/api/drops").exchange())

        assertThat(page.ids).containsExactly(open, scheduled, soldOut)
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `다음 페이지도 CLOSED 드롭을 뺀다`() {
        insertDrop(OPEN_AT.plusSeconds(10), "OPEN")
        insertDrop(OPEN_AT.plusSeconds(15), "CLOSED")
        val scheduled = insertDrop(OPEN_AT.plusSeconds(20), "SCHEDULED")

        val first = readPage(http.get().uri("/api/drops?limit=1").exchange())
        val second = readPage(http.get().uri("/api/drops?limit=1&cursor=${first.nextCursor}").exchange())

        assertThat(second.ids).containsExactly(scheduled)
    }

    @Test
    fun `limit을 주지 않으면 20개씩 돌려준다`() {
        repeat(DEFAULT_LIMIT + 1) { insertDrop(OPEN_AT.plusSeconds(it.toLong())) }

        val page = readPage(http.get().uri("/api/drops").exchange())

        assertThat(page.ids).hasSize(DEFAULT_LIMIT)
        assertThat(page.nextCursor).isNotNull()
    }

    @Test
    fun `nextCursor로 다음 페이지를 이어 받고 마지막 페이지의 nextCursor는 null이다`() {
        val dropIds = (0L until 5L).map { insertDrop(OPEN_AT.plusSeconds(it)) }

        val first = readPage(http.get().uri("/api/drops?limit=2").exchange())
        val second = readPage(http.get().uri("/api/drops?limit=2&cursor=${first.nextCursor}").exchange())
        val last = readPage(http.get().uri("/api/drops?limit=2&cursor=${second.nextCursor}").exchange())

        assertThat(first.ids + second.ids + last.ids).containsExactlyElementsOf(dropIds)
        assertThat(last.nextCursor).isNull()
    }

    @Test
    fun `openAt이 같은 드롭도 커서로 빠짐없이 한 번씩 돌려준다`() {
        val dropIds = List(3) { insertDrop(OPEN_AT) }

        val first = readPage(http.get().uri("/api/drops?limit=2").exchange())
        val second = readPage(http.get().uri("/api/drops?limit=2&cursor=${first.nextCursor}").exchange())

        assertThat(first.ids + second.ids).containsExactlyInAnyOrderElementsOf(dropIds)
        assertThat(second.nextCursor).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 101])
    fun `limit이 1에서 100 밖이면 400 VALIDATION_FAILED를 돌려준다`(limit: Int) {
        http.get().uri("/api/drops?limit=$limit").exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("limit")
    }

    @Test
    fun `limit이 최대보다 크면 최대값을 담은 메시지를 돌려준다`() {
        http.get().uri("/api/drops?limit=${MAX_LIMIT + 1}").exchange()
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.errors[0].message").isEqualTo("limit은 ${MAX_LIMIT} 이하여야 합니다")
    }

    @Test
    fun `cursor 형식이 올바르지 않으면 400 VALIDATION_FAILED를 돌려준다`() {
        http.get().uri("/api/drops?cursor=broken").exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("cursor")
    }

    private fun readPage(response: RestTestClient.ResponseSpec): DropPage {
        val body = response.expectStatus().isOk.expectBody(DropPage::class.java).returnResult().responseBody
        return checkNotNull(body) { "드롭 목록 응답 본문이 없습니다" }
    }

    private data class DropPage(val items: List<DropItem>, val nextCursor: String?) {
        val ids: List<UUID> get() = items.map { it.id }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DropItem(val id: UUID)

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}
