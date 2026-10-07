package dropgate.auth.repository

import dropgate.auth.client.KakaoClient
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Repository
class OAuthStateCookieRepository(
    private val kakao: KakaoClient,
    @Value("\${dropgate.oauth-state-cookie-key}") signingKey: String,
    private val clock: Clock,
    private val random: SecureRandom,
) : AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private val key = SecretKeySpec(signingKey.toByteArray().also { require(it.size >= STATE_BYTES) }, "HmacSHA256")

    fun createAuthorizationRequest(): OAuth2AuthorizationRequest = kakao.createAuthorizationRequest(encode(ByteArray(STATE_BYTES).also(random::nextBytes)))

    override fun saveAuthorizationRequest(authorizationRequest: OAuth2AuthorizationRequest, request: HttpServletRequest, response: HttpServletResponse) {
        val payload = "${authorizationRequest.state}.${clock.instant().plus(COOKIE_TTL).epochSecond}"
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie("$payload.${sign(payload)}", COOKIE_TTL))
    }

    override fun loadAuthorizationRequest(request: HttpServletRequest): OAuth2AuthorizationRequest {
        val parts = request.cookies.orEmpty().singleOrNull { it.name == COOKIE_NAME }?.value.orEmpty().split('.')
        if (parts.size != 3 ||
            !MessageDigest.isEqual(sign("${parts[0]}.${parts[1]}").toByteArray(), parts[2].toByteArray()) ||
            (parts[1].toLongOrNull() ?: 0) <= clock.instant().epochSecond ||
            parts[0].isBlank() ||
            parts[0] != request.getParameter("state")
        ) {
            throw InvalidStateException()
        }
        return kakao.createAuthorizationRequest(parts[0])
    }

    override fun removeAuthorizationRequest(request: HttpServletRequest, response: HttpServletResponse): OAuth2AuthorizationRequest {
        val authorization = loadAuthorizationRequest(request)
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie("", Duration.ZERO))
        return authorization
    }

    private fun createCookie(value: String, age: Duration): String = ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(true).sameSite("Lax").path("/auth").maxAge(age).build().toString()

    private fun sign(payload: String): String = encode(Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(payload.toByteArray()))

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        private const val COOKIE_NAME = "dropgate_oauth_state"
        private const val STATE_BYTES = 32
        private val COOKIE_TTL = Duration.ofMinutes(10)
    }
}

class InvalidStateException : RuntimeException("로그인 state가 유효하지 않습니다")
