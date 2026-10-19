package dropgate.order.service

import dropgate.common.UuidV7Generator
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.DropEntity
import dropgate.order.entity.OrderEntity
import dropgate.order.repository.OrderRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val NO_INSERTED_ROWS = 0

@Service
class OrderConfirmationRecorder(
    private val orders: OrderRepository,
    private val history: OrderHistoryRecorder,
    private val identifiers: UuidV7Generator,
    private val clock: Clock,
) {
    fun recordConfirmed(drop: DropEntity, userId: UUID): OrderWithDropName {
        val order = OrderEntity.confirm(identifiers.generate(), drop.id, userId, clock.instant().truncatedTo(ChronoUnit.MILLIS))
        if (orders.insertConfirmedUnlessPurchased(order.id, order.dropId, order.userId, order.quantity, order.confirmedAt) == NO_INSERTED_ROWS) {
            throw DropgateException(ErrorCode.ALREADY_PURCHASED, "사용자 $userId 는 드롭 ${drop.id} 를 이미 구매했습니다")
        }
        history.recordConfirmed(order, drop.name)
        return OrderWithDropName(order, drop.name)
    }
}
