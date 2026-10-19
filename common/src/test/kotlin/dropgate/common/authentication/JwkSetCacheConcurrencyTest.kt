package dropgate.common.authentication

import com.nimbusds.jose.jwk.JWKSet
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.common.error.assertThrowsDropgateError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val JWK_SET_JSON = """{"keys":[{"kty":"RSA","kid":"test-key","n":"AQAB","e":"AQAB"}]}"""
private val WAIT_LIMIT = Duration.ofSeconds(5)

class JwkSetCacheConcurrencyTest {
    private val clock = MutableClock(Instant.parse("2026-10-17T00:00:00Z"))

    @Test
    fun `앞선 조회가 실패하면 기다리던 요청은 다시 조회하지 않고 AUTH_UNAVAILABLE을 받는다`() {
        val fetcher = BlockingJwkSetFetcher { throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "테스트 JWKS 조회 실패") }
        val outcomes = requestTwiceWhileFirstFetchBlocks(JwkSetCache(fetcher, clock), fetcher)
        assertThat(outcomes).allSatisfy { outcome ->
            assertThrowsDropgateError(ErrorCode.AUTH_UNAVAILABLE) { outcome.getOrThrow() }
        }
        assertThat(fetcher.fetchCount).isEqualTo(1)
    }

    @Test
    fun `앞선 조회가 성공하면 기다리던 요청은 다시 조회하지 않고 그 키를 쓴다`() {
        val fetcher = BlockingJwkSetFetcher { JWK_SET_JSON }
        val outcomes = requestTwiceWhileFirstFetchBlocks(JwkSetCache(fetcher, clock), fetcher)
        assertThat(outcomes).allSatisfy { assertThat(it.getOrThrow().getKeyByKeyId("test-key")).isNotNull() }
        assertThat(fetcher.fetchCount).isEqualTo(1)
    }

    private fun requestTwiceWhileFirstFetchBlocks(cache: JwkSetCache, fetcher: BlockingJwkSetFetcher): List<Result<JWKSet>> {
        val first = KeyRequest(cache)
        fetcher.awaitFetchStarted()
        val waiting = KeyRequest(cache)
        waiting.awaitBlocked()
        fetcher.release()
        return listOf(first.awaitOutcome(), waiting.awaitOutcome())
    }

    private class KeyRequest(cache: JwkSetCache) {
        private var outcome: Result<JWKSet> = Result.failure(IllegalStateException("키 요청이 ${WAIT_LIMIT.seconds}초 안에 끝나지 않았습니다"))
        private val thread = Thread { outcome = runCatching { cache.getCurrentKeys() } }.apply { start() }

        fun awaitBlocked() {
            val deadline = System.nanoTime() + WAIT_LIMIT.toNanos()
            while (thread.state != Thread.State.BLOCKED) {
                check(System.nanoTime() < deadline) { "키 요청이 진행 중인 조회를 기다리지 않습니다: ${thread.state}" }
                Thread.onSpinWait()
            }
        }

        fun awaitOutcome(): Result<JWKSet> {
            thread.join(WAIT_LIMIT.toMillis())
            return outcome
        }
    }

    private class BlockingJwkSetFetcher(private val respond: () -> String) : JwkSetFetcher {
        private val started = CountDownLatch(1)
        private val released = CountDownLatch(1)
        private val count = AtomicInteger()
        val fetchCount: Int get() = count.get()

        override fun fetch(): String {
            count.incrementAndGet()
            started.countDown()
            check(released.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) { "테스트가 조회를 풀어 주지 않았습니다" }
            return respond()
        }

        fun awaitFetchStarted() = check(started.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) { "조회가 시작되지 않았습니다" }

        fun release() = released.countDown()
    }
}
