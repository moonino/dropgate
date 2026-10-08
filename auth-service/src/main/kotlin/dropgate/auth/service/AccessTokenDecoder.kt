package dropgate.auth.service

import com.nimbusds.jose.jwk.RSAKey
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.UUID

private val BEARER_HEADER = Regex("Bearer +([^\\s]+)", RegexOption.IGNORE_CASE)

@Service
class AccessTokenDecoder(key: RSAKey, private val clock: Clock) {
    private val decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build().apply {
        setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(Duration.ZERO).apply {
                    setClock(clock)
                    setAllowEmptyExpiryClaim(false)
                },
                JwtIssuerValidator(ISSUER),
            ),
        )
    }

    fun decodeUserId(authorization: String): UUID {
        val token = BEARER_HEADER.matchEntire(authorization)?.groupValues?.get(1)
            ?: throw UnauthenticatedException("Bearer 인증 헤더가 없거나 형식이 올바르지 않습니다")
        return try {
            val jwt = decoder.decode(token)
            val expiresAt = requireNotNull(jwt.expiresAt) { "토큰에 만료 시각 exp가 없습니다" }
            require(clock.instant().isBefore(expiresAt)) { "토큰의 만료 시각 exp가 지났습니다" }
            require(jwt.claims["typ"] != "refresh") { "리프레시 토큰으로 사용자 정보를 조회할 수 없습니다" }
            val subject = requireNotNull(jwt.subject) { "토큰에 사용자 식별자 sub가 없습니다" }
            val userId = UUID.fromString(subject)
            require(userId.toString().equals(subject, ignoreCase = true)) { "sub가 표준 UUID 형식이 아닙니다" }
            userId
        } catch (exception: JwtException) {
            throw UnauthenticatedException("액세스 토큰 검증 실패: ${exception.message}", exception)
        } catch (exception: IllegalArgumentException) {
            throw UnauthenticatedException("액세스 토큰 클레임 오류: ${exception.message}", exception)
        }
    }
}

class UnauthenticatedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
