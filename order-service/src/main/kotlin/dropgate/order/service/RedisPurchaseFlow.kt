package dropgate.order.service

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.repository.DropRepository
import dropgate.order.repository.DropStockRepository
import dropgate.order.repository.StockReservation
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

private const val COMPENSATION_FAILED_METRIC = "dropgate_stock_compensation_failed_total"
private const val COMMIT_UNKNOWN_METRIC = "dropgate_order_commit_unknown_total"
private val log = LoggerFactory.getLogger(RedisPurchaseFlow::class.java)

@Service
@ConditionalOnProperty(prefix = "dropgate.order", name = ["purchase-path"], havingValue = "redis", matchIfMissing = true)
class RedisPurchaseFlow(
    private val stocks: DropStockRepository,
    private val confirmations: RedisOrderConfirmationService,
    private val cancellations: RedisOrderCancelService,
    private val drops: DropRepository,
    private val statuses: DropSaleStatusService,
    private val metrics: MeterRegistry,
    private val clock: Clock,
) : PurchaseFlow {
    override fun purchase(dropId: UUID, userId: UUID): OrderWithDropName {
        val reservation = stocks.purchase(dropId, userId, clock.instant())
        if (reservation is StockReservation.Rejected) throw rejectPurchase(dropId, userId, reservation.errorCode)
        val confirmed = try {
            confirmations.confirm(dropId, userId)
        } catch (exception: OrderConfirmationRolledBackException) {
            restoreStock(dropId, userId) { stocks.compensate(dropId, userId) }
            throw DropgateException(exception.errorCode, "드롭 $dropId 사용자 $userId 의 주문 확정을 롤백했습니다", cause = exception)
        } catch (exception: RuntimeException) {
            metrics.counter(COMMIT_UNKNOWN_METRIC, "drop", dropId.toString()).increment()
            log.error("드롭 {} 사용자 {} 의 DB 커밋 결과가 불확실합니다. 예약을 유지하며 수동 대조가 필요합니다", dropId, userId, exception)
            throw DropgateException(ErrorCode.ORDER_PERSIST_FAILED, "드롭 $dropId 사용자 $userId 의 주문 확정 결과를 확인하지 못했습니다", cause = exception)
        }
        trySynchronizeSaleStatus(dropId)
        return confirmed
    }

    override fun cancel(orderId: UUID, userId: UUID): OrderWithDropName {
        val canceled = cancellations.cancel(orderId, userId)
        restoreStock(canceled.order.dropId, userId) { stocks.cancel(canceled.order.dropId, userId) }
        return canceled
    }

    private fun rejectPurchase(dropId: UUID, userId: UUID, code: ErrorCode): DropgateException {
        if (code == ErrorCode.DROP_NOT_OPEN && !drops.existsById(dropId)) return DropgateException(ErrorCode.DROP_NOT_FOUND, "드롭이 없습니다: $dropId")
        return DropgateException(code, "드롭 $dropId 사용자 $userId 구매를 Redis가 거절했습니다: $code")
    }

    private fun restoreStock(dropId: UUID, userId: UUID, restore: () -> Unit) {
        try {
            restore()
        } catch (exception: RuntimeException) {
            metrics.counter(COMPENSATION_FAILED_METRIC, "drop", dropId.toString()).increment()
            log.error("드롭 {} 사용자 {} 의 재고 복구에 실패했습니다. 원래 응답을 유지하고 수동 대조가 필요합니다", dropId, userId, exception)
            return
        }
        trySynchronizeSaleStatus(dropId)
    }

    private fun trySynchronizeSaleStatus(dropId: UUID) {
        try {
            statuses.synchronizeSaleStatus(dropId)
        } catch (exception: RuntimeException) {
            log.warn("드롭 {} 의 DB 판매 상태 동기화에 실패했습니다. 원래 응답을 유지합니다", dropId, exception)
        }
    }
}
