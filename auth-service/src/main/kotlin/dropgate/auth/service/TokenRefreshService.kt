package dropgate.auth.service

import dropgate.auth.repository.RefreshTokenRepository
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.springframework.stereotype.Service

@Service
class TokenRefreshService(
    private val decoder: RefreshTokenDecoder,
    private val users: UserService,
    private val tokens: JwtTokenService,
    private val refreshTokens: RefreshTokenRepository,
) {
    fun refresh(token: String): IssuedTokens {
        val verified = decoder.decode(token)
        val user = users.getUser(verified.userId)
        val issued = tokens.issueTokenPair(user)
        if (!refreshTokens.rotate(user.id, verified.tokenId, issued.refreshTokenId, issued.refreshTokenTtl)) {
            throw DropgateException(ErrorCode.UNAUTHENTICATED, "사용자 ${user.id} 의 리프레시 ${verified.tokenId} 는 이미 사용했거나 만료됐습니다")
        }
        return issued
    }
}
