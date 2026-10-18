package dropgate.order.service

import java.time.Instant
import java.time.temporal.ChronoUnit

object StorableInstantRange {
    private val STORABLE_START: Instant = Instant.parse("0001-01-01T00:00:00Z")
    private val STORABLE_END_EXCLUSIVE: Instant = Instant.parse("+10000-01-01T00:00:00Z")

    fun contains(instant: Instant): Boolean = !instant.isBefore(STORABLE_START) && instant.isBefore(STORABLE_END_EXCLUSIVE)
}

internal fun Instant.truncateToStoredPrecision(): Instant = truncatedTo(ChronoUnit.MILLIS)
