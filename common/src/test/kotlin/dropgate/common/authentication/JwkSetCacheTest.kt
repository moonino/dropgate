package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.common.error.assertThrowsDropgateError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private const val JWK_SET_JSON = """{"keys":[{"kty":"RSA","kid":"test-key","n":"AQAB","e":"AQAB"}]}"""
private val JWKS_CACHE_TTL = Duration.ofMinutes(10)

class JwkSetCacheTest {
    private val clock = MutableClock(Instant.parse("2026-10-17T00:00:00Z"))
    private val fetcher = CountingJwkSetFetcher { JWK_SET_JSON }
    private val cache = JwkSetCache(fetcher, clock)

    @Test
    fun `받은 JWKS의 키를 돌려준다`() {
        assertThat(cache.getCurrentKeys().getKeyByKeyId("test-key")).isNotNull()
    }

    @Test
    fun `10분 안에는 JWKS를 다시 받지 않는다`() {
        cache.getCurrentKeys()
        clock.advance(JWKS_CACHE_TTL.minusNanos(1))
        cache.getCurrentKeys()
        assertThat(fetcher.fetchCount).isEqualTo(1)
    }

    @Test
    fun `10분이 지나면 JWKS를 다시 받는다`() {
        cache.getCurrentKeys()
        clock.advance(JWKS_CACHE_TTL)
        cache.getCurrentKeys()
        assertThat(fetcher.fetchCount).isEqualTo(2)
    }

    @Test
    fun `JWKS 형식이 깨졌으면 AUTH_UNAVAILABLE로 거부한다`() {
        assertThrowsDropgateError(ErrorCode.AUTH_UNAVAILABLE) { JwkSetCache({ "not-json" }, clock).getCurrentKeys() }
    }

    @Test
    fun `10분 뒤 다시 받지 못하면 만료된 키를 쓰지 않고 AUTH_UNAVAILABLE로 거부한다`() {
        val failingAfterFirst = JwkSetCache(CountingJwkSetFetcher { attempt -> if (attempt == 1) JWK_SET_JSON else throw createFetchFailure() }, clock)
        failingAfterFirst.getCurrentKeys()
        clock.advance(JWKS_CACHE_TTL)
        assertThrowsDropgateError(ErrorCode.AUTH_UNAVAILABLE) { failingAfterFirst.getCurrentKeys() }
    }

    @Test
    fun `조회가 실패한 뒤에 온 요청은 JWKS를 다시 받는다`() {
        val recovering = CountingJwkSetFetcher { attempt -> if (attempt == 1) throw createFetchFailure() else JWK_SET_JSON }
        val recoveringCache = JwkSetCache(recovering, clock)
        assertThrowsDropgateError(ErrorCode.AUTH_UNAVAILABLE) { recoveringCache.getCurrentKeys() }
        assertThat(recoveringCache.getCurrentKeys().getKeyByKeyId("test-key")).isNotNull()
    }

    private fun createFetchFailure() = DropgateException(ErrorCode.AUTH_UNAVAILABLE, "테스트 JWKS 조회 실패")
}
