package dropgate.order.service

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PurchaseService(private val confirmations: OrderConfirmationService) {
    fun purchase(dropId: UUID, userId: UUID): OrderResponse {
        val confirmed = confirmations.confirm(dropId, userId)
        return confirmed.order
    }
}
