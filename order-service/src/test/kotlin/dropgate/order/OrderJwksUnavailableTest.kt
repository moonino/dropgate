package dropgate.order

import dropgate.common.authentication.TestTokenIssuer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Clock

@SpringBootTest(classes = [OrderServiceApplication::class], webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration::class)
class OrderJwksUnavailableTest {
    @Autowired
    lateinit var http: RestTestClient

    @Test
    fun `JWKS를 받을 수 없으면 Bearer 엔드포인트는 503 AUTH_UNAVAILABLE과 Retry-After를 돌려준다`() {
        http.get().uri("/api/orders/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestTokenIssuer(Clock.systemUTC()).issueAccessToken()}")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "2")
            .expectBody().jsonPath("$.code").isEqualTo("AUTH_UNAVAILABLE")
    }
}
