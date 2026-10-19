package dropgate.order

import dropgate.order.repository.DropRepository
import dropgate.order.repository.DropStockRepository
import dropgate.order.service.DropSaleStatusService
import dropgate.order.service.RedisOrderCancelService
import dropgate.order.service.RedisOrderConfirmationService
import dropgate.order.service.RedisPurchaseFlow
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.util.UUID

abstract class RedisFlowIntegrationTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var stocks: DropStockRepository

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var drops: DropRepository

    @Autowired
    lateinit var confirmations: RedisOrderConfirmationService

    @Autowired
    lateinit var cancellations: RedisOrderCancelService

    @Autowired
    lateinit var statuses: DropSaleStatusService

    @Autowired
    lateinit var clock: Clock

    lateinit var flow: RedisPurchaseFlow
    lateinit var metrics: SimpleMeterRegistry

    @BeforeEach
    fun createRedisFlow() {
        metrics = SimpleMeterRegistry()
        flow = RedisPurchaseFlow(stocks, confirmations, cancellations, drops, statuses, metrics, clock)
    }

    @AfterEach
    fun closeMetrics() {
        metrics.close()
    }

    fun initializeStock(total: Int = 1): UUID {
        val dropId = insertDropWithStock(totalQuantity = total)
        stocks.initialize(drops.findById(dropId).orElseThrow())
        return dropId
    }

    fun readStock(dropId: UUID): String = checkNotNull(redis.opsForValue().get("drop:$dropId:stock"))
}
