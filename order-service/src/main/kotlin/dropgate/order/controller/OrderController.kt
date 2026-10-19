package dropgate.order.controller

import dropgate.common.authentication.AuthenticatedUser
import dropgate.order.dto.OrderPageResponse
import dropgate.order.dto.OrderResponse
import dropgate.order.service.MyOrderService
import dropgate.order.service.OrderCancelService
import dropgate.order.service.OrderCursor
import dropgate.order.service.PurchaseService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

private const val DEFAULT_PAGE_LIMIT = "20"
private const val MIN_PAGE_LIMIT = 1L
private const val MAX_PAGE_LIMIT = 100L

@RestController
class OrderController(private val purchases: PurchaseService, private val cancellations: OrderCancelService, private val myOrders: MyOrderService) {
    @PostMapping("/api/drops/{dropId}/orders")
    fun purchase(user: AuthenticatedUser, @PathVariable("dropId") dropId: UUID): ResponseEntity<OrderResponse> {
        val order = OrderResponse.createFrom(purchases.purchase(dropId, user.id))
        return ResponseEntity.created(URI.create("/api/orders/${order.id}")).body(order)
    }

    @PostMapping("/api/orders/{orderId}/cancel")
    fun cancel(user: AuthenticatedUser, @PathVariable("orderId") orderId: UUID): OrderResponse = OrderResponse.createFrom(cancellations.cancel(orderId, user.id))

    @GetMapping("/api/orders/me")
    fun getMyOrders(
        user: AuthenticatedUser,
        @RequestParam("cursor", required = false) cursor: String?,
        @RequestParam("limit", defaultValue = DEFAULT_PAGE_LIMIT)
        @Min(MIN_PAGE_LIMIT, message = "limit은 {value} 이상이어야 합니다")
        @Max(MAX_PAGE_LIMIT, message = "limit은 {value} 이하여야 합니다")
        limit: Int,
    ): OrderPageResponse {
        val page = if (cursor == null) myOrders.getFirstOrders(user.id, limit) else myOrders.getOrdersBefore(user.id, OrderCursor.decode(cursor), limit)
        return OrderPageResponse(page.items.map(OrderResponse::createFrom), page.nextCursor)
    }
}
