package dropgate.order.service

import com.fasterxml.jackson.annotation.JsonFormat
import dropgate.order.entity.OrderEntity
import dropgate.order.entity.OrderStatus
import java.time.Instant
import java.util.UUID

data class OrderResponse(
    val id: UUID,
    val dropId: UUID,
    val dropName: String,
    val quantity: Int,
    val status: OrderStatus,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = UTC_MILLIS_JSON_PATTERN, timezone = UTC_JSON_TIMEZONE) val confirmedAt: Instant,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = UTC_MILLIS_JSON_PATTERN, timezone = UTC_JSON_TIMEZONE) val canceledAt: Instant?,
) {
    companion object {
        fun createFrom(order: OrderEntity, dropName: String) = OrderResponse(order.id, order.dropId, dropName, order.quantity, order.status, order.confirmedAt, order.canceledAt)
    }
}
