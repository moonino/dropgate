package dropgate.order.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.TimeUnit

private const val TRANSITION_INTERVAL_SECONDS = 1L

@Component
class DropStatusTransitionScheduler(private val transitions: DropStatusTransitionService, private val clock: Clock) {
    @Scheduled(fixedDelay = TRANSITION_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun transitionDueDrops() {
        transitions.transitionDueDrops(clock.instant())
    }
}
