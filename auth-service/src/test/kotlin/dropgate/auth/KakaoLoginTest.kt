package dropgate.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import dropgate.auth.client.KakaoClient
import dropgate.auth.client.KakaoUnavailableException
import dropgate.auth.repository.InvalidStateException
import dropgate.auth.repository.OAuthStateCookieRepository
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration::class)
class KakaoLoginTest @Autowired constructor(private val http: RestTestClient, private val kakao: KakaoClient) {
    private val cookies = OAuthStateCookieRepository(kakao, "test-cookie-signing-key-32-bytes-long", Clock.systemUTC(), SecureRandom())

    @BeforeEach
    fun resetKakao() = wiremock.resetAll()

    @Test
    fun `로그인은 state 쿠키와 카카오 인가 URL을 302로 돌려준다`() {
        val headers = http.get().uri("/auth/login/kakao").exchange().expectStatus().isFound.returnResult(Void::class.java).responseHeaders
        val query = UriComponentsBuilder.fromUriString(headers.location.toString()).build().queryParams
        val cookie = headers.getFirst("Set-Cookie")!!
        assertThat(headers.location.toString()).startsWith(wiremock.baseUrl() + "/oauth/authorize?")
        assertThat(query["response_type"]).containsExactly("code")
        assertThat(query["client_id"]).containsExactly("test-client")
        assertThat(cookie).contains(query.getFirst("state")!!, "HttpOnly", "Secure", "SameSite=Lax", "Max-Age=600")
        assertThat(headers["Set-Cookie"]).hasSize(1)
    }

    @Test
    fun `정상 state를 읽고 쿠키를 삭제한다`() {
        val callback = createCallbackRequest(cookies)
        val removed = MockHttpServletResponse()
        assertThat(cookies.removeAuthorizationRequest(callback, removed).state).isEqualTo(callback.getParameter("state"))
        assertThat(removed.getHeader("Set-Cookie")).contains("Max-Age=0", "Path=/auth")
    }

    @ParameterizedTest
    @ValueSource(strings = ["불일치", "위변조", "만료", "누락"])
    fun `유효하지 않은 state 쿠키를 거부한다`(scenario: String) {
        val clock = Clock.offset(Clock.systemUTC(), Duration.ofMinutes(if (scenario == "만료") -10 else 0))
        val issuer = OAuthStateCookieRepository(kakao, "test-cookie-signing-key-32-bytes-long", clock, SecureRandom())
        val callback = createCallbackRequest(issuer)
        when (scenario) {
            "불일치" -> callback.setParameter("state", "different")
            "위변조" -> callback.cookies!!.single().value += "tampered"
            "누락" -> callback.setCookies()
        }
        assertThatThrownBy { cookies.loadAuthorizationRequest(callback) }.isInstanceOf(InvalidStateException::class.java)
    }

    @Test
    fun `토큰을 교환하고 사용자 정보를 조회한다`() {
        wiremock.stubFor(WireMock.post("/oauth/token").willReturn(WireMock.okJson("""{"access_token":"access"}""")))
        wiremock.stubFor(WireMock.get("/v2/user/me").willReturn(WireMock.okJson("""{"id":1234567,"kakao_account":{"profile":{"nickname":"테스터"}}}""")))
        val user = kakao.fetchUser("code")
        assertThat(user.id).isEqualTo(1234567)
        assertThat(user.nickname).isEqualTo("테스터")
        wiremock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/oauth/token")).withFormParam("client_secret", WireMock.equalTo("test-secret")))
        wiremock.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/v2/user/me")).withHeader("Authorization", WireMock.equalTo("Bearer access")))
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 200])
    fun `카카오 실패나 3초 초과를 맥락 있는 예외로 바꾼다`(status: Int) {
        wiremock.stubFor(WireMock.post("/oauth/token").willReturn(WireMock.aResponse().withStatus(status).withBody("""{"access_token":"access"}""").withHeader("Content-Type", "application/json").withFixedDelay(if (status == 200) 3500 else 0)))
        val failure = assertThatThrownBy { kakao.fetchUser("code") }.isInstanceOf(KakaoUnavailableException::class.java)
        if (status == 200) failure.hasRootCauseInstanceOf(SocketTimeoutException::class.java)
    }

    private fun createCallbackRequest(repository: OAuthStateCookieRepository): MockHttpServletRequest {
        val authorization = repository.createAuthorizationRequest()
        val response = MockHttpServletResponse()
        repository.saveAuthorizationRequest(authorization, MockHttpServletRequest(), response)
        return MockHttpServletRequest().apply {
            setCookies(Cookie("dropgate_oauth_state", response.getHeader("Set-Cookie")!!.substringAfter('=').substringBefore(';')))
            setParameter("state", authorization.state!!)
        }
    }

    companion object {
        private val wiremock = WireMockServer(0).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureKakao(registry: DynamicPropertyRegistry) {
            listOf("authorization", "token", "user").forEach { registry.add("dropgate.kakao.$it-base-url", wiremock::baseUrl) }
        }

        @JvmStatic
        @AfterAll
        fun stopKakao() = wiremock.stop()
    }
}
