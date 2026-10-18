package dropgate.order.service

import dropgate.order.entity.OrderEntity
import dropgate.order.repository.DropRepository
import dropgate.order.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MyOrderService(private val orders: OrderRepository, private val drops: DropRepository) {
    @Transactional(readOnly = true)
    fun getFirstOrders(userId: UUID, limit: Int): OrderPageResponse = toPage(orders.findLatestByUser(userId, limit + 1), limit)

    @Transactional(readOnly = true)
    fun getOrdersBefore(userId: UUID, cursor: OrderCursor, limit: Int): OrderPageResponse = toPage(orders.findLatestByUserBefore(userId, cursor.confirmedAt, cursor.id, limit + 1), limit)

    private fun toPage(fetched: List<OrderEntity>, limit: Int): OrderPageResponse {
        val items = fetched.take(limit)
        val dropNames = drops.findAllById(items.map { it.dropId }.toSet()).associate { it.id to it.name }
        val nextCursor = if (fetched.size > limit) OrderCursor(items.last().confirmedAt, items.last().id).encode() else null
        return OrderPageResponse(items.map { OrderResponse.createFrom(it, dropNames.getValue(it.dropId)) }, nextCursor)
    }
}

data class OrderPageResponse(val items: List<OrderResponse>, val nextCursor: String?)
