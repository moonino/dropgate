package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.common.error.assertThrowsDropgateError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class AccessTokenVerifierTest {
    private val clock = MutableClock(Instant.parse("2026-10-17T00:00:00Z"))
    private val issuer = TestTokenIssuer(clock)
    private val fetcher = CountingJwkSetFetcher { issuer.createJwkSetJson() }
    private val revokedTokenId = UUID.randomUUID().toString()
    private val revoked = InMemoryRevokedAccessTokens(setOf(revokedTokenId))
    private val verifier = AccessTokenVerifier(JwkSetCache(fetcher, clock), clock, revoked)

    @Test
    fun `무효화된 jti면 토큰을 거부한다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(issuer.issueToken { jwtID(revokedTokenId) }) }.hasMessageContaining("무효화")
    }

    @Test
    fun `무효화되지 않은 토큰은 확인 뒤 통과한다`() {
        assertThat(verifier.verify(issuer.issueAccessToken()).role).isEqualTo(Role.USER)
        assertThat(revoked.lookupCount).isEqualTo(1)
    }

    @Test
    fun `무효화를 확인할 수 없으면 AUTH_UNAVAILABLE을 유지한다`() {
        val unavailable = RevokedAccessTokens { throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "무효화 확인 실패") }
        val verifier = AccessTokenVerifier(JwkSetCache(fetcher, clock), clock, unavailable)
        assertThrowsDropgateError(ErrorCode.AUTH_UNAVAILABLE) { verifier.verify(issuer.issueAccessToken()) }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" "])
    fun `jti가 없거나 비면 무효화를 조회하기 전에 거부한다`(tokenId: String?) {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(issuer.issueToken { jwtID(tokenId) }) }.hasMessageContaining("jti")
        assertThat(revoked.lookupCount).isZero()
    }

    @Test
    fun `유효한 토큰에서 사용자 식별자와 역할을 읽는다`() {
        val userId = UUID.randomUUID()
        assertThat(verifier.verify(issuer.issueAccessToken(userId, "ADMIN"))).isEqualTo(AuthenticatedUser(userId, Role.ADMIN))
    }

    @Test
    fun `서명이 깨진 토큰은 무효화를 확인하지 않는다`() {
        val forged = TestTokenIssuer(clock).issueAccessToken()
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(forged) }.hasMessageContaining("서명")
        assertThat(revoked.lookupCount).isZero()
    }

    @Test
    fun `JWKS에 없는 kid로 서명한 토큰을 거부한다`() {
        val unknown = TestTokenIssuer(clock, keyId = "other-key").issueAccessToken()
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(unknown) }.hasMessageContaining("other-key")
    }

    @Test
    fun `JWKS에 없는 kid가 와도 JWKS를 다시 받지 않는다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(TestTokenIssuer(clock, keyId = "other-key").issueAccessToken()) }
        assertThat(fetcher.fetchCount).isEqualTo(1)
    }

    @Test
    fun `만료 직전까지는 토큰을 받아들인다`() {
        val token = issuer.issueAccessToken()
        clock.advance(TestTokenIssuer.ACCESS_TOKEN_TTL.minusNanos(1))
        assertThat(verifier.verify(token).role).isEqualTo(Role.USER)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 1])
    fun `만료 시각부터 토큰을 거부한다`(secondsAfterExpiry: Long) {
        val token = issuer.issueAccessToken()
        clock.advance(TestTokenIssuer.ACCESS_TOKEN_TTL.plusSeconds(secondsAfterExpiry))
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(token) }.hasMessageContaining("exp")
    }

    @Test
    fun `리프레시 토큰을 거부한다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(issuer.issueToken { claim("typ", "refresh") }) }.hasMessageContaining("리프레시")
    }

    @Test
    fun `다른 발급자의 토큰을 거부한다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(issuer.issueToken { issuer("other-auth") }) }.hasMessageContaining("iss")
    }

    @Test
    fun `잘못된 역할은 Redis 무효화 확인 전에 거부한다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(issuer.issueToken { claim("role", "ROOT") }) }.hasMessageContaining("role")
        assertThat(revoked.lookupCount).isZero()
    }

    @Test
    fun `표준 UUID가 아닌 sub를 거부한다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(issuer.issueToken { subject("1-1-1-1-1") }) }.hasMessageContaining("sub")
    }

    @Test
    fun `JWT 형식이 아닌 토큰을 거부한다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify("invalid.jwt.token") }
    }
}
