package dropgate.order

import dropgate.order.configuration.OrderProperties
import dropgate.order.repository.DropStockRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.net.ServerSocket
import java.util.UUID

@Import(StockUnavailableResponseTest.UnavailableStocks::class)
class StockUnavailableResponseTest : RedisOrderApiIntegrationTest() {
    @Test
    fun `인증 Redis는 정상이고 재고 연결만 거절되면 503과 재시도 헤더를 돌려준다`() {
        val dropId = insertDropWithStock()

        purchase(dropId, UUID.randomUUID()).expectStatus().isEqualTo(503)
            .expectHeader().valueEquals("Retry-After", "2")
            .expectBody().jsonPath("$.code").isEqualTo("STOCK_UNAVAILABLE")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class UnavailableStocks(shared: LettuceConnectionFactory) : DisposableBean {
        private val unavailable = LettuceConnectionFactory(
            RedisStandaloneConfiguration("127.0.0.1", ServerSocket(0).use { it.localPort }),
            shared.clientConfiguration,
        ).apply { afterPropertiesSet() }

        @Bean
        @Primary
        fun unavailableStocks(settings: OrderProperties): DropStockRepository = DropStockRepository(StringRedisTemplate(unavailable), settings)

        override fun destroy() = unavailable.destroy()
    }
}
