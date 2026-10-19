package dropgate.order

import dropgate.order.repository.DropStockReader
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.net.ServerSocket

@Import(DropStockUnavailableResponseTest.UnavailableStocks::class)
class DropStockUnavailableResponseTest : DropApiIntegrationTest() {
    @ParameterizedTest
    @CsvSource("/api/drops, $.items[0].remainingQuantity", "/api/drops/%s, $.remainingQuantity")
    fun `재고 Redis 연결이 거절돼도 200과 수량 null을 돌려준다`(path: String, quantityPath: String) {
        val id = insertDrop(status = "OPEN")

        http.get().uri(path.format(id)).exchange().expectStatus().isOk.expectBody().jsonPath(quantityPath).isEmpty
    }

    @TestConfiguration(proxyBeanMethods = false)
    class UnavailableStocks(connections: LettuceConnectionFactory) : DisposableBean {
        private val unavailable = LettuceConnectionFactory(
            RedisStandaloneConfiguration("127.0.0.1", ServerSocket(0).use { it.localPort }),
            connections.clientConfiguration,
        ).apply { afterPropertiesSet() }

        @Bean
        @Primary
        fun unavailableStocks(): DropStockReader = DropStockReader(StringRedisTemplate(unavailable))

        override fun destroy() = unavailable.destroy()
    }
}
