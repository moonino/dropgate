package dropgate.common

import java.security.SecureRandom
import java.time.Clock
import java.util.UUID

class UuidV7Generator(private val clock: Clock, private val random: SecureRandom) {
    private var timestamp = -1L
    private var sequence = 0L

    @Synchronized
    fun generate(): UUID {
        val nextTimestamp = maxOf(clock.millis(), timestamp)
        sequence = if (nextTimestamp == timestamp) sequence + 1 else 0
        timestamp = nextTimestamp + sequence / SEQUENCE_CAPACITY
        sequence %= SEQUENCE_CAPACITY
        check(timestamp in 0..MAX_TIMESTAMP) { "UUID v7 생성 시각이 48비트 범위를 벗어났습니다: $timestamp" }
        return UUID((timestamp shl TIME_SHIFT) or VERSION_BITS or sequence, (random.nextLong() and RANDOM_MASK) or VARIANT_BITS)
    }

    private companion object {
        const val TIME_SHIFT = 16
        const val VERSION_BITS = 0x7000L
        const val VARIANT_BITS = Long.MIN_VALUE
        const val RANDOM_MASK = 0x3FFF_FFFF_FFFF_FFFFL
        const val SEQUENCE_CAPACITY = 0x1000L
        const val MAX_TIMESTAMP = 0xFFFF_FFFF_FFFFL
    }
}
