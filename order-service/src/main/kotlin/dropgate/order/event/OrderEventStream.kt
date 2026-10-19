package dropgate.order.event

import dropgate.order.entity.OutboxEntity
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

private const val ORDER_EVENT_STREAM = "orders.events"
private const val MAX_STREAM_LENGTH = 1_000_000L
private val EVENT_TIME_FORMAT = DateTimeFormatterBuilder().appendInstant(3).toFormatter()

@Component
class OrderEventStream(private val redis: StringRedisTemplate, private val jsonMapper: ObjectMapper) {
    fun publish(event: OutboxEntity) {
        redis.opsForStream<String, String>().add(
            ORDER_EVENT_STREAM,
            createFields(event),
            XAddOptions.trim(TrimOptions.maxLen(MAX_STREAM_LENGTH).approximate()),
        )
    }

    private fun createFields(event: OutboxEntity): Map<String, String> = mapOf(
        "event_id" to checkNotNull(event.id) { "저장되지 않은 주문 이벤트입니다: ${event.aggregateId}" }.toString(),
        "event_type" to event.eventType,
        "schema_version" to "1",
        "occurred_at" to EVENT_TIME_FORMAT.format(readOccurredAt(event)),
        "aggregate_type" to event.aggregateType,
        "aggregate_id" to event.aggregateId.toString(),
        "payload" to event.payload,
    )

    private fun readOccurredAt(event: OutboxEntity): Instant {
        val field = when (event.eventType) {
            "order.confirmed" -> "confirmedAt"
            "order.canceled" -> "canceledAt"
            else -> throw IllegalArgumentException("발생 시각을 읽을 수 없는 주문 이벤트입니다: ${event.id} ${event.eventType}")
        }
        return Instant.parse(jsonMapper.readTree(event.payload).path(field).stringValue(""))
    }
}
