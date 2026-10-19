package dropgate.order

import dropgate.order.entity.OutboxStatus
import dropgate.order.repository.OutboxRepository
import dropgate.order.service.OutboxRelayService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OutboxRelayTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var relay: OutboxRelayService

    @Autowired
    lateinit var outbox: OutboxRepository

    @Autowired
    lateinit var redis: StringRedisTemplate

    @BeforeEach
    fun clearEventStream() {
        redis.delete("orders.events")
    }

    @Test
    fun `구매 이벤트를 발행하면 outbox에 발행 상태와 시각을 남긴다`() {
        purchaseOrderId(insertDropWithStock(), UUID.randomUUID())
        val before = Instant.now()

        relay.publishPendingEvents()

        val event = outbox.findAll().single()
        assertThat(event.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(event.publishedAt).isBetween(before.minusMillis(1), Instant.now())
        assertThat(readEventIds()).containsExactly(event.id.toString())
    }

    @Test
    fun `같은 주문의 확정과 취소를 순서와 관계없이 각 사건으로 발행한다`() {
        val userId = UUID.randomUUID()
        cancel(purchaseOrderId(insertDropWithStock(), userId), userId).expectStatus().isOk
        val expected = outbox.findAll().map { it.id.toString() }

        relay.publishPendingEvents()

        assertThat(readEventIds()).containsExactlyInAnyOrderElementsOf(expected)
    }

    @Test
    fun `한 번에 PENDING 100건까지만 발행한다`() {
        insertPendingEvents(250)

        relay.publishPendingEvents()

        assertThat(readEventIds()).hasSize(100)
        assertThat(outbox.findAll().count { it.status == OutboxStatus.PENDING }).isEqualTo(150)
    }

    @Test
    fun `릴레이 두 개가 동시에 200건을 중복 없이 발행한다`() {
        insertPendingEvents(200)

        ConcurrentRequests.run(2) { relay.publishPendingEvents() }

        assertThat(readEventIds()).hasSize(200).doesNotHaveDuplicates()
    }

    @Test
    fun `다른 트랜잭션이 잠근 PENDING 행을 기다리지 않고 건너뛴다`() {
        insertPendingEvents(200)

        Executors.newSingleThreadExecutor().use { executor ->
            transactions.executeWithoutResult {
                outbox.findPendingForUpdate(100)
                executor.submit { relay.publishPendingEvents() }.get(5, TimeUnit.SECONDS)
                assertThat(readEventIds()).hasSize(100)
            }
        }
    }

    @Test
    fun `이미 발행한 행은 다음 실행에서 다시 발행하지 않는다`() {
        insertPendingEvents(1)
        relay.publishPendingEvents()

        relay.publishPendingEvents()

        assertThat(readEventIds()).hasSize(1)
    }

    private fun insertPendingEvents(count: Int) {
        jdbc.sql(
            """
            INSERT INTO orders.outbox (aggregate_type, aggregate_id, event_type, payload)
            SELECT 'order', gen_random_uuid(), 'order.confirmed', '{"confirmedAt":"2030-11-01T10:00:00.123Z"}'::jsonb
            FROM generate_series(1, :count)
            """,
        ).param("count", count).update()
    }

    private fun readEventIds(): List<String> = redis.opsForStream<String, String>().range("orders.events", Range.unbounded())!!.map { it.value.getValue("event_id") }
}
