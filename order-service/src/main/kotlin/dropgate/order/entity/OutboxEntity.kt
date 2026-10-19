package dropgate.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

private const val MAX_PUBLISH_ATTEMPTS = 10

@Entity
@Table(name = "outbox", schema = "orders")
class OutboxEntity(
    @Column(name = "aggregate_type", nullable = false, length = 30) val aggregateType: String,
    @Column(name = "aggregate_id", nullable = false) val aggregateId: UUID,
    @Column(name = "event_type", nullable = false, length = 50) val eventType: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) val payload: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    @Column(nullable = false)
    var attempts: Int = 0
        protected set

    @Column(name = "published_at")
    var publishedAt: Instant? = null
        protected set

    fun markPublished(at: Instant) {
        status = OutboxStatus.PUBLISHED
        publishedAt = at
    }

    fun recordFailure() {
        attempts += 1
        if (attempts >= MAX_PUBLISH_ATTEMPTS) status = OutboxStatus.FAILED
    }
}
