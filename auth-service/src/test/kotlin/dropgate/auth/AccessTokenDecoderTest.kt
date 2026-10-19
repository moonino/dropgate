package dropgate.auth

import dropgate.auth.configuration.JwtConfiguration
import dropgate.auth.entity.UserEntity
import dropgate.auth.service.AccessTokenDecoder
import dropgate.auth.service.JwtTokenService
import dropgate.common.UuidV7Generator
import dropgate.common.authentication.InMemoryRevokedAccessTokens
import dropgate.common.authentication.RevokedAccessTokens
import dropgate.common.error.DropgateException
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
    private val revoked = InMemoryRevokedAccessTokens()

    @Test
    fun `만료 직전까지 주입한 Clock으로 검증한다`() {
        val decoder = AccessTokenDecoder(key, Clock.offset(clock, Duration.ofMinutes(15).minusNanos(1)), revoked)
        assertThat(decoder.decode("Bearer ${tokens.accessToken}").userId).isEqualTo(user.id)
        assertThat(revoked.lookupCount).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 1])
    fun `만료 시각부터 토큰을 거부하고 예외에 이유를 남긴다`(secondsAfterExpiry: Long) {
        val decoder = AccessTokenDecoder(key, Clock.offset(clock, Duration.ofMinutes(15).plusSeconds(secondsAfterExpiry)), revoked)
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { decoder.decode("Bearer ${tokens.accessToken}") }.hasMessageContaining("exp")
        assertThat(revoked.lookupCount).isZero()
    }

    @Test
    fun `리프레시 토큰을 거부한 이유를 예외에 남긴다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { AccessTokenDecoder(key, clock, revoked).decode("Bearer ${tokens.refreshToken}") }.hasMessageContaining("리프레시 토큰")
        assertThat(revoked.lookupCount).isZero()
    }

    @Test
    fun `서명이 깨진 토큰은 무효화를 확인하지 않는다`() {
        val otherKey = JwtConfiguration().jwtSigningKey(JwtIntegrationTest.generatePrivateKeyPem())
        val forged = JwtTokenService(otherKey, UuidV7Generator(clock, SecureRandom()), clock).issueTokenPair(user).accessToken
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { AccessTokenDecoder(key, clock, revoked).decode("Bearer $forged") }
        assertThat(revoked.lookupCount).isZero()
    }

    @Test
    fun `무효화 확인의 AUTH_UNAVAILABLE을 인증 실패로 바꾸지 않는다`() {
        val unavailable = RevokedAccessTokens { throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "무효화 확인 실패") }
        assertThrowsDropgateError(ErrorCode.AUTH_UNAVAILABLE) { AccessTokenDecoder(key, clock, unavailable).decode("Bearer ${tokens.accessToken}") }
    }
}
