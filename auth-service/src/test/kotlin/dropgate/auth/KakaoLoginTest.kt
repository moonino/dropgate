package dropgate.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
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
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.simple.JdbcClient
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration::class)
class KakaoLoginTest @Autowired constructor(private val http: RestTestClient, private val kakao: KakaoClient, private val jdbc: JdbcClient) : JwtIntegrationTest() {
    private val cookies = OAuthStateCookieRepository(kakao, "test-cookie-signing-key-32-bytes-long", Clock.systemUTC(), SecureRandom())

    @BeforeEach
    fun resetKakao() {
        wiremock.resetAll()
        jdbc.sql("DELETE FROM auth.users").update()
    }

    @Test
    fun `로그인은 state 쿠키와 카카오 인가 URL을 302로 돌려준다`() {
        val headers = requestLogin()
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
        val tokenResponse = WireMock.aResponse()
            .withStatus(status)
            .withBody("""{"access_token":"access"}""")
            .withHeader("Content-Type", "application/json")
            .withFixedDelay(if (status == 200) 3500 else 0)
        wiremock.stubFor(WireMock.post("/oauth/token").willReturn(tokenResponse))
        val failure = assertThatThrownBy { kakao.fetchUser("code") }.isInstanceOf(KakaoUnavailableException::class.java)
        if (status == 200) failure.hasRootCauseInstanceOf(SocketTimeoutException::class.java)
    }

    @Test
    fun `콜백은 새 사용자를 저장하고 Bearer 토큰 쌍을 돌려준다`() {
        stubKakaoUser("""{"id":1234567,"kakao_account":{"profile":{"nickname":"테스터"}}}""")
        val response = callCallback()
            .expectStatus().isOk
            .expectHeader().valueMatches("Set-Cookie", ".*Max-Age=0.*")
            .expectBody()
        response.jsonPath("$.accessToken").isNotEmpty.jsonPath("$.refreshToken").isNotEmpty
            .jsonPath("$.expiresIn").isEqualTo(900).jsonPath("$.tokenType").isEqualTo("Bearer")
        val id = jdbc.sql("SELECT id FROM auth.users WHERE kakao_id=1234567").query(UUID::class.java).single()
        assertThat(id.version()).isEqualTo(7)
        assertThat(jdbc.sql("SELECT nickname FROM auth.users").query(String::class.java).single()).isEqualTo("테스터")
    }

    @Test
    fun `액세스 토큰은 JWKS로 검증되고 저장된 사용자 클레임을 담는다`() {
        val token = SignedJWT.parse(requestTokenPair()["accessToken"] as String)
        val key = RSAKey.parse(requestJwks().single())
        assertThat(token.verify(RSASSAVerifier(key))).isTrue()
        assertThat(token.header.algorithm.name).isEqualTo("RS256")
        assertThat(token.header.keyID).isEqualTo(key.keyID)
        val claims = token.jwtClaimsSet
        assertThat(claims.subject).isEqualTo(jdbc.sql("SELECT id FROM auth.users").query(UUID::class.java).single().toString())
        assertThat(claims.getStringClaim("role")).isEqualTo("USER")
        assertThat(claims.issuer).isEqualTo("dropgate-auth")
        assertThat(UUID.fromString(claims.jwtid).version()).isEqualTo(7)
    }

    @Test
    fun `액세스 토큰은 발급 시각부터 15분 뒤 만료된다`() {
        val claims = SignedJWT.parse(requestTokenPair()["accessToken"] as String).jwtClaimsSet
        assertThat(Duration.between(claims.issueTime.toInstant(), claims.expirationTime.toInstant())).isEqualTo(Duration.ofMinutes(15))
    }

    @Test
    fun `리프레시 토큰은 같은 키로 서명하고 별도 식별자로 14일간 유효하다`() {
        val pair = requestTokenPair()
        val access = SignedJWT.parse(pair["accessToken"] as String)
        val refresh = SignedJWT.parse(pair["refreshToken"] as String)
        assertThat(refresh.verify(RSASSAVerifier(RSAKey.parse(requestJwks().single())))).isTrue()
        assertThat(refresh.header.algorithm).isEqualTo(access.header.algorithm)
        assertThat(refresh.header.keyID).isEqualTo(access.header.keyID)
        val claims = refresh.jwtClaimsSet
        assertThat(claims.subject).isEqualTo(access.jwtClaimsSet.subject)
        assertThat(claims.issuer).isEqualTo("dropgate-auth")
        assertThat(claims.getStringClaim("typ")).isEqualTo("refresh")
        assertThat(claims.jwtid).isNotEqualTo(access.jwtClaimsSet.jwtid)
        assertThat(UUID.fromString(claims.jwtid).version()).isEqualTo(7)
        assertThat(Duration.between(claims.issueTime.toInstant(), claims.expirationTime.toInstant())).isEqualTo(Duration.ofDays(14))
    }

    @Test
    fun `JWKS는 thumbprint 식별자를 가진 서명 공개키 하나만 노출한다`() {
        val key = requestJwks().single()
        assertThat(key.keys).containsExactlyInAnyOrder("kty", "use", "alg", "n", "e", "kid")
        assertThat(key).containsEntry("kty", "RSA").containsEntry("use", "sig").containsEntry("alg", "RS256")
        assertThat(key["kid"]).isEqualTo(RSAKey.parse(key).computeThumbprint().toString())
    }

    @Test
    fun `같은 카카오 사용자면 마지막 로그인 시각만 갱신한다`() {
        val id = UUID.randomUUID()
        val before = Instant.parse("2026-01-01T00:00:00Z")
        jdbc.sql("INSERT INTO auth.users VALUES (:id,1234567,'원래 이름','ADMIN',:before,:before)")
            .param("id", id)
            .param("before", java.sql.Timestamp.from(before))
            .update()
        stubKakaoUser("""{"id":1234567,"kakao_account":{"profile":{"nickname":"바뀐 이름"}}}""")
        val pair = callCallback().expectStatus().isOk.expectBody(Map::class.java).returnResult().responseBody!!
        assertThat(SignedJWT.parse(pair["accessToken"] as String).jwtClaimsSet.getStringClaim("role")).isEqualTo("ADMIN")
        assertThat(jdbc.sql("SELECT id FROM auth.users").query(UUID::class.java).single()).isEqualTo(id)
        assertThat(jdbc.sql("SELECT nickname FROM auth.users").query(String::class.java).single()).isEqualTo("원래 이름")
        assertThat(jdbc.sql("SELECT role FROM auth.users").query(String::class.java).single()).isEqualTo("ADMIN")
        val row = jdbc.sql("SELECT created_at,last_login_at FROM auth.users")
            .query { resultSet, _ ->
                resultSet.getTimestamp("created_at").toInstant() to
                    resultSet.getTimestamp("last_login_at").toInstant()
            }.single()
        assertThat(row.first).isEqualTo(before)
        assertThat(row.second).isAfter(before)
        assertThat(jdbc.sql("SELECT count(*) FROM auth.users").query(Long::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `닉네임이 없으면 사용자 ID 뒤 여섯 자리를 붙인다`() {
        stubKakaoUser("""{"id":1234567}""")
        callCallback().expectStatus().isOk
        assertThat(jdbc.sql("SELECT nickname FROM auth.users").query(String::class.java).single()).isEqualTo("user234567")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `인가 코드가 비면 쿠키를 지우고 400을 돌려준다`(code: String) {
        callCallback(code)
            .expectStatus().isBadRequest
            .expectHeader().valueMatches("Set-Cookie", ".*Max-Age=0.*")
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("code")
        assertThat(wiremock.allServeEvents).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 51])
    fun `카카오 닉네임이 DB 계약을 어기면 502를 돌려준다`(length: Int) {
        val nickname = if (length == 0) "42" else "\"${"가".repeat(length)}\""
        stubKakaoUser("""{"id":1234567,"kakao_account":{"profile":{"nickname":$nickname}}}""")
        callCallback().expectStatus().isEqualTo(502).expectBody().jsonPath("$.code").isEqualTo("KAKAO_UNAVAILABLE")
    }

    @Test
    fun `동시 콜백도 같은 카카오 사용자 한 명만 저장한다`() {
        stubKakaoUser("""{"id":1234567}""")
        Executors.newFixedThreadPool(2).use { executor ->
            List(2) { executor.submit { callCallback().expectStatus().isOk } }.forEach { it.get() }
        }
        assertThat(jdbc.sql("SELECT count(*) FROM auth.users").query(Long::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `state가 다르면 카카오를 호출하지 않고 400을 돌려준다`() {
        val headers = requestLogin()
        http.get()
            .uri("/auth/callback/kakao?code=code&state=different")
            .cookie("dropgate_oauth_state", readStateCookie(headers))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("state")
        assertThat(wiremock.allServeEvents).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 200])
    fun `카카오 실패나 읽기 시간 초과면 쿠키를 지우고 502를 돌려준다`(status: Int) {
        stubKakaoUser("""{"id":1234567}""")
        val userResponse = WireMock.okJson("""{"id":1234567}""")
            .withStatus(status)
            .withFixedDelay(if (status == 200) 3500 else 0)
        wiremock.stubFor(WireMock.get("/v2/user/me").willReturn(userResponse))
        callCallback()
            .expectStatus().isEqualTo(502)
            .expectHeader().valueMatches("Set-Cookie", ".*Max-Age=0.*")
            .expectBody()
            .jsonPath("$.code").isEqualTo("KAKAO_UNAVAILABLE")
            .jsonPath("$.errors").isArray
        assertThat(jdbc.sql("SELECT count(*) FROM auth.users").query(Long::class.java).single()).isZero()
    }

    private fun stubKakaoUser(body: String) {
        wiremock.stubFor(WireMock.post("/oauth/token").willReturn(WireMock.okJson("""{"access_token":"access"}""")))
        wiremock.stubFor(WireMock.get("/v2/user/me").willReturn(WireMock.okJson(body)))
    }

    private fun requestTokenPair(): Map<*, *> {
        stubKakaoUser("""{"id":1234567}""")
        return callCallback().expectStatus().isOk.expectBody(Map::class.java).returnResult().responseBody!!
    }

    private fun requestJwks(): List<Map<String, Any>> = http.get().uri("/.well-known/jwks.json").exchange().expectStatus().isOk
        .expectBody(JwksTestResponse::class.java).returnResult().responseBody!!.keys

    private fun callCallback(code: String = "code"): RestTestClient.ResponseSpec {
        val headers = requestLogin()
        val state = UriComponentsBuilder.fromUriString(headers.location.toString())
            .build().queryParams.getFirst("state")!!
        return http.get()
            .uri("/auth/callback/kakao?code=$code&state=$state")
            .cookie("dropgate_oauth_state", readStateCookie(headers))
            .exchange()
    }

    private fun requestLogin(): HttpHeaders = http.get()
        .uri("/auth/login/kakao")
        .exchange()
        .expectStatus().isFound
        .returnResult(Void::class.java).responseHeaders

    private fun readStateCookie(headers: HttpHeaders): String = headers.getFirst("Set-Cookie")!!.substringAfter('=').substringBefore(';')

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

data class JwksTestResponse(val keys: List<Map<String, Any>>)
