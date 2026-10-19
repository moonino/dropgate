package dropgate.common.authentication

class InMemoryRevokedAccessTokens(private val revokedTokenIds: Set<String> = emptySet()) : RevokedAccessTokens {
    var lookupCount = 0
        private set

    override fun isRevoked(tokenId: String): Boolean {
        lookupCount++
        return tokenId in revokedTokenIds
    }
}
