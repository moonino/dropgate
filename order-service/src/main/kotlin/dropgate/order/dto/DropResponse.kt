package dropgate.order.dto

import com.fasterxml.jackson.annotation.JsonFormat
import dropgate.order.UTC_JSON_TIMEZONE
import dropgate.order.UTC_MILLIS_JSON_PATTERN
import dropgate.order.entity.DropEntity
import dropgate.order.entity.DropStatus
import java.time.Instant
import java.util.UUID

data class DropResponse(
    val id: UUID,
    val name: String,
    val totalQuantity: Int,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = UTC_MILLIS_JSON_PATTERN, timezone = UTC_JSON_TIMEZONE) val openAt: Instant,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = UTC_MILLIS_JSON_PATTERN, timezone = UTC_JSON_TIMEZONE) val closeAt: Instant,
    val status: DropStatus,
    val admissionRatePerSec: Int,
    val remainingQuantity: Int?,
) {
    companion object {
        fun createWithoutStock(drop: DropEntity) = DropResponse(
            drop.id,
            drop.name,
            drop.totalQuantity,
            drop.openAt,
            drop.closeAt,
            drop.status,
            drop.admissionRatePerSec,
            remainingQuantity = null,
        )
    }
}

data class DropPageResponse(val items: List<DropResponse>, val nextCursor: String?)
