package dropgate.order

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import dropgate.common.authentication.TestTokenIssuer
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration::class)
abstract class DropApiIntegrationTest {
    @Autowired
    lateinit var http: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var transactions: TransactionTemplate

    @BeforeEach
    fun truncateOrdersSchemaWithoutTriggers() {
        transactions.executeWithoutResult {
            jdbc.sql("SET LOCAL session_replication_role = replica").update()
            jdbc.sql("TRUNCATE orders.outbox, orders.order_ledger, orders.orders, orders.drops").update()
        }
    }

    fun postDrop(body: String, token: String): RestTestClient.ResponseSpec = http.post().uri("/api/admin/drops")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    companion object {
        private const val JWKS_PATH = "/.well-known/jwks.json"
        val tokens = TestTokenIssuer(Clock.systemUTC())
        private val jwks = WireMockServer(0).apply {
            start()
            stubFor(WireMock.get(JWKS_PATH).willReturn(WireMock.okJson(tokens.createJwkSetJson())))
            Runtime.getRuntime().addShutdownHook(Thread(::stop))
        }

        fun issueAdminToken(adminId: UUID = UUID.randomUUID()): String = tokens.issueAccessToken(adminId, role = "ADMIN")

        @JvmStatic
        @DynamicPropertySource
        fun configureJwks(registry: DynamicPropertyRegistry) {
            registry.add("dropgate.auth.jwks-uri") { jwks.baseUrl() + JWKS_PATH }
        }
    }
}
