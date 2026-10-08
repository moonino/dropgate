package dropgate.auth

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.security.KeyPairGenerator
import java.util.Base64

abstract class JwtIntegrationTest {
    companion object {
        private const val RSA_KEY_BITS = 2048
        val privateKeyPem: String = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_BITS) }.generateKeyPair().private.encoded.let {
            "-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(it)}\n-----END PRIVATE KEY-----"
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureJwt(registry: DynamicPropertyRegistry) {
            registry.add("dropgate.jwt.private-key-pem") { privateKeyPem }
        }
    }
}
