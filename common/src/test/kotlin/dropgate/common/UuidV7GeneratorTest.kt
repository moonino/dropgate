package dropgate.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UuidV7GeneratorTest {
    @Test
    fun `같은 밀리초에도 생성 순서대로 정렬된다`() {
        val generator = UuidV7Generator(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), SecureRandom())
        val identifiers = List(5_000) { generator.generate() }
        assertTrue(identifiers.zipWithNext().all { (previous, next) -> previous < next })
    }

    @Test
    fun `생성 시각 순으로 정렬된다`() {
        val instant = Instant.parse("2026-10-07T00:00:00Z")
        val identifier = UuidV7Generator(Clock.fixed(instant, ZoneOffset.UTC), SecureRandom()).generate()
        val next = UuidV7Generator(Clock.fixed(instant.plusMillis(1), ZoneOffset.UTC), SecureRandom()).generate()
        assertTrue(identifier < next)
        assertEquals(instant.toEpochMilli(), identifier.mostSignificantBits ushr 16)
    }

    @Test
    fun `UUID v7의 버전과 변형 비트가 올바르다`() {
        val identifier = UuidV7Generator(Clock.systemUTC(), SecureRandom()).generate()
        assertEquals(7, identifier.version())
        assertEquals(2, identifier.variant())
    }
}
