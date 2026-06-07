package features.hashing

data class SaltedHash(
    val hash: String,
    val salt: String
)