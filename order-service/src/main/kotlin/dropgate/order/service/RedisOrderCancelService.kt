package dropgate.order.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RedisOrderCancelService(
    private val cancelableOrders: CancelableOrderReader,
    private val history: OrderHistoryRecorder,
) {
    @Transactional(propagation = REQUIRES_NEW)
    fun cancel(orderId: UUID, userId: UUID): OrderWithDropName {
        val cancelable = cancelableOrders.lockCancelableOrder(orderId, userId)
        cancelable.order.cancel(cancelable.canceledAt)
        history.recordCanceled(cancelable.order, cancelable.dropName)
        return OrderWithDropName(cancelable.order, cancelable.dropName)
    }
}
