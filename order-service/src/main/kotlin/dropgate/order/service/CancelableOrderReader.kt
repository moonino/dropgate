package dropgate.order.service

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.OrderEntity
import dropgate.order.entity.OrderStatus
import dropgate.order.repository.DropRepository
import dropgate.order.repository.OrderRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class CancelableOrderReader(private val orders: OrderRepository, private val drops: DropRepository, private val clock: Clock) {
    fun lockCancelableOrder(orderId: UUID, userId: UUID): CancelableOrder {
        val order = orders.findForUpdate(orderId).orElseThrow { DropgateException(ErrorCode.ORDER_NOT_FOUND, "주문이 없습니다: $orderId") }
        if (order.userId != userId) throw DropgateException(ErrorCode.FORBIDDEN, "사용자 $userId 는 남의 주문 $orderId 를 취소할 수 없습니다")
        if (order.status == OrderStatus.CANCELED) throw DropgateException(ErrorCode.ORDER_ALREADY_CANCELED, "이미 취소한 주문입니다: $orderId")
        val drop = drops.findById(order.dropId).orElseThrow { IllegalStateException("주문 $orderId 의 드롭 ${order.dropId} 가 없습니다") }
        val canceledAt = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        if (!canceledAt.isBefore(drop.closeAt)) throw DropgateException(ErrorCode.DROP_CLOSED, "마감 시각 ${drop.closeAt} 이 지난 드롭 ${drop.id} 의 주문은 취소할 수 없습니다: $orderId")
        return CancelableOrder(order, drop.name, canceledAt)
    }
}

data class CancelableOrder(val order: OrderEntity, val dropName: String, val canceledAt: Instant)
