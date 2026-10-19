package dropgate.order.repository

import dropgate.order.entity.OutboxEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OutboxRepository : JpaRepository<OutboxEntity, Long> {
    @Query(
        value = "SELECT * FROM orders.outbox WHERE status = 'PENDING' ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun findPendingForUpdate(@Param("limit") limit: Int): List<OutboxEntity>
}
