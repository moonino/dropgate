package dropgate.auth.service

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import dropgate.auth.entity.UserEntity
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
    private val publicKeys = key.toPublicJWK().let {
        listOf(PublicSigningKey(it.keyType.value, it.keyID, it.keyUse.value, it.algorithm.name, it.modulus.toString(), it.publicExponent.toString()))
    }

    fun issueTokenPair(user: UserEntity): IssuedTokens {
        val issuedAt = clock.instant()
        val access = encode(user.id, issuedAt, ACCESS_TOKEN_TTL, mapOf("role" to user.role), identifiers.generate())
        val refreshTokenId = identifiers.generate()
        val refresh = encode(user.id, issuedAt, REFRESH_TOKEN_TTL, mapOf("typ" to "refresh"), refreshTokenId)
        return IssuedTokens(access, refresh, ACCESS_TOKEN_TTL, refreshTokenId, REFRESH_TOKEN_TTL)
    }

    fun getPublicKeys(): List<PublicSigningKey> = publicKeys

    private fun encode(userId: UUID, issuedAt: Instant, ttl: Duration, claims: Map<String, String>, tokenId: UUID): String {
        val payload = JwtClaimsSet.builder().subject(userId.toString()).issuer(ISSUER).id(tokenId.toString())
            .issuedAt(issuedAt).expiresAt(issuedAt.plus(ttl)).claims { it.putAll(claims) }.build()
        return encoder.encode(JwtEncoderParameters.from(header, payload)).tokenValue
    }
}

data class IssuedTokens(val accessToken: String, val refreshToken: String, val accessTokenTtl: Duration, val refreshTokenId: UUID, val refreshTokenTtl: Duration)

data class PublicSigningKey(val kty: String, val kid: String, val use: String, val alg: String, val n: String, val e: String)
