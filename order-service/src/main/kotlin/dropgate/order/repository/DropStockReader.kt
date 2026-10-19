package dropgate.order.repository

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DropStockReader(private val redis: StringRedisTemplate) {
    fun readStock(dropId: UUID): RemainingStock = readOrElse(listOf(dropId), RemainingStock.Unknown) {
        parseStock(redis.opsForValue().get(createDropRedisKey(dropId, "stock")))
    }

    fun readStocks(dropIds: List<UUID>): Map<UUID, RemainingStock> {
        if (dropIds.isEmpty()) return emptyMap()
        return readOrElse(dropIds, dropIds.associateWith<UUID, RemainingStock> { RemainingStock.Unknown }) {
            val values = redis.opsForValue().multiGet(dropIds.map { createDropRedisKey(it, "stock") }).orEmpty()
            dropIds.mapIndexed { index, id -> id to parseStock(values.getOrNull(index)) }.toMap()
        }
    }

    private fun parseStock(value: String?): RemainingStock = value?.toIntOrNull()?.let { RemainingStock.Known(it) } ?: RemainingStock.Unknown

    private fun <T> readOrElse(dropIds: List<UUID>, fallback: T, command: () -> T): T = try {
        command()
    } catch (exception: DataAccessException) {
        log.warn("드롭 {}의 Redis 재고를 읽지 못해 수량을 모름으로 처리합니다", dropIds, exception)
        fallback
    }

    private companion object {
        val log = LoggerFactory.getLogger(DropStockReader::class.java)
    }
}
