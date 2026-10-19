package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate

private const val REVOKED_ACCESS_TOKEN_PREFIX = "auth:revoked:"

fun createRevokedAccessTokenKey(tokenId: String): String = REVOKED_ACCESS_TOKEN_PREFIX + tokenId

class RedisRevokedAccessTokens(private val redis: StringRedisTemplate) : RevokedAccessTokens {
    override fun isRevoked(tokenId: String): Boolean = try {
        redis.hasKey(createRevokedAccessTokenKey(tokenId))
    } catch (cause: DataAccessException) {
        throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "토큰 $tokenId 의 무효화 Redis 조회에 실패했습니다", cause = cause)
    }
}
