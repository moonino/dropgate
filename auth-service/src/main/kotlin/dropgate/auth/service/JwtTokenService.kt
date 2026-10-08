package dropgate.auth.service

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import dropgate.common.UuidV7Generator
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val ACCESS_TOKEN_TTL = Duration.ofMinutes(15)
private val REFRESH_TOKEN_TTL = Duration.ofDays(14)
internal const val ISSUER = "dropgate-auth"

@Service
class JwtTokenService(key: RSAKey, private val identifiers: UuidV7Generator, private val clock: Clock) {
    private val encoder = NimbusJwtEncoder(ImmutableJWKSet<SecurityContext>(JWKSet(key)))
    private val header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.keyID).build()
    private val jwks = key.toPublicJWK().let {
        JwksResponse(listOf(JwkResponse(it.keyType.value, it.keyID, it.keyUse.value, it.algorithm.name, it.modulus.toString(), it.publicExponent.toString())))
    }

    fun issueTokenPair(user: UserResponse): TokenPairResponse {
        val issuedAt = clock.instant()
        val access = encode(user.id, issuedAt, ACCESS_TOKEN_TTL, mapOf("role" to user.role))
        val refresh = encode(user.id, issuedAt, REFRESH_TOKEN_TTL, mapOf("typ" to "refresh"))
        return TokenPairResponse(access, refresh, ACCESS_TOKEN_TTL.seconds, "Bearer")
    }

    fun getPublicKeys(): JwksResponse = jwks

    private fun encode(userId: UUID, issuedAt: Instant, ttl: Duration, claims: Map<String, String>): String {
        val payload = JwtClaimsSet.builder().subject(userId.toString()).issuer(ISSUER).id(identifiers.generate().toString())
            .issuedAt(issuedAt).expiresAt(issuedAt.plus(ttl)).claims { it.putAll(claims) }.build()
        return encoder.encode(JwtEncoderParameters.from(header, payload)).tokenValue
    }
}

data class TokenPairResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long, val tokenType: String)

data class JwksResponse(val keys: List<JwkResponse>)

data class JwkResponse(val kty: String, val kid: String, val use: String, val alg: String, val n: String, val e: String)
