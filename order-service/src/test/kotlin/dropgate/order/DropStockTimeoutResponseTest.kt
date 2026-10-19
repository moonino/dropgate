package dropgate.order

import dropgate.order.repository.DropStockReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import kotlin.time.measureTime

private const val REDIS_PORT = 6379

@Import(DropStockTimeoutResponseTest.UnresponsiveStocks::class)
class DropStockTimeoutResponseTest : DropApiIntegrationTest() {
    @Autowired
    lateinit var isolated: UnresponsiveStocks

    @ParameterizedTest
    @CsvSource("/api/drops, $.items[0].remainingQuantity", "/api/drops/%s, $.remainingQuantity")
    fun `재고 Redis가 응답하지 않으면 200ms 근처에 200과 수량 null을 돌려준다`(path: String, quantityPath: String) {
        val id = insertDrop(status = "OPEN")
        isolated.redis.opsForValue().set("drop:$id:stock", "3")
        http.get().uri(path.format(id)).exchange().expectStatus().isOk.expectBody().jsonPath(quantityPath).isEqualTo(3)
        val docker = DockerClientFactory.instance().client()
        docker.pauseContainerCmd(isolated.container.containerId).exec()
        try {
            val elapsed = measureTime { http.get().uri(path.format(id)).exchange().expectStatus().isOk.expectBody().jsonPath(quantityPath).isEmpty }
            println("재고 조회 HTTP 응답 시간: $elapsed, 명령 제한: ${isolated.connections.clientConfiguration.commandTimeout}")
            assertThat(elapsed.inWholeMilliseconds).isBetween(150L, 2000L)
        } finally {
            docker.unpauseContainerCmd(isolated.container.containerId).exec()
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class UnresponsiveStocks(connections: LettuceConnectionFactory) : DisposableBean {
        val container: GenericContainer<*> = GenericContainer<Nothing>("redis:7.4-alpine").withExposedPorts(REDIS_PORT).apply { start() }
        val connections = LettuceConnectionFactory(
            RedisStandaloneConfiguration(container.host, container.getMappedPort(REDIS_PORT)),
            connections.clientConfiguration,
        ).apply { afterPropertiesSet() }
        val redis = StringRedisTemplate(this.connections)

        @Bean
        @Primary
        fun unresponsiveStocks(): DropStockReader = DropStockReader(redis)

        override fun destroy() {
            connections.destroy()
            container.stop()
        }
    }
}
