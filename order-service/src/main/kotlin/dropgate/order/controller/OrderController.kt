package dropgate.order.controller

import dropgate.common.authentication.AuthenticatedUser
import dropgate.order.service.OrderResponse
import dropgate.order.service.PurchaseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class OrderController(private val purchases: PurchaseService) {
    @PostMapping("/api/drops/{dropId}/orders")
    fun purchase(user: AuthenticatedUser, @PathVariable("dropId") dropId: UUID): ResponseEntity<OrderResponse> {
        val order = purchases.purchase(dropId, user.id)
        return ResponseEntity.created(URI.create("/api/orders/${order.id}")).body(order)
    }
}
