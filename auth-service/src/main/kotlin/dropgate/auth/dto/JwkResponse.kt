package dropgate.auth.dto

import dropgate.auth.service.PublicSigningKey

data class JwkResponse(val kty: String, val kid: String, val use: String, val alg: String, val n: String, val e: String) {
    companion object {
        fun createFrom(key: PublicSigningKey): JwkResponse = JwkResponse(key.kty, key.kid, key.use, key.alg, key.n, key.e)
    }
}
