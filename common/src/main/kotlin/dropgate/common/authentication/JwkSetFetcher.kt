package dropgate.common.authentication

fun interface JwkSetFetcher {
    fun fetch(): String
}
