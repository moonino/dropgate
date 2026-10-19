package dropgate.auth.repository

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class RefreshTokenRepository(private val redis: StringRedisTemplate) {
    private val storeScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("redis/store-refresh.lua"))
        resultType = Long::class.javaObjectType
    }

    fun store(userId: UUID, tokenId: UUID, ttl: Duration) {
        execute(userId) { redis.execute(storeScript, listOf(refreshKey(userId, tokenId), indexKey(userId)), tokenId.toString(), ttl.toMillis().toString()) }
    }

    private fun <T> execute(userId: UUID, command: () -> T): T = try {
        command()
    } catch (cause: DataAccessException) {
        throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "사용자 $userId 의 인증 토큰 Redis 명령에 실패했습니다", cause = cause)
    }

    private fun refreshKey(userId: UUID, tokenId: UUID): String = "auth:refresh:$userId:$tokenId"

    private fun indexKey(userId: UUID): String = "auth:refresh-index:$userId"
}
