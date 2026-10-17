package dropgate.order.controller

import dropgate.common.error.MISSING_VALUE_MESSAGE
import dropgate.order.service.CreateDropCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val MAX_NAME_LENGTH = 100
private const val MIN_TOTAL_QUANTITY = 1L
private const val MIN_ADMISSION_RATE_PER_SEC = 1L
private const val MAX_ADMISSION_RATE_PER_SEC = 10_000L
private const val DEFAULT_ADMISSION_RATE_PER_SEC = 50

@CloseAfterOpen
data class CreateDropRequest(
    @field:NotBlank(message = "이름은 비어 있을 수 없습니다")
    @field:Size(max = MAX_NAME_LENGTH, message = "이름은 {max}자 이하여야 합니다")
    val name: String?,
    @field:NotNull(message = MISSING_VALUE_MESSAGE)
    @field:Min(MIN_TOTAL_QUANTITY, message = "총수량은 {value} 이상이어야 합니다")
    val totalQuantity: Int?,
    @field:NotNull(message = MISSING_VALUE_MESSAGE)
    @field:StorableInstant
    val openAt: Instant?,
    @field:NotNull(message = MISSING_VALUE_MESSAGE)
    @field:StorableInstant
    val closeAt: Instant?,
    @field:Min(MIN_ADMISSION_RATE_PER_SEC, message = "입장 속도는 {value} 이상이어야 합니다")
    @field:Max(MAX_ADMISSION_RATE_PER_SEC, message = "입장 속도는 {value} 이하여야 합니다")
    val admissionRatePerSec: Int = DEFAULT_ADMISSION_RATE_PER_SEC,
) {
    fun toCommand(): CreateDropCommand = CreateDropCommand(
        requireValidated(name, "name"),
        requireValidated(totalQuantity, "totalQuantity"),
        requireValidated(openAt, "openAt").truncateToStoredPrecision(),
        requireValidated(closeAt, "closeAt").truncateToStoredPrecision(),
        admissionRatePerSec,
    )

    private fun <T : Any> requireValidated(value: T?, field: String): T = checkNotNull(value) { "검증을 거치지 않은 드롭 등록 요청입니다: $field 이 없습니다" }
}

internal fun Instant.truncateToStoredPrecision(): Instant = truncatedTo(ChronoUnit.MILLIS)
