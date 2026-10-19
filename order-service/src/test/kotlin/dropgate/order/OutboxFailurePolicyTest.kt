package dropgate.order

import dropgate.order.entity.OutboxStatus
import dropgate.order.event.OrderEventStream
import dropgate.order.repository.OutboxRepository
import dropgate.order.service.OutboxRelayService
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID

class OutboxFailurePolicyTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var relay: OutboxRelayService

    @Autowired
    lateinit var outbox: OutboxRepository

    @Autowired
    lateinit var meters: MeterRegistry

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var events: OrderEventStream

    @BeforeEach
    fun clearEventStream() {
        redis.delete("orders.events")
    }

    @Test
    fun `누락된 발생 시각 필드를 오류에 남긴다`() {
        val event = outbox.findById(insertInvalidEvent()).orElseThrow()

        assertThatThrownBy { events.publish(event) }.hasStackTraceContaining("confirmedAt")
    }

    @Test
    fun `발생 시각을 만들 수 없는 이벤트는 시도 횟수를 올리고 PENDING으로 남는다`() {
        val eventId = insertInvalidEvent()

        relay.publishPendingEvents()

        val event = outbox.findById(eventId).orElseThrow()
        assertThat(event.attempts).isEqualTo(1)
        assertThat(event.status).isEqualTo(OutboxStatus.PENDING)
    }

    @Test
    fun `일반 실패 열 번째에 FAILED로 남기고 실패 카운터를 한 번 올린다`() {
        val eventId = insertInvalidEvent()
        val before = meters.counter(FAILED_COUNTER).count()

        repeat(11) { relay.publishPendingEvents() }

        val event = outbox.findById(eventId).orElseThrow()
        assertThat(event.status).isEqualTo(OutboxStatus.FAILED)
        assertThat(event.attempts).isEqualTo(10)
        assertThat(meters.counter(FAILED_COUNTER).count() - before).isEqualTo(1.0)
    }

    @Test
    fun `잘못된 이벤트 뒤의 정상 이벤트도 같은 실행에서 발행한다`() {
        val invalidId = insertInvalidEvent()
        purchaseOrderId(insertDropWithStock(), UUID.randomUUID())
        val validId = outbox.findAll().single { it.id != invalidId }.id.toString()

        relay.publishPendingEvents()

        val messages = redis.opsForStream<String, String>().range("orders.events", Range.unbounded())!!
        assertThat(messages.map { it.value.getValue("event_id") }).containsExactly(validId)
    }

    private fun insertInvalidEvent(): Long = jdbc.sql(
        """
        INSERT INTO orders.outbox (aggregate_type, aggregate_id, event_type, payload, created_at)
        VALUES ('order', gen_random_uuid(), 'order.confirmed', '{}'::jsonb, '2000-01-01T00:00:00Z')
        RETURNING id
        """,
    ).query(Long::class.java).single()

    private companion object {
        const val FAILED_COUNTER = "dropgate_outbox_failed_total"
    }
}
