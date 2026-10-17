package dropgate.common.authentication

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import java.text.ParseException
import java.time.Clock
import java.util.UUID

private const val ACCESS_TOKEN_ISSUER = "dropgate-auth"
private const val ROLE_CLAIM = "role"
private const val TOKEN_TYPE_CLAIM = "typ"
private const val REFRESH_TOKEN_TYPE = "refresh"

class AccessTokenVerifier(private val jwkSetCache: JwkSetCache, private val clock: Clock) {
    fun verify(token: String): AuthenticatedUser {
        val jwt = parse(token)
        verifySignature(jwt)
        val claims = readClaims(jwt)
        verifyClaims(claims)
        return readUser(claims)
    }

    private fun parse(token: String): SignedJWT = try {
        SignedJWT.parse(token)
    } catch (exception: ParseException) {
        throw unauthenticated("JWS 형식의 토큰이 아닙니다", exception)
    }

    private fun verifySignature(jwt: SignedJWT) {
        if (jwt.header.algorithm != JWSAlgorithm.RS256) throw unauthenticated("서명 알고리즘이 RS256이 아닙니다: ${jwt.header.algorithm}")
        val verified = try {
            jwt.verify(RSASSAVerifier(findPublicKey(jwt.header)))
        } catch (exception: JOSEException) {
            throw unauthenticated("서명을 검증할 수 없습니다", exception)
        }
        if (!verified) throw unauthenticated("서명이 JWKS 공개키와 맞지 않습니다: kid ${jwt.header.keyID}")
    }

    private fun findPublicKey(header: JWSHeader): RSAKey {
        val key = jwkSetCache.getCurrentKeys().getKeyByKeyId(header.keyID) ?: throw unauthenticated("JWKS에 kid ${header.keyID} 키가 없습니다")
        return key as? RSAKey ?: throw unauthenticated("kid ${header.keyID} 키가 RSA 키가 아닙니다")
    }

    private fun readClaims(jwt: SignedJWT): JWTClaimsSet = try {
        jwt.jwtClaimsSet
    } catch (exception: ParseException) {
        throw unauthenticated("토큰 클레임을 읽을 수 없습니다", exception)
    }

    private fun verifyClaims(claims: JWTClaimsSet) {
        if (claims.issuer != ACCESS_TOKEN_ISSUER) throw unauthenticated("발급자 iss가 $ACCESS_TOKEN_ISSUER 가 아닙니다: ${claims.issuer}")
        val expiresAt = claims.expirationTime?.toInstant() ?: throw unauthenticated("토큰에 만료 시각 exp가 없습니다")
        if (!clock.instant().isBefore(expiresAt)) throw unauthenticated("토큰의 만료 시각 exp가 지났습니다: $expiresAt")
        if (claims.getClaim(TOKEN_TYPE_CLAIM) == REFRESH_TOKEN_TYPE) throw unauthenticated("리프레시 토큰은 액세스 토큰으로 쓸 수 없습니다")
    }

    private fun readUser(claims: JWTClaimsSet): AuthenticatedUser = AuthenticatedUser(readUserId(claims.subject), readRole(claims.getClaim(ROLE_CLAIM)))

    private fun readUserId(subject: String?): UUID {
        val userId = subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (userId == null || !userId.toString().equals(subject, ignoreCase = true)) throw unauthenticated("sub가 표준 UUID 형식이 아닙니다: $subject")
        return userId
    }

    private fun readRole(role: Any?): Role = Role.entries.firstOrNull { it.name == role } ?: throw unauthenticated("알 수 없는 role입니다: $role")

    private fun unauthenticated(reason: String, cause: Throwable? = null) = DropgateException(ErrorCode.UNAUTHENTICATED, "액세스 토큰 검증 실패: $reason", cause = cause)
}
