package dropgate.order.service

import com.fasterxml.jackson.annotation.JsonFormat
import dropgate.order.UTC_JSON_TIMEZONE
import dropgate.order.UTC_MILLIS_JSON_PATTERN
import dropgate.order.entity.OrderEntity
import dropgate.order.entity.OrderStatus
import java.time.Instant
import java.util.UUID

data class OrderEventPayload(
    val orderId: UUID,
    val dropId: UUID,
    val dropName: String,
    val userId: UUID,
    val quantity: Int,
    val status: OrderStatus,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = UTC_MILLIS_JSON_PATTERN, timezone = UTC_JSON_TIMEZONE) val confirmedAt: Instant,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = UTC_MILLIS_JSON_PATTERN, timezone = UTC_JSON_TIMEZONE) val canceledAt: Instant?,
) {
    companion object {
        fun createFrom(order: OrderEntity, dropName: String) = OrderEventPayload(order.id, order.dropId, dropName, order.userId, order.quantity, order.status, order.confirmedAt, order.canceledAt)
    }
}
