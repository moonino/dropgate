package dropgate.order.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dropgate.order")
data class OrderProperties(val purchasePath: PurchasePath = PurchasePath.REDIS, val queue: Queue = Queue()) {
    enum class PurchasePath {
        REDIS,
        DB,
    }

    data class Queue(val enabled: Boolean = false)
}
