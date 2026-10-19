package dropgate.order

import dropgate.order.entity.OutboxEntity
import dropgate.order.event.OrderEventStream
import dropgate.order.repository.OutboxRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID

class OrderEventStreamTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var events: OrderEventStream

    @Autowired
    lateinit var outbox: OutboxRepository

    @Autowired
    lateinit var redis: StringRedisTemplate

    @BeforeEach
    fun clearEventStream() {
        redis.delete("orders.events")
    }

    @Test
    fun `확정 이벤트는 명세의 일곱 필드와 저장된 페이로드를 발행한다`() {
        purchaseOrderId(insertDropWithStock(), UUID.randomUUID())
        val event = outbox.findAll().single()
        val confirmedAt = OutboxPayload.findByEventType(jdbc, "order.confirmed").readText("confirmedAt")

        events.publish(event)

        assertThat(readMessages()).containsExactly(
            mapOf(
                "event_id" to event.id.toString(),
                "event_type" to "order.confirmed",
                "schema_version" to "1",
                "occurred_at" to confirmedAt,
                "aggregate_type" to "order",
                "aggregate_id" to event.aggregateId.toString(),
                "payload" to event.payload,
            ),
        )
    }

    @Test
    fun `취소 이벤트의 발생 시각은 페이로드의 취소 시각이다`() {
        val userId = UUID.randomUUID()
        cancel(purchaseOrderId(insertDropWithStock(), userId), userId).expectStatus().isOk
        val event = outbox.findAll().single { it.eventType == "order.canceled" }
        val canceledAt = OutboxPayload.findByEventType(jdbc, "order.canceled").readText("canceledAt")

        events.publish(event)

        assertThat(readMessages().single()["occurred_at"]).isEqualTo(canceledAt)
    }

    @Test
    fun `발생 시각은 소수점 이하가 없어도 UTC 밀리초로 발행한다`() {
        val event = outbox.save(OutboxEntity("order", UUID.randomUUID(), "order.confirmed", """{"confirmedAt":"2030-11-01T10:00:00Z"}"""))

        events.publish(event)

        assertThat(readMessages().single()["occurred_at"]).isEqualTo("2030-11-01T10:00:00.000Z")
    }

    private fun readMessages(): List<Map<String, String>> = redis.opsForStream<String, String>().range("orders.events", Range.unbounded())!!.map { it.value }
}
