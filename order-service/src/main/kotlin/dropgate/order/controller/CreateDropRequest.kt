package dropgate.order.controller

import dropgate.order.service.CreateDropCommand
import java.time.Instant

private const val DEFAULT_ADMISSION_RATE_PER_SEC = 50

data class CreateDropRequest(
    val name: String?,
    val totalQuantity: Int?,
    val openAt: Instant?,
    val closeAt: Instant?,
    val admissionRatePerSec: Int = DEFAULT_ADMISSION_RATE_PER_SEC,
) {
    fun toCommand(): CreateDropCommand = CreateDropCommand(
        requireValidated(name, "name"),
        requireValidated(totalQuantity, "totalQuantity"),
        requireValidated(openAt, "openAt"),
        requireValidated(closeAt, "closeAt"),
        admissionRatePerSec,
    )

    private fun <T : Any> requireValidated(value: T?, field: String): T = checkNotNull(value) { "검증을 거치지 않은 드롭 등록 요청입니다: $field 이 없습니다" }
}
