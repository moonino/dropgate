package dropgate.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(classes = [AuthServiceApplication::class], webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration::class)
class AuthServiceHealthTest : JwtIntegrationTest() {
    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    fun `헬스 엔드포인트가 200을 돌려준다`() {
        restTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk
    }

    @Test
    fun `readiness 프로브가 200을 돌려준다`() {
        restTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk
    }

    @Test
    fun `liveness 프로브가 200을 돌려준다`() {
        restTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk
    }
}
