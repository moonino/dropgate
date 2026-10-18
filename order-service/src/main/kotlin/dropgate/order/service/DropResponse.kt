package dropgate.order.service

import com.fasterxml.jackson.annotation.JsonFormat
import dropgate.order.entity.DropEntity
import dropgate.order.entity.DropStatus
import java.time.Instant
import java.util.UUID

private const val UTC_MILLIS_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSX"
private const val UTC = "UTC"

data class DropResponse(
    val id: UUID,
    val name: String,
    val totalQuantity: Int,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = UTC_MILLIS_PATTERN, timezone = UTC) val openAt: Instant,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = UTC_MILLIS_PATTERN, timezone = UTC) val closeAt: Instant,
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
