package dropgate.order.service

import dropgate.common.error.DropgateException
import dropgate.order.repository.DropStockRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.TimeUnit

private const val TRANSITION_INTERVAL_SECONDS = 1L
private val log = LoggerFactory.getLogger(DropStatusTransitionScheduler::class.java)

@Component
@ConditionalOnBooleanProperty("dropgate.drops.status-scheduler.enabled", matchIfMissing = true)
class DropStatusTransitionScheduler(
    private val transitions: DropStatusTransitionService,
    private val initializations: DropStockInitializationService,
    private val stocks: DropStockRepository,
    private val clock: Clock,
) {
    @Scheduled(fixedDelay = TRANSITION_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun transitionDueDrops() {
        transitions.transitionDueDrops(clock.instant())
        initializeClaimedStocks()
    }

    private fun initializeClaimedStocks() {
        initializations.claimOpenDrops().forEach { drop ->
            try {
                stocks.initialize(drop)
            } catch (exception: DropgateException) {
                log.warn("드롭 {} 의 Redis 초기화에 실패했습니다. 권한은 유지하며 자동 재시도하지 않습니다", drop.id, exception)
            }
        }
    }
}
