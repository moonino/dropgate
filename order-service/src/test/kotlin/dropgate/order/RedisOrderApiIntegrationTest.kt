package dropgate.order

import dropgate.order.repository.DropRepository
import dropgate.order.repository.DropStockRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.TestPropertySource
import java.util.UUID

@TestPropertySource(properties = ["dropgate.order.purchase-path=redis"])
abstract class RedisOrderApiIntegrationTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var stocks: DropStockRepository

    @Autowired
    lateinit var drops: DropRepository

    @Autowired
    lateinit var redis: StringRedisTemplate

    fun initializeStock(total: Int = 1): UUID {
        val dropId = insertDropWithStock(totalQuantity = total)
        stocks.initialize(drops.findById(dropId).orElseThrow())
        return dropId
    }

    fun readStock(dropId: UUID): String = checkNotNull(redis.opsForValue().get("drop:$dropId:stock"))
}
