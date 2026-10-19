package dropgate.order.service

import dropgate.common.UuidV7Generator
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.OrderEntity
import dropgate.order.repository.DropRepository
import dropgate.order.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val NO_ROWS = 0

@Service
class RedisOrderConfirmationService(
    private val drops: DropRepository,
    private val orders: OrderRepository,
    private val history: OrderHistoryRecorder,
    private val identifiers: UuidV7Generator,
    private val clock: Clock,
) {
    @Transactional(propagation = REQUIRES_NEW)
    fun confirm(dropId: UUID, userId: UUID): OrderWithDropName = try {
        val drop = drops.findById(dropId).orElseThrow { IllegalStateException("Redis로 예약한 드롭이 DB에 없습니다: $dropId") }
        val order = OrderEntity.confirm(identifiers.generate(), dropId, userId, clock.instant().truncatedTo(ChronoUnit.MILLIS))
        if (orders.insertConfirmedUnlessPurchased(order.id, order.dropId, order.userId, order.quantity, order.confirmedAt) == NO_ROWS) {
            throw DropgateException(ErrorCode.ALREADY_PURCHASED, "사용자 $userId 는 드롭 $dropId 를 이미 구매했습니다")
        }
        history.recordConfirmed(order, drop.name)
        orders.flush()
        OrderWithDropName(order, drop.name)
    } catch (exception: RuntimeException) {
        val code = if (exception is DropgateException && exception.errorCode == ErrorCode.ALREADY_PURCHASED) ErrorCode.ALREADY_PURCHASED else ErrorCode.ORDER_PERSIST_FAILED
        throw OrderConfirmationRolledBackException(code, dropId, userId, exception)
    }
}
