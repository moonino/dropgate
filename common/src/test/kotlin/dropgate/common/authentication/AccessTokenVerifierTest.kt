package dropgate.common.authentication

import dropgate.common.error.ErrorCode
import dropgate.common.error.assertThrowsDropgateError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class AccessTokenVerifierTest {
    private val clock = MutableClock(Instant.parse("2026-10-17T00:00:00Z"))
    private val issuer = TestTokenIssuer(clock)
    private val fetcher = CountingJwkSetFetcher { issuer.createJwkSetJson() }
    private val verifier = AccessTokenVerifier(JwkSetCache(fetcher, clock), clock)

    @Test
    fun `유효한 토큰에서 사용자 식별자와 역할을 읽는다`() {
        val userId = UUID.randomUUID()
        assertThat(verifier.verify(issuer.issueAccessToken(userId, "ADMIN"))).isEqualTo(AuthenticatedUser(userId, Role.ADMIN))
    }

    @Test
    fun `같은 kid의 다른 키로 서명한 토큰을 거부한다`() {
        val forged = TestTokenIssuer(clock).issueAccessToken()
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(forged) }.hasMessageContaining("서명")
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
    fun `알 수 없는 역할의 토큰을 거부한다`() {
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { verifier.verify(issuer.issueToken { claim("role", "ROOT") }) }.hasMessageContaining("role")
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
