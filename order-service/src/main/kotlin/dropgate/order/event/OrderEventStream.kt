package dropgate.order.event

import dropgate.order.UTC_JSON_TIMEZONE
import dropgate.order.UTC_MILLIS_JSON_PATTERN
import dropgate.order.entity.OutboxEntity
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisException
import org.springframework.dao.DataAccessException
import org.springframework.dao.QueryTimeoutException
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val ORDER_EVENT_STREAM = "orders.events"
private const val MAX_STREAM_LENGTH = 1_000_000L
private val EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern(UTC_MILLIS_JSON_PATTERN).withZone(ZoneId.of(UTC_JSON_TIMEZONE))

@Component
class OrderEventStream(private val redis: StringRedisTemplate, private val jsonMapper: ObjectMapper) {
    fun publish(event: OutboxEntity) {
        val fields = createFields(event)
        try {
            redis.opsForStream<String, String>().add(
                ORDER_EVENT_STREAM,
                fields,
                XAddOptions.trim(TrimOptions.maxLen(MAX_STREAM_LENGTH).approximate()),
            )
        } catch (exception: DataAccessException) {
            val message = "outbox ${event.id} 의 ${event.eventType} Redis 발행에 실패했습니다"
            if (isUnavailable(exception)) {
                throw OrderEventStreamUnavailableException(message, exception)
            }
            throw IllegalStateException(message, exception)
        }
    }

    private fun isUnavailable(exception: DataAccessException): Boolean {
        if (exception is RedisConnectionFailureException || exception is QueryTimeoutException) return true
        val causes = generateSequence<Throwable>(exception) { it.cause }
        return causes.none { it is RedisCommandExecutionException } && causes.any { it.javaClass == RedisException::class.java }
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
            ORDER_CONFIRMED_EVENT -> "confirmedAt"
            ORDER_CANCELED_EVENT -> "canceledAt"
            else -> throw IllegalArgumentException("발생 시각을 읽을 수 없는 주문 이벤트입니다: ${event.id} ${event.eventType}")
        }
        return Instant.parse(jsonMapper.readTree(event.payload).required(field).stringValue())
    }
}
