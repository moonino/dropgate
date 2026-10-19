package dropgate.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dropgate.auth.client.KakaoClient
import dropgate.auth.configuration.JwtConfiguration
import dropgate.auth.controller.KakaoCallbackController
import dropgate.auth.controller.TokenRefreshController
import dropgate.auth.repository.OAuthStateCookieRepository
import dropgate.auth.repository.RefreshTokenRepository
import dropgate.auth.service.JwtTokenService
import dropgate.auth.service.RefreshTokenDecoder
import dropgate.auth.service.TokenRefreshService
import dropgate.auth.service.UserService
import dropgate.common.error.ErrorCode
import dropgate.common.error.GlobalExceptionHandler
import dropgate.common.error.assertThrowsDropgateError
import io.lettuce.core.ClientOptions.DisconnectedBehavior
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.ValueOutput
import io.lettuce.core.protocol.Command
import io.lettuce.core.protocol.CommandType
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.util.UriComponentsBuilder
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration::class, RedisTestcontainersConfiguration::class)
class KakaoLoginTest @Autowired constructor(
    private val http: RestTestClient,
    private val kakao: KakaoClient,
    private val jdbc: JdbcClient,
    private val redis: StringRedisTemplate,
    private val connectionFactory: LettuceConnectionFactory,
    private val users: UserService,
    private val tokens: JwtTokenService,
    private val refreshDecoder: RefreshTokenDecoder,
) : JwtIntegrationTest() {
    private val cookies = OAuthStateCookieRepository(kakao, "test-cookie-signing-key-32-bytes-long", Clock.systemUTC(), SecureRandom())

    @BeforeEach
    fun resetKakao() {
        wiremock.resetAll()
        jdbc.sql("DELETE FROM auth.users").update()
        redis.connectionFactory!!.connection.use { it.serverCommands().flushDb() }
    }

    @Test
    fun `콜백은 리프레시를 14일 안의 TTL로 저장한다`() {
        val claims = SignedJWT.parse(requestTokenPair()["refreshToken"] as String).jwtClaimsSet
        val key = "auth:refresh:${claims.subject}:${claims.jwtid}"
        assertThat(redis.opsForValue().get(key)).isEqualTo("1")
        assertThat(redis.getExpire(key)).isBetween(1L, Duration.ofDays(14).seconds)
    }

    @Test
    fun `콜백은 사용자 리프레시 index에 발급 식별자를 기록한다`() {
        val claims = SignedJWT.parse(requestTokenPair()["refreshToken"] as String).jwtClaimsSet
        assertThat(redis.opsForSet().members("auth:refresh-index:${claims.subject}")).containsExactly(claims.jwtid)
    }

    @Test
    fun `실제 Redis 연결은 reconnect를 유지하고 Lua replay와 단절 중 명령을 막는다`() {
        val options = connectionFactory.clientConfiguration.clientOptions.orElseThrow()
        val command = Command<String, String, String>(CommandType.EVAL, ValueOutput(StringCodec.UTF8))
        assertThat(options.isAutoReconnect).isTrue()
        assertThat(options.replayFilter.test(command)).isTrue()
        assertThat(options.disconnectedBehavior).isEqualTo(DisconnectedBehavior.REJECT_COMMANDS)
    }

    @Test
    fun `실제 Redis 연결은 Boot 명령과 연결 타임아웃을 유지한다`() {
        val configuration = connectionFactory.clientConfiguration
        val options = configuration.clientOptions.orElseThrow()
        assertThat(configuration.commandTimeout).isEqualTo(Duration.ofMillis(200))
        assertThat(options.socketOptions.connectTimeout).isEqualTo(Duration.ofSeconds(1))
        assertThat(options.timeoutOptions.isTimeoutCommands).isTrue()
    }

    @Test
    fun `콜백의 Redis 연결 거절은 AUTH_UNAVAILABLE과 재시도 헤더를 돌려준다`() {
        withUnavailableRedis { repository ->
            val client = createControllerClient(KakaoCallbackController(cookies, kakao, users, tokens, repository))
            stubKakaoUser("""{"id":1234567}""")
            callCallback(client = client).expectStatus().isEqualTo(503).expectHeader().valueEquals("Retry-After", "2")
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_UNAVAILABLE")
        }
    }

    @Test
    fun `재발급은 옛 리프레시를 새 키와 index로 원자 교체한다`() {
        val previous = SignedJWT.parse(requestTokenPair()["refreshToken"] as String)
        val pair = refresh(previous.serialize()).expectStatus().isOk.expectBody(Map::class.java).returnResult().responseBody!!
        val next = SignedJWT.parse(pair["refreshToken"] as String).jwtClaimsSet
        val before = previous.jwtClaimsSet
        assertThat(next.jwtid).isNotEqualTo(before.jwtid)
        assertThat(redis.hasKey("auth:refresh:${before.subject}:${before.jwtid}")).isFalse()
        assertThat(redis.opsForValue().get("auth:refresh:${next.subject}:${next.jwtid}")).isEqualTo("1")
        assertThat(redis.opsForSet().members("auth:refresh-index:${next.subject}")).containsExactly(next.jwtid)
        assertThat(redis.getExpire("auth:refresh:${next.subject}:${next.jwtid}")).isBetween(1L, Duration.ofDays(14).seconds)
    }

    @Test
    fun `이미 쓴 리프레시로 다시 재발급하면 401이다`() {
        val token = requestTokenPair()["refreshToken"] as String
        refresh(token).expectStatus().isOk
        expectUnauthenticatedResponse(refresh(token))
    }

    @Test
    fun `같은 리프레시의 동시 재발급은 하나만 성공한다`() {
        val token = requestTokenPair()["refreshToken"] as String
        val statuses = Executors.newFixedThreadPool(2).use { executor ->
            List(2) { executor.submit<Int> { refresh(token).returnResult(Void::class.java).status.value() } }.map { it.get() }
        }
        assertThat(statuses).containsExactlyInAnyOrder(200, 401)
    }

    @Test
    fun `저장 키가 있어도 액세스 토큰으로 재발급하면 401이다`() {
        val token = requestTokenPair()["accessToken"] as String
        val claims = SignedJWT.parse(token).jwtClaimsSet
        redis.opsForValue().set("auth:refresh:${claims.subject}:${claims.jwtid}", "1", Duration.ofMinutes(15))
        expectUnauthenticatedResponse(refresh(token))
    }

    @Test
    fun `만료된 리프레시는 저장 키가 있어도 재발급할 수 없다`() {
        expectUnauthenticatedResponse(refresh(signRefreshToken { expirationTime(Date.from(Instant.now().minusSeconds(1))) }))
    }

    @Test
    fun `리프레시는 만료 직전까지 주입한 Clock으로 검증한다`() {
        val token = requestTokenPair()["refreshToken"] as String
        val claims = SignedJWT.parse(token).jwtClaimsSet
        assertThat(refreshDecoderAt(claims.expirationTime.toInstant().minusNanos(1)).decode(token).userId).isEqualTo(UUID.fromString(claims.subject))
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 1])
    fun `리프레시는 정확한 만료 시각부터 거부한다`(secondsAfterExpiry: Long) {
        val token = requestTokenPair()["refreshToken"] as String
        val expiry = SignedJWT.parse(token).jwtClaimsSet.expirationTime.toInstant()
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { refreshDecoderAt(expiry.plusSeconds(secondsAfterExpiry)).decode(token) }.hasMessageContaining("exp")
    }

    @Test
    fun `리프레시는 nbf 직전에는 거부하고 해당 시각부터 허용한다`() {
        val notBefore = Instant.ofEpochSecond(Instant.now().epochSecond + 60)
        val token = signRefreshToken { notBeforeTime(Date.from(notBefore)) }
        assertThrowsDropgateError(ErrorCode.UNAUTHENTICATED) { refreshDecoderAt(notBefore.minusNanos(1)).decode(token) }
        assertThat(refreshDecoderAt(notBefore).decode(token).userId).isEqualTo(UUID.fromString(SignedJWT.parse(token).jwtClaimsSet.subject))
    }

    @Test
    fun `다른 키로 서명한 리프레시를 거부한다`() {
        expectUnauthenticatedResponse(refresh(signRefreshToken(key = JwtConfiguration().jwtSigningKey(generatePrivateKeyPem()))))
    }

    @Test
    fun `재발급 본문에 refreshToken이 없으면 필드 오류다`() {
        http.post().uri("/auth/token/refresh").body(emptyMap<String, String>()).exchange()
            .expectStatus().isBadRequest.expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("refreshToken")
    }

    @ParameterizedTest
    @ValueSource(strings = ["발급자", "만료시각", "사용자", "식별자"])
    fun `리프레시의 필수 클레임이 없거나 틀리면 401이다`(claim: String) {
        val token = signRefreshToken {
            when (claim) {
                "발급자" -> issuer("other-auth")
                "만료시각" -> expirationTime(null)
                "사용자" -> subject("not-a-uuid")
                "식별자" -> jwtID(null)
            }
        }
        expectUnauthenticatedResponse(refresh(token))
    }

    @Test
    fun `재발급의 Redis 연결 거절은 AUTH_UNAVAILABLE이다`() {
        val token = requestTokenPair()["refreshToken"] as String
        withUnavailableRedis { repository ->
            val client = createControllerClient(TokenRefreshController(TokenRefreshService(refreshDecoder, users, tokens, repository)))
            refresh(token, client).expectStatus().isEqualTo(503).expectHeader().valueEquals("Retry-After", "2")
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_UNAVAILABLE")
        }
    }

    private fun refresh(token: String, client: RestTestClient = http): RestTestClient.ResponseSpec = client.post().uri("/auth/token/refresh").body(mapOf("refreshToken" to token)).exchange()

    private fun signRefreshToken(key: RSAKey = JwtConfiguration().jwtSigningKey(privateKeyPem), change: JWTClaimsSet.Builder.() -> Unit = {}): String {
        val original = SignedJWT.parse(requestTokenPair()["refreshToken"] as String)
        return SignedJWT(original.header, JWTClaimsSet.Builder(original.jwtClaimsSet).apply(change).build()).apply { sign(RSASSASigner(key)) }.serialize()
    }

    private fun refreshDecoderAt(instant: Instant): RefreshTokenDecoder = RefreshTokenDecoder(JwtConfiguration().jwtSigningKey(privateKeyPem), Clock.fixed(instant, ZoneOffset.UTC))

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
        assertThrowsDropgateError(ErrorCode.VALIDATION_FAILED) { cookies.loadAuthorizationRequest(callback) }
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
        val failure = assertThrowsDropgateError(ErrorCode.KAKAO_UNAVAILABLE) { kakao.fetchUser("code") }
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

    @ParameterizedTest
    @ValueSource(strings = ["Bearer", "bearer"])
    fun `내 정보는 정상 토큰의 사용자를 DB에서 조회한다`(scheme: String) {
        val token = requestTokenPair()["accessToken"] as String
        jdbc.sql("UPDATE auth.users SET nickname='변경한 이름', role='ADMIN'").update()
        http.get().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "$scheme $token").exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.id").isEqualTo(SignedJWT.parse(token).jwtClaimsSet.subject)
            .jsonPath("$.nickname").isEqualTo("변경한 이름").jsonPath("$.role").isEqualTo("ADMIN")
    }

    @Test
    fun `내 정보는 인증 헤더가 없으면 401을 돌려준다`() {
        expectUnauthenticatedResponse(http.get().uri("/auth/me").exchange())
    }

    @ParameterizedTest
    @ValueSource(strings = ["Bearer", "Basic token", "Bearer invalid.jwt.token", "Bearer token extra"])
    fun `내 정보는 잘못된 인증 헤더나 토큰이면 401을 돌려준다`(header: String) {
        expectUnauthenticatedResponse(http.get().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, header).exchange())
    }

    @Test
    fun `내 정보는 다른 키로 서명한 토큰이면 401을 돌려준다`() {
        val otherKey = JwtConfiguration().jwtSigningKey(generatePrivateKeyPem())
        expectUnauthenticatedResponse(requestCurrentUser(signAccessToken(otherKey)))
    }

    @Test
    fun `내 정보는 RS256 이외의 알고리즘이면 401을 돌려준다`() {
        expectUnauthenticatedResponse(requestCurrentUser(signAccessToken(algorithm = JWSAlgorithm.RS512)))
    }

    @Test
    fun `내 정보는 만료된 토큰이면 401을 돌려준다`() {
        val expired = signAccessToken {
            issueTime(Date.from(Instant.now().minus(Duration.ofMinutes(15))))
            expirationTime(Date.from(Instant.now().minusSeconds(1)))
        }
        expectUnauthenticatedResponse(requestCurrentUser(expired))
    }

    @Test
    fun `내 정보는 다른 발급자의 토큰이면 401을 돌려준다`() {
        expectUnauthenticatedResponse(requestCurrentUser(signAccessToken { issuer("other-auth") }))
    }

    @Test
    fun `내 정보는 리프레시 토큰이면 401을 돌려준다`() {
        expectUnauthenticatedResponse(requestCurrentUser(requestTokenPair()["refreshToken"] as String))
    }

    @Test
    fun `내 정보는 사용자가 삭제됐으면 401을 돌려준다`() {
        val token = requestTokenPair()["accessToken"] as String
        jdbc.sql("DELETE FROM auth.users").update()
        expectUnauthenticatedResponse(requestCurrentUser(token))
    }

    @Test
    fun `내 정보는 만료 시각이 없으면 401을 돌려준다`() {
        expectUnauthenticatedResponse(requestCurrentUser(signAccessToken { expirationTime(null) }))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "not-a-uuid", "1-1-1-1-1"])
    fun `내 정보는 사용자 식별자가 없거나 UUID가 아니면 401을 돌려준다`(subject: String) {
        expectUnauthenticatedResponse(requestCurrentUser(signAccessToken { subject(subject.ifEmpty { null }) }))
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
            .jsonPath("$.message").isEqualTo(ErrorCode.VALIDATION_FAILED.message)
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
            .jsonPath("$.message").isEqualTo(ErrorCode.VALIDATION_FAILED.message)
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
            .jsonPath("$.message").isEqualTo(ErrorCode.KAKAO_UNAVAILABLE.message)
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

    private fun requestCurrentUser(token: String): RestTestClient.ResponseSpec = http.get().uri("/auth/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token").exchange()

    private fun expectUnauthenticatedResponse(response: RestTestClient.ResponseSpec) {
        response.expectStatus().isUnauthorized.expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
            .jsonPath("$.message").isEqualTo(ErrorCode.UNAUTHENTICATED.message).jsonPath("$.errors").isEqualTo(emptyList<Any>())
    }

    private fun signAccessToken(
        key: RSAKey = JwtConfiguration().jwtSigningKey(privateKeyPem),
        algorithm: JWSAlgorithm = JWSAlgorithm.RS256,
        changeClaims: JWTClaimsSet.Builder.() -> Unit = {},
    ): String {
        val access = SignedJWT.parse(requestTokenPair()["accessToken"] as String)
        val header = JWSHeader.Builder(algorithm).keyID(access.header.keyID).build()
        return SignedJWT(header, JWTClaimsSet.Builder(access.jwtClaimsSet).apply(changeClaims).build())
            .apply { sign(RSASSASigner(key)) }.serialize()
    }

    private fun requestJwks(): List<Map<String, Any>> = http.get().uri("/.well-known/jwks.json").exchange().expectStatus().isOk
        .expectBody(JwksTestResponse::class.java).returnResult().responseBody!!.keys

    private fun withUnavailableRedis(verify: (RefreshTokenRepository) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val unavailable = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port), connectionFactory.clientConfiguration)
        unavailable.afterPropertiesSet()
        try {
            verify(RefreshTokenRepository(StringRedisTemplate(unavailable)))
        } finally {
            unavailable.destroy()
        }
    }

    private fun createControllerClient(controller: Any): RestTestClient = RestTestClient.bindTo(MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler()).build()).build()

    private fun callCallback(code: String = "code", client: RestTestClient = http): RestTestClient.ResponseSpec {
        val headers = requestLogin()
        val state = UriComponentsBuilder.fromUriString(headers.location.toString())
            .build().queryParams.getFirst("state")!!
        return client.get()
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
