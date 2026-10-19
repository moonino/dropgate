package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.common.error.ErrorResponseWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

class JwtAuthenticationFilterTest {
    private val clock = MutableClock(Instant.parse("2026-10-17T00:00:00Z"))
    private val issuer = TestTokenIssuer(clock)
    private val jsonMapper = JsonMapper.builder().build()
    private val publicEndpoints = listOf(PublicEndpoint(HttpMethod.GET, "/api/drops/*"))

    @Test
    fun `토큰이 없으면 401 UNAUTHENTICATED 오류 응답을 쓴다`() {
        val response = filter().run(MockHttpServletRequest("POST", "/api/drops/1/orders"), MockFilterChain())
        assertThat(response.status).isEqualTo(401)
        assertThat(response.contentType).startsWith("application/json")
        assertThat(jsonMapper.readTree(response.contentAsString))
            .isEqualTo(jsonMapper.readTree("""{"code":"UNAUTHENTICATED","message":"로그인이 필요합니다","errors":[]}"""))
    }

    @Test
    fun `토큰이 없으면 요청을 다음 필터로 넘기지 않는다`() {
        val chain = MockFilterChain()
        filter().run(MockHttpServletRequest("POST", "/api/drops/1/orders"), chain)
        assertThat(chain.request).isNull()
    }

    @Test
    fun `Bearer가 아닌 인증 헤더는 401로 거부한다`() {
        val request = MockHttpServletRequest("POST", "/api/drops/1/orders").apply { addHeader(HttpHeaders.AUTHORIZATION, "Basic token") }
        assertThat(filter().run(request, MockFilterChain()).status).isEqualTo(401)
    }

    @Test
    fun `유효한 토큰이면 인증된 사용자를 요청에 담아 넘긴다`() {
        val userId = UUID.randomUUID()
        val request = createAuthorizedRequest("POST", "/api/drops/1/orders", issuer.issueAccessToken(userId))
        val chain = MockFilterChain()
        filter().run(request, chain)
        assertThat(chain.request!!.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE)).isEqualTo(AuthenticatedUser(userId, Role.USER))
    }

    @Test
    fun `공개 엔드포인트는 토큰 없이 통과한다`() {
        val chain = MockFilterChain()
        val response = filter().run(MockHttpServletRequest("GET", "/api/drops/1"), chain)
        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `공개 경로라도 메서드가 다르면 토큰을 요구한다`() {
        val response = filter().run(MockHttpServletRequest("DELETE", "/api/drops/1"), MockFilterChain())
        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `JWKS를 받지 못하면 503 AUTH_UNAVAILABLE과 Retry-After를 쓴다`() {
        val unavailable = JwkSetFetcher { throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "JWKS 서버 응답 없음") }
        val request = createAuthorizedRequest("POST", "/api/drops/1/orders", issuer.issueAccessToken())
        val response = filter(unavailable).run(request, MockFilterChain())
        assertThat(response.status).isEqualTo(503)
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("2")
        assertThat(jsonMapper.readTree(response.contentAsString)["code"].asString()).isEqualTo("AUTH_UNAVAILABLE")
    }

    private fun filter(fetcher: JwkSetFetcher = JwkSetFetcher { issuer.createJwkSetJson() }) = JwtAuthenticationFilter(
        AccessTokenVerifier(JwkSetCache(fetcher, clock), clock, InMemoryRevokedAccessTokens()),
        publicEndpoints,
        ErrorResponseWriter(jsonMapper),
    )

    private fun JwtAuthenticationFilter.run(request: MockHttpServletRequest, chain: MockFilterChain): MockHttpServletResponse = MockHttpServletResponse().also { doFilter(request, it, chain) }

    private fun createAuthorizedRequest(method: String, path: String, token: String) = MockHttpServletRequest(method, path).apply {
        addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }
}
