package dropgate.order

import dropgate.order.configuration.OrderProperties
import dropgate.order.repository.DropStockRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import java.util.UUID
import kotlin.time.measureTime

private const val STOCK_REDIS_PORT = 6379

@Import(StockTimeoutResponseTest.UnresponsiveStocks::class)
class StockTimeoutResponseTest : RedisOrderApiIntegrationTest() {
    @Autowired
    lateinit var isolated: UnresponsiveStocks

    @Test
    fun `재고 Redis만 응답하지 않으면 200ms 근처에 503과 재시도 헤더를 돌려준다`() {
        val dropId = insertDropWithStock()
        val token = tokens.issueAccessToken(UUID.randomUUID())
        http.get().uri("/api/orders/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token").exchange().expectStatus().isOk
        isolated.redis.opsForValue().set("connection-warmup", "1")
        val docker = DockerClientFactory.instance().client()
        docker.pauseContainerCmd(isolated.container.containerId).exec()
        try {
            val elapsed = measureTime {
                purchaseWithToken(dropId, token).expectStatus().isEqualTo(503)
                    .expectHeader().valueEquals("Retry-After", "2")
                    .expectBody().jsonPath("$.code").isEqualTo("STOCK_UNAVAILABLE")
            }
            println("재고 구매 HTTP 응답 시간: $elapsed, 명령 제한: ${isolated.stockConnections.clientConfiguration.commandTimeout}")
            assertThat(elapsed.inWholeMilliseconds).isBetween(150L, 2000L)
        } finally {
            docker.unpauseContainerCmd(isolated.container.containerId).exec()
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class UnresponsiveStocks(shared: LettuceConnectionFactory) : DisposableBean {
        val container: GenericContainer<*> = GenericContainer<Nothing>("redis:7.4-alpine").withExposedPorts(STOCK_REDIS_PORT).apply { start() }
        val stockConnections = LettuceConnectionFactory(
            RedisStandaloneConfiguration(container.host, container.getMappedPort(STOCK_REDIS_PORT)),
            shared.clientConfiguration,
        ).apply { afterPropertiesSet() }
        val redis = StringRedisTemplate(stockConnections)

        @Bean
        @Primary
        fun unresponsiveStocks(settings: OrderProperties): DropStockRepository = DropStockRepository(redis, settings)

        override fun destroy() {
            stockConnections.destroy()
            container.stop()
        }
    }
}
