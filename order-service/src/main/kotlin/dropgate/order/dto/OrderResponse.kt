package dropgate.order.dto

import com.fasterxml.jackson.annotation.JsonFormat
import dropgate.order.UTC_JSON_TIMEZONE
import dropgate.order.UTC_MILLIS_JSON_PATTERN
import dropgate.order.entity.OrderStatus
import dropgate.order.service.OrderWithDropName
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
        fun createFrom(namedOrder: OrderWithDropName): OrderResponse = with(namedOrder.order) {
            OrderResponse(id, dropId, namedOrder.dropName, quantity, status, confirmedAt, canceledAt)
        }
    }
}
