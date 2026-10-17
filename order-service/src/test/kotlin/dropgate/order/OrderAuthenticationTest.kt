package dropgate.order

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import dropgate.common.authentication.AuthenticatedUser
import dropgate.common.authentication.TestTokenIssuer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.util.UUID

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration::class, OrderAuthenticationTest.AuthenticationProbeController::class)
class OrderAuthenticationTest @Autowired constructor(private val http: RestTestClient) {
    @Test
    fun `토큰 없이 Bearer 엔드포인트를 부르면 401 UNAUTHENTICATED를 돌려준다`() {
        http.get().uri(PROBE_PATH).exchange()
            .expectStatus().isUnauthorized
            .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
    }

    @Test
    fun `유효한 토큰이면 컨트롤러가 현재 사용자를 받는다`() {
        val userId = UUID.randomUUID()
        http.get().uri(PROBE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer ${issuer.issueAccessToken(userId)}").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.id").isEqualTo(userId.toString())
    }

    @Test
    fun `JWKS에 없는 키로 서명한 토큰은 401로 거부한다`() {
        val forged = TestTokenIssuer(Clock.systemUTC()).issueAccessToken()
        http.get().uri(PROBE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $forged").exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `USER 토큰으로 ADMIN 확인을 통과하지 못하면 403 FORBIDDEN을 돌려준다`() {
        http.get().uri(ADMIN_PROBE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer ${issuer.issueAccessToken(role = "USER")}").exchange()
            .expectStatus().isForbidden
            .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN")
    }

    @Test
    fun `드롭 조회는 토큰 없이 필터를 통과한다`() {
        http.get().uri("/api/drops/${UUID.randomUUID()}").exchange()
            .expectStatus().isNotFound
    }

    @RestController
    class AuthenticationProbeController {
        @GetMapping(PROBE_PATH)
        fun getCurrentUser(user: AuthenticatedUser): Map<String, String> = mapOf("id" to user.id.toString())

        @GetMapping(ADMIN_PROBE_PATH)
        fun getAdmin(user: AuthenticatedUser): Map<String, String> {
            user.requireAdmin()
            return mapOf("id" to user.id.toString())
        }
    }

    companion object {
        private const val JWKS_PATH = "/.well-known/jwks.json"
        private const val PROBE_PATH = "/test/me"
        private const val ADMIN_PROBE_PATH = "/test/admin"
        private val issuer = TestTokenIssuer(Clock.systemUTC())
        private val wiremock = WireMockServer(0).apply {
            start()
            stubFor(WireMock.get(JWKS_PATH).willReturn(WireMock.okJson(issuer.createJwkSetJson())))
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureJwks(registry: DynamicPropertyRegistry) {
            registry.add("dropgate.auth.jwks-uri") { wiremock.baseUrl() + JWKS_PATH }
        }

        @JvmStatic
        @AfterAll
        fun stopJwks() = wiremock.stop()
    }
}
