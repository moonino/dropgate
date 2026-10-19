package dropgate.auth.controller

import dropgate.auth.client.KakaoClient
import dropgate.auth.dto.TokenPairResponse
import dropgate.auth.repository.OAuthStateCookieRepository
import dropgate.auth.repository.RefreshTokenRepository
import dropgate.auth.service.JwtTokenService
import dropgate.auth.service.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val BEARER_TOKEN_TYPE = "Bearer"

@RestController
class KakaoCallbackController(
    private val cookies: OAuthStateCookieRepository,
    private val kakao: KakaoClient,
    private val users: UserService,
    private val tokens: JwtTokenService,
    private val refreshTokens: RefreshTokenRepository,
) {
    @GetMapping("/auth/callback/kakao")
    fun handleCallback(@RequestParam(defaultValue = "") code: String, request: HttpServletRequest, response: HttpServletResponse): TokenPairResponse {
        cookies.removeAuthorizationRequest(request, response)
        if (code.isBlank()) throw MissingServletRequestParameterException("code", "String")
        val user = users.storeUser(kakao.fetchUser(code))
        val issued = tokens.issueTokenPair(user)
        refreshTokens.store(user.id, issued.refreshTokenId, issued.refreshTokenTtl)
        return TokenPairResponse(issued.accessToken, issued.refreshToken, issued.accessTokenTtl.seconds, BEARER_TOKEN_TYPE)
    }
}
