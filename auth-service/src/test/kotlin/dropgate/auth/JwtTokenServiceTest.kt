package dropgate.auth

import com.nimbusds.jwt.SignedJWT
import dropgate.auth.configuration.JwtConfiguration
import dropgate.auth.service.JwtTokenService
import dropgate.auth.service.UserResponse
import dropgate.common.UuidV7Generator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class JwtTokenServiceTest {
    @Test
    fun `두 토큰의 발급 시각은 주입한 Clock을 따른다`() {
        val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
        val clock = Clock.fixed(issuedAt, ZoneOffset.UTC)
        val key = JwtConfiguration().jwtSigningKey(JwtIntegrationTest.privateKeyPem)
        val tokens = JwtTokenService(key, UuidV7Generator(clock, SecureRandom()), clock)
            .issueTokenPair(UserResponse(UUID.randomUUID(), "테스터", "USER"))
        listOf(tokens.accessToken, tokens.refreshToken).forEach {
            assertThat(SignedJWT.parse(it).jwtClaimsSet.issueTime.toInstant()).isEqualTo(issuedAt)
        }
    }
}
