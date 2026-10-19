package dropgate.order

import dropgate.order.repository.DropRepository
import dropgate.order.repository.DropStockRepository
import dropgate.order.service.DropSaleStatusService
import dropgate.order.service.PurchaseFlow
import dropgate.order.service.RedisOrderCancelService
import dropgate.order.service.RedisOrderConfirmationService
import dropgate.order.service.RedisPurchaseFlow
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock

@TestConfiguration(proxyBeanMethods = false)
class RedisPurchaseHttpConfiguration {
    @Bean
    @Primary
    fun redisPurchaseFlow(
        stocks: DropStockRepository,
        confirmations: RedisOrderConfirmationService,
        cancellations: RedisOrderCancelService,
        drops: DropRepository,
        statuses: DropSaleStatusService,
        metrics: MeterRegistry,
        clock: Clock,
    ): PurchaseFlow = RedisPurchaseFlow(stocks, confirmations, cancellations, drops, statuses, metrics, clock)
}
