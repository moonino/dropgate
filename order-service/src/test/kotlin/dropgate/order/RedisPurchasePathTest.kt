package dropgate.order

import dropgate.order.configuration.OrderProperties
import dropgate.order.service.PurchaseFlow
import dropgate.order.service.RedisPurchaseFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class RedisPurchasePathTest : DropApiIntegrationTest() {
    @Autowired
    lateinit var flow: PurchaseFlow

    @Autowired
    lateinit var settings: OrderProperties

    @Test
    fun `기본 설정은 구매와 취소를 함께 담는 Redis 흐름을 선택한다`() {
        assertThat(settings.purchasePath).isEqualTo(OrderProperties.PurchasePath.REDIS)
        assertThat(flow).isInstanceOf(RedisPurchaseFlow::class.java)
    }
}
