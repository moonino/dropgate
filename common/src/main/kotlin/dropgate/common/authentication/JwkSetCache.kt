package dropgate.common.authentication

import com.nimbusds.jose.jwk.JWKSet
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import java.text.ParseException
import java.time.Clock
import java.time.Duration
import java.time.Instant

private val JWKS_CACHE_TTL = Duration.ofMinutes(10)

class JwkSetCache(private val fetcher: JwkSetFetcher, private val clock: Clock) {
    @Volatile
    private var latest: FetchOutcome = FetchedKeys(JWKSet(), Instant.MIN)

    internal fun currentKeys(): JWKSet {
        val observed = latest
        return if (observed.isFreshAt(clock.instant())) observed.keys() else refreshOrShare(observed)
    }

    @Synchronized
    private fun refreshOrShare(observed: FetchOutcome): JWKSet {
        if (latest === observed) latest = fetch()
        return latest.keys()
    }

    private fun fetch(): FetchOutcome {
        val fetchedAt = clock.instant()
        return try {
            FetchedKeys(parse(fetcher.fetch()), fetchedAt.plus(JWKS_CACHE_TTL))
        } catch (exception: DropgateException) {
            FetchFailure(exception)
        }
    }

    private fun parse(json: String): JWKSet = try {
        JWKSet.parse(json)
    } catch (exception: ParseException) {
        throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "JWKS 형식이 올바르지 않습니다", cause = exception)
    }

    private sealed interface FetchOutcome {
        fun isFreshAt(now: Instant): Boolean

        fun keys(): JWKSet
    }

    private class FetchedKeys(private val keys: JWKSet, private val expiresAt: Instant) : FetchOutcome {
        override fun isFreshAt(now: Instant): Boolean = now.isBefore(expiresAt)

        override fun keys(): JWKSet = keys
    }

    private class FetchFailure(private val failure: DropgateException) : FetchOutcome {
        override fun isFreshAt(now: Instant): Boolean = false

        override fun keys(): JWKSet = throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "JWKS 조회가 실패했습니다: ${failure.message}", cause = failure)
    }
}
