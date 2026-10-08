package dropgate.auth.configuration

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

private const val MIN_RSA_KEY_BITS = 2048
private const val PEM_HEADER = "-----BEGIN PRIVATE KEY-----"
private const val PEM_FOOTER = "-----END PRIVATE KEY-----"

@Configuration(proxyBeanMethods = false)
class JwtConfiguration {
    @Bean
    fun jwtSigningKey(@Value("\${dropgate.jwt.private-key-pem:}") pem: String): RSAKey = try {
        val normalized = pem.trim()
        require(normalized.startsWith(PEM_HEADER) && normalized.endsWith(PEM_FOOTER)) { "PKCS#8 PEM 형식이어야 합니다" }
        val encoded = normalized.removePrefix(PEM_HEADER).removeSuffix(PEM_FOOTER).filterNot(Char::isWhitespace)
        val factory = KeyFactory.getInstance("RSA")
        val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded))) as RSAPrivateCrtKey
        require(privateKey.modulus.bitLength() >= MIN_RSA_KEY_BITS) { "RSA 키는 최소 $MIN_RSA_KEY_BITS 비트여야 합니다" }
        val publicKey = factory.generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)) as RSAPublicKey
        RSAKey.Builder(publicKey).privateKey(privateKey).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).keyIDFromThumbprint().build()
    } catch (exception: Exception) {
        throw IllegalStateException("DROPGATE_JWT_PRIVATE_KEY_PEM에서 RSA 서명 키를 읽을 수 없습니다", exception)
    }
}
