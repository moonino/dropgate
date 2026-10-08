package dropgate.auth.controller

import dropgate.auth.client.KakaoClient
import dropgate.auth.repository.OAuthStateCookieRepository
import dropgate.auth.service.JwtTokenService
import dropgate.auth.service.TokenPairResponse
import dropgate.auth.service.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class KakaoCallbackController(
    private val cookies: OAuthStateCookieRepository,
    private val kakao: KakaoClient,
    private val users: UserService,
    private val tokens: JwtTokenService,
) {
    @GetMapping("/auth/callback/kakao")
    fun handleCallback(@RequestParam(defaultValue = "") code: String, request: HttpServletRequest, response: HttpServletResponse): TokenPairResponse {
        cookies.removeAuthorizationRequest(request, response)
        if (code.isBlank()) throw MissingServletRequestParameterException("code", "String")
        return tokens.issueTokenPair(users.storeUser(kakao.fetchUser(code)))
    }
}
