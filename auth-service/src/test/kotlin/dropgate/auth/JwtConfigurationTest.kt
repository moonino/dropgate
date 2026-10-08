package dropgate.auth

import dropgate.auth.configuration.JwtConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class JwtConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = ["", "깨진 PEM", "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"])
    fun `개인키가 없거나 깨지면 맥락 있는 예외로 기동에 실패한다`(pem: String) {
        ApplicationContextRunner().withUserConfiguration(JwtConfiguration::class.java)
            .withPropertyValues("dropgate.jwt.private-key-pem=$pem")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("DROPGATE_JWT_PRIVATE_KEY_PEM")
            }
    }
}
