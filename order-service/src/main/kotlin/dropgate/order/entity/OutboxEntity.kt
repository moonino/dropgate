package dropgate.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

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
}
