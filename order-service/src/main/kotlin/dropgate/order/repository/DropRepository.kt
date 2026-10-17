package dropgate.order.repository

import dropgate.order.entity.DropEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DropRepository : JpaRepository<DropEntity, UUID>
