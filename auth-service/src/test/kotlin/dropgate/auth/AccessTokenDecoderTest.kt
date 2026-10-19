package dropgate.auth

import dropgate.auth.configuration.JwtConfiguration
import dropgate.auth.entity.UserEntity
import dropgate.auth.service.AccessTokenDecoder
import dropgate.auth.service.JwtTokenService
import dropgate.common.UuidV7Generator
import dropgate.common.error.ErrorCode
import dropgate.common.error.assertThrowsDropgateError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AccessTokenDecoderTest {
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val key = JwtConfiguration().jwtSigningKey(JwtIntegrationTest.privateKeyPem)
    private val user = UserEntity(UUID.randomUUID(), 1234567, "테스터", "USER", clock.instant(), clock.instant())
    private val tokens = JwtTokenService(key, UuidV7Generator(clock, SecureRandom()), clock).issueTokenPair(user)

    @Test
    fun `만료 직전까지 주입한 Clock으로 검증한다`() {
        val decoder = AccessTokenDecoder(key, Clock.offset(clock, Duration.ofMinutes(15).minusNanos(1)))
        assertThat(decoder.decodeUserId("Bearer ${tokens.accessToken}")).isEqualTo(user.id)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 1])
    fun `만료 시각부터 토큰을 거부하고 예외에 이유를 남긴다`(secondsAfterExpiry: Long) {
        val decoder = AccessTokenDecoder(key, Clock.offset(clock, Duration.ofMinutes(15).plusSeconds(secondsAfterExpiry)))
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { decoder.decodeUserId("Bearer ${tokens.accessToken}") }.hasMessageContaining("exp")
    }

    @Test
    fun `리프레시 토큰을 거부한 이유를 예외에 남긴다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { AccessTokenDecoder(key, clock).decodeUserId("Bearer ${tokens.refreshToken}") }.hasMessageContaining("리프레시 토큰")
    }
}
