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

data class DropCursor(val openAt: Instant, val id: UUID) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$openAt$SEPARATOR$id".toByteArray())

    companion object {
        fun decode(encoded: String): DropCursor = try {
            val parts = String(Base64.getUrlDecoder().decode(encoded)).split(SEPARATOR)
            require(parts.size == 2) { "커서는 오픈 시각과 식별자 두 부분이어야 합니다" }
            val openAt = Instant.parse(parts[0])
            require(StorableInstantRange.contains(openAt)) { "커서의 오픈 시각 $openAt 은 저장할 수 있는 범위 밖입니다" }
            DropCursor(openAt, UUID.fromString(parts[1]))
        } catch (exception: IllegalArgumentException) {
            throw createInvalidCursorException(encoded, exception)
        } catch (exception: DateTimeParseException) {
            throw createInvalidCursorException(encoded, exception)
        }

        private fun createInvalidCursorException(encoded: String, cause: Exception) = DropgateException(
            ErrorCode.VALIDATION_FAILED,
            "드롭 목록 커서를 읽을 수 없습니다: $encoded",
            listOf(InvalidField(CURSOR_FIELD, INVALID_FORMAT_MESSAGE)),
            cause,
        )
    }
}
