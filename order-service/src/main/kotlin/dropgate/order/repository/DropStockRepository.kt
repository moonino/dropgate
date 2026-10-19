package dropgate.order.repository

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.DropEntity
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets.UTF_8

@Repository
class DropStockRepository(private val redis: StringRedisTemplate) {
    private val initialization = DefaultRedisScript(ClassPathResource("redis/init.lua").getContentAsString(UTF_8), Long::class.java)

    fun initialize(drop: DropEntity) {
        try {
            redis.execute(
                initialization,
                listOf("drop:${drop.id}:stock", "drop:${drop.id}:meta"),
                drop.totalQuantity.toString(),
                drop.openAt.toEpochMilli().toString(),
                drop.closeAt.toEpochMilli().toString(),
                drop.admissionRatePerSec.toString(),
            )
        } catch (exception: DataAccessException) {
            throw DropgateException(ErrorCode.STOCK_UNAVAILABLE, "드롭 ${drop.id} 의 Redis 재고를 초기화하지 못했습니다", cause = exception)
        }
    }
}
