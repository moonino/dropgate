package dropgate.auth.service

import com.nimbusds.jose.jwk.RSAKey
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenDecoder(key: RSAKey, clock: Clock) {
    private val decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build().apply {
        val notBeforeValidator = JwtClaimValidator<Instant>("nbf") { !clock.instant().isBefore(it) }
        setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtClaimValidator<Instant>("exp") { clock.instant().isBefore(it) },
                OAuth2TokenValidator { jwt ->
                    if (jwt.notBefore == null) OAuth2TokenValidatorResult.success() else notBeforeValidator.validate(jwt)
                },
                JwtIssuerValidator(ISSUER),
                JwtClaimValidator<Any>("typ") { it == "refresh" },
            ),
        )
    }

    fun decode(token: String): VerifiedRefreshToken = try {
        val jwt = decoder.decode(token)
        VerifiedRefreshToken(readIdentifier(jwt.subject, "sub"), readIdentifier(jwt.id, "jti"))
    } catch (cause: JwtException) {
        throw DropgateException(ErrorCode.UNAUTHENTICATED, "리프레시 토큰 검증 실패: ${cause.message}", cause = cause)
    } catch (cause: IllegalArgumentException) {
        throw DropgateException(ErrorCode.UNAUTHENTICATED, "리프레시 토큰 클레임 오류: ${cause.message}", cause = cause)
    }

    private fun readIdentifier(value: String?, claim: String): UUID {
        val text = requireNotNull(value) { "리프레시 토큰에 $claim 식별자가 없습니다" }
        val identifier = UUID.fromString(text)
        require(identifier.toString().equals(text, ignoreCase = true)) { "$claim 값이 표준 UUID 형식이 아닙니다" }
        return identifier
    }
}

data class VerifiedRefreshToken(val userId: UUID, val tokenId: UUID)
