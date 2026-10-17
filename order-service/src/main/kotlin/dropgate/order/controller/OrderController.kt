package dropgate.order.controller

import dropgate.common.authentication.AuthenticatedUser
import dropgate.order.service.OrderCancelService
import dropgate.order.service.OrderResponse
import dropgate.order.service.PurchaseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class OrderController(private val purchases: PurchaseService, private val cancellations: OrderCancelService) {
    @PostMapping("/api/drops/{dropId}/orders")
    fun purchase(user: AuthenticatedUser, @PathVariable("dropId") dropId: UUID): ResponseEntity<OrderResponse> {
        val order = purchases.purchase(dropId, user.id)
        return ResponseEntity.created(URI.create("/api/orders/${order.id}")).body(order)
    }

    @PostMapping("/api/orders/{orderId}/cancel")
    fun cancel(user: AuthenticatedUser, @PathVariable("orderId") orderId: UUID): OrderResponse = cancellations.cancel(orderId, user.id)
}
