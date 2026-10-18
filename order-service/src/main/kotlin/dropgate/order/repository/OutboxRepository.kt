package dropgate.order.repository

import dropgate.order.entity.OutboxEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxRepository : JpaRepository<OutboxEntity, Long>
