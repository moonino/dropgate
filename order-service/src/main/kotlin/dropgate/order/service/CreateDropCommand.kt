package dropgate.order.service

import java.time.Instant

data class CreateDropCommand(val name: String, val totalQuantity: Int, val openAt: Instant, val closeAt: Instant, val admissionRatePerSec: Int)
