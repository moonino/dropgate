package dropgate.order.service

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.common.error.INVALID_FORMAT_MESSAGE
import dropgate.common.error.InvalidField
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.UUID

private const val SEPARATOR = ","
private const val CURSOR_FIELD = "cursor"

data class OrderCursor(val confirmedAt: Instant, val id: UUID) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$confirmedAt$SEPARATOR$id".toByteArray())

    companion object {
        fun decode(encoded: String): OrderCursor = try {
            val parts = String(Base64.getUrlDecoder().decode(encoded)).split(SEPARATOR)
            require(parts.size == 2) { "커서는 확정 시각과 식별자 두 부분이어야 합니다" }
            val confirmedAt = Instant.parse(parts[0])
            require(StorableInstantRange.contains(confirmedAt)) { "커서의 확정 시각 $confirmedAt 은 저장할 수 있는 범위 밖입니다" }
            OrderCursor(confirmedAt, UUID.fromString(parts[1]))
        } catch (exception: IllegalArgumentException) {
            throw createInvalidCursorException(encoded, exception)
        } catch (exception: DateTimeParseException) {
            throw createInvalidCursorException(encoded, exception)
        }

        private fun createInvalidCursorException(encoded: String, cause: Exception) = DropgateException(
            ErrorCode.VALIDATION_FAILED,
            "내 주문 커서를 읽을 수 없습니다: $encoded",
            listOf(InvalidField(CURSOR_FIELD, INVALID_FORMAT_MESSAGE)),
            cause,
        )
    }
}
