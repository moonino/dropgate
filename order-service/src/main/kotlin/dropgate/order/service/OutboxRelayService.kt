package dropgate.order.service

import dropgate.order.event.OrderEventStream
import dropgate.order.repository.OutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit

private const val OUTBOX_BATCH_SIZE = 100

@Service
class OutboxRelayService(private val outbox: OutboxRepository, private val events: OrderEventStream, private val clock: Clock) {
    @Transactional
    fun publishPendingEvents() {
        outbox.findPendingForUpdate(OUTBOX_BATCH_SIZE).forEach { event ->
            events.publish(event)
            event.markPublished(clock.instant().truncatedTo(ChronoUnit.MILLIS))
        }
    }
}
