package dropgate.order.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private const val OUTBOX_RELAY_INTERVAL_MILLIS = 500L

@Component
@ConditionalOnBooleanProperty("dropgate.outbox.relay.enabled", matchIfMissing = true)
class OutboxRelayScheduler(private val relay: OutboxRelayService) {
    @Scheduled(fixedDelay = OUTBOX_RELAY_INTERVAL_MILLIS, timeUnit = TimeUnit.MILLISECONDS)
    fun publishPendingEvents() {
        relay.publishPendingEvents()
    }
}
