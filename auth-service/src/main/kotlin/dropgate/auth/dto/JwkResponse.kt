package dropgate.auth.dto

data class JwkResponse(val kty: String, val kid: String, val use: String, val alg: String, val n: String, val e: String)
