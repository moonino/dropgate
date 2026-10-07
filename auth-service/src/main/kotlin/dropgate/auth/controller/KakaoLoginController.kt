package dropgate.auth.controller

import dropgate.auth.repository.OAuthStateCookieRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class KakaoLoginController(private val cookies: OAuthStateCookieRepository) {
    @GetMapping("/auth/login/kakao")
    fun login(request: HttpServletRequest, response: HttpServletResponse) {
        val authorization = cookies.createAuthorizationRequest()
        cookies.saveAuthorizationRequest(authorization, request, response)
        response.status = HttpServletResponse.SC_FOUND
        response.setHeader("Location", authorization.authorizationRequestUri)
    }
}
