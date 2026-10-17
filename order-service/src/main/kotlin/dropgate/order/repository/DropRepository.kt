package dropgate.order.repository

import dropgate.order.entity.DropEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

private const val LISTED_DROP_CONDITION = "status IN ('SCHEDULED', 'OPEN', 'SOLD_OUT')"

interface DropRepository : JpaRepository<DropEntity, UUID> {
    @Query(
        value = """
        SELECT * FROM orders.drops
        WHERE $LISTED_DROP_CONDITION
        ORDER BY open_at, id
        LIMIT :limit
    """,
        nativeQuery = true,
    )
    fun findListedDrops(@Param("limit") limit: Int): List<DropEntity>

    @Query(
        value = """
        SELECT * FROM orders.drops
        WHERE $LISTED_DROP_CONDITION AND (open_at, id) > (:openAt, :id)
        ORDER BY open_at, id
        LIMIT :limit
    """,
        nativeQuery = true,
    )
    fun findListedDropsAfter(@Param("openAt") openAt: Instant, @Param("id") id: UUID, @Param("limit") limit: Int): List<DropEntity>
}
