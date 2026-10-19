package dropgate.order.service

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.repository.DropRepository
import dropgate.order.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RedisOrderConfirmationService(
    private val drops: DropRepository,
    private val orders: OrderRepository,
    private val confirmations: OrderConfirmationRecorder,
) {
    @Transactional(propagation = REQUIRES_NEW)
    fun confirm(dropId: UUID, userId: UUID): OrderWithDropName = try {
        val drop = drops.findById(dropId).orElseThrow { IllegalStateException("Redis로 예약한 드롭이 DB에 없습니다: $dropId") }
        val confirmed = confirmations.recordConfirmed(drop, userId)
        orders.flush()
        confirmed
    } catch (exception: RuntimeException) {
        val code = if (exception is DropgateException && exception.errorCode == ErrorCode.ALREADY_PURCHASED) ErrorCode.ALREADY_PURCHASED else ErrorCode.ORDER_PERSIST_FAILED
        throw OrderConfirmationRolledBackException(code, dropId, userId, exception)
    }
}
