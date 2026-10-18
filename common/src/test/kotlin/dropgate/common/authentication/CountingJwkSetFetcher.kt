package dropgate.common.authentication

internal class CountingJwkSetFetcher(private val respond: (attempt: Int) -> String) : JwkSetFetcher {
    var fetchCount = 0
        private set

    override fun fetch(): String {
        fetchCount++
        return respond(fetchCount)
    }
}
