package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID

private val ADMISSION_TTL = Duration.ofSeconds(60)

@TestPropertySource(properties = ["dropgate.order.queue.enabled=true"])
class RedisAdmissionResponseTest : RedisOrderApiIntegrationTest() {
    @Test
    fun `입장 확인이 켜져 있고 입장 키가 없으면 403 NOT_ADMITTED다`() {
        purchase(initializeStock(), UUID.randomUUID()).expectStatus().isForbidden.expectBody().jsonPath("$.code").isEqualTo("NOT_ADMITTED")
    }

    @Test
    fun `입장 키가 있으면 구매 성공과 함께 키를 소비한다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        redis.opsForValue().set("drop:$dropId:admit:$userId", "1", ADMISSION_TTL)

        purchase(dropId, userId).expectStatus().isCreated

        assertThat(redis.hasKey("drop:$dropId:admit:$userId")).isFalse()
    }

    @Test
    fun `입장 키를 소비한 기존 구매자의 재요청도 입장 오류가 먼저다`() {
        val dropId = initializeStock()
        val userId = UUID.randomUUID()
        redis.opsForValue().set("drop:$dropId:admit:$userId", "1", ADMISSION_TTL)
        purchase(dropId, userId).expectStatus().isCreated

        purchase(dropId, userId).expectStatus().isForbidden.expectBody().jsonPath("$.code").isEqualTo("NOT_ADMITTED")
    }
}
