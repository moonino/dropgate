package dropgate.auth.service

import dropgate.auth.repository.RefreshTokenRepository
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

@Service
class TokenLogoutService(private val refreshTokens: RefreshTokenRepository, private val clock: Clock) {
    fun logout(token: VerifiedAccessToken) {
        val ttl = Duration.between(clock.instant(), token.expiresAt)
        if (ttl.toMillis() <= 0) throw DropgateException(ErrorCode.UNAUTHENTICATED, "로그아웃을 기록할 액세스 토큰 ${token.tokenId} 의 남은 만료 시간이 없습니다")
        refreshTokens.logout(token.userId, token.tokenId, ttl)
    }
}
