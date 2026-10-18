package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.common.error.ErrorResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

private val BEARER_HEADER = Regex("Bearer +(\\S+)", RegexOption.IGNORE_CASE)

class JwtAuthenticationFilter(
    private val verifier: AccessTokenVerifier,
    private val publicEndpoints: List<PublicEndpoint>,
    private val errorResponseWriter: ErrorResponseWriter,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = publicEndpoints.any { it.matches(request) }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val user = try {
            verifier.verify(extractBearerToken(request))
        } catch (exception: DropgateException) {
            errorResponseWriter.write(response, exception)
            return
        }
        request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, user)
        filterChain.doFilter(request, response)
    }

    private fun extractBearerToken(request: HttpServletRequest): String {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
        val match = BEARER_HEADER.matchEntire(authorization) ?: throw DropgateException(ErrorCode.UNAUTHENTICATED, "Bearer 인증 헤더가 없거나 형식이 올바르지 않습니다")
        return match.groupValues[1]
    }
}
