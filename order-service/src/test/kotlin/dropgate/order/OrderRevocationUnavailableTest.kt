package dropgate.order

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import dropgate.common.authentication.RedisRevokedAccessTokens
import dropgate.common.authentication.RevokedAccessTokens
import dropgate.common.authentication.TestTokenIssuer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import java.net.ServerSocket
import java.time.Clock

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration::class, OrderRevocationUnavailableTest.UnavailableRevocationConfiguration::class)
class OrderRevocationUnavailableTest @Autowired constructor(private val http: RestTestClient) {
    @Test
    fun `무효화 Redis 연결 거절은 Bearer 요청에 503과 재시도 헤더를 돌려준다`() {
        http.get().uri("/api/orders/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${issuer.issueAccessToken()}").exchange()
            .expectStatus().isEqualTo(503).expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "2")
            .expectBody().jsonPath("$.code").isEqualTo("AUTH_UNAVAILABLE")
    }

    @Test
    fun `무효화 Redis 연결이 거절돼도 공개 드롭 목록은 Bearer와 무관하게 200이다`() {
        http.get().uri("/api/drops").header(HttpHeaders.AUTHORIZATION, "Bearer ${issuer.issueAccessToken()}").exchange().expectStatus().isOk
    }

    @TestConfiguration(proxyBeanMethods = false)
    class UnavailableRevocationConfiguration(connectionFactory: LettuceConnectionFactory) : DisposableBean {
        private val unavailable = LettuceConnectionFactory(
            RedisStandaloneConfiguration("127.0.0.1", ServerSocket(0).use { it.localPort }),
            connectionFactory.clientConfiguration,
        ).apply { afterPropertiesSet() }

        @Bean
        @Primary
        fun unavailableRevokedAccessTokens(): RevokedAccessTokens = RedisRevokedAccessTokens(StringRedisTemplate(unavailable))

        override fun destroy() = unavailable.destroy()
    }

    companion object {
        private val issuer = TestTokenIssuer(Clock.systemUTC())
        private val wiremock = WireMockServer(0).apply {
            start()
            stubFor(WireMock.get("/.well-known/jwks.json").willReturn(WireMock.okJson(issuer.createJwkSetJson())))
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureJwks(registry: DynamicPropertyRegistry) {
            registry.add("dropgate.auth.jwks-uri") { wiremock.baseUrl() + "/.well-known/jwks.json" }
        }

        @JvmStatic
        @AfterAll
        fun stopJwks() = wiremock.stop()
    }
}
