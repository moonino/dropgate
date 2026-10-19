package dropgate.order.service

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.DropStatus
import dropgate.order.repository.DropRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val NO_ROWS = 0

@Service
class DbOrderConfirmationService(
    private val drops: DropRepository,
    private val confirmations: OrderConfirmationRecorder,
) {
    @Transactional
    fun confirm(dropId: UUID, userId: UUID): ConfirmedOrder {
        if (drops.decrementRemainingQuantityIfOpen(dropId) == NO_ROWS) throw rejectPurchase(dropId)
        val drop = drops.findById(dropId).orElseThrow { IllegalStateException("재고를 차감한 드롭을 다시 읽지 못했습니다: $dropId") }
        val confirmed = confirmations.recordConfirmed(drop, userId)
        return ConfirmedOrder(confirmed, stockExhausted = drop.remainingQuantity == 0)
    }

    private fun rejectPurchase(dropId: UUID): DropgateException {
        val drop = drops.findById(dropId).orElseThrow { DropgateException(ErrorCode.DROP_NOT_FOUND, "드롭이 없습니다: $dropId") }
        return when (drop.status) {
            DropStatus.SCHEDULED, DropStatus.CLOSED -> DropgateException(ErrorCode.DROP_NOT_OPEN, "판매 중이 아닌 드롭입니다: $dropId ${drop.status}")
            DropStatus.OPEN, DropStatus.SOLD_OUT -> DropgateException(ErrorCode.SOLD_OUT, "재고가 없는 드롭입니다: $dropId ${drop.status} 남은 수량 ${drop.remainingQuantity}")
        }
    }
}

data class ConfirmedOrder(val order: OrderWithDropName, val stockExhausted: Boolean)
