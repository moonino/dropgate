package dropgate.order.service

import dropgate.order.entity.OutboxEntity
import dropgate.order.entity.OutboxStatus
import dropgate.order.event.OrderEventStream
import dropgate.order.repository.OutboxRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit

private const val OUTBOX_BATCH_SIZE = 100
private val log = LoggerFactory.getLogger(OutboxRelayService::class.java)

@Service
class OutboxRelayService(private val outbox: OutboxRepository, private val events: OrderEventStream, private val clock: Clock, meters: MeterRegistry) {
    private val failures = meters.counter("dropgate_outbox_failed_total")

    @Transactional
    fun publishPendingEvents() {
        for (event in outbox.findPendingForUpdate(OUTBOX_BATCH_SIZE)) {
            try {
                events.publish(event)
            } catch (exception: RuntimeException) {
                recordFailure(event, exception)
                continue
            }
            event.markPublished(clock.instant().truncatedTo(ChronoUnit.MILLIS))
        }
    }

    private fun recordFailure(event: OutboxEntity, exception: RuntimeException) {
        event.recordFailure()
        if (event.status == OutboxStatus.FAILED) {
            failures.increment()
            log.error("outbox {} 의 {} 발행이 {}회 실패해 FAILED로 남깁니다", event.id, event.eventType, event.attempts, exception)
        }
    }
}
