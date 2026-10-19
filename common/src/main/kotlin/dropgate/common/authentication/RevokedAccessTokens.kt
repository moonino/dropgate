package dropgate.common.authentication

fun interface RevokedAccessTokens {
    fun isRevoked(tokenId: String): Boolean
}
