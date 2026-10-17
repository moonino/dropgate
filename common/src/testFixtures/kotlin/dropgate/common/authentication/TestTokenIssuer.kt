package dropgate.common.authentication

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.UUID

private const val RSA_KEY_BITS = 2048
private const val TEST_ISSUER = "dropgate-auth"
private val ACCESS_TOKEN_TTL = Duration.ofMinutes(15)

class TestTokenIssuer(private val clock: Clock, keyId: String = "test-key") {
    private val key = RSAKeyGenerator(RSA_KEY_BITS).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).keyID(keyId).generate()

    fun createJwkSetJson(): String = JWKSet(key.toPublicJWK()).toString()

    fun issueAccessToken(userId: UUID = UUID.randomUUID(), role: String = "USER"): String = issueToken {
        subject(userId.toString())
        claim("role", role)
    }

    fun issueToken(customize: JWTClaimsSet.Builder.() -> Unit): String {
        val issuedAt = clock.instant()
        val claims = JWTClaimsSet.Builder()
            .subject(UUID.randomUUID().toString())
            .issuer(TEST_ISSUER)
            .jwtID(UUID.randomUUID().toString())
            .claim("role", "USER")
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(issuedAt.plus(ACCESS_TOKEN_TTL)))
            .apply(customize)
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(key)) }.serialize()
    }
}
