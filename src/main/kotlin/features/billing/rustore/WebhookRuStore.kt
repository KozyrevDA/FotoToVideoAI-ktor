package features.billing.rustore

import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val GCM_IV_LENGTH = 12
private const val GCM_TAG_LENGTH = 16
private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
const val SECRET_KEY = "fQq86GANLKOqxHzkf3hgRIlalOGphBR+kya0E98SLR4="
const val SECRET_KEY_TEST = "Pa5lUhnUb0mfzYm+EHx8qLSubpz3R9Nbkfsc3Mp/v7A="

fun decryptRuStorePayload(
    encryptedInput: String,
    secretKey: String
): String {
    try {
        val decoded = Base64.getDecoder().decode(encryptedInput.toByteArray(StandardCharsets.UTF_8))
        val iv = decoded.copyOfRange(0, GCM_IV_LENGTH)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            convertBase64KeyToSecretKey(secretKey),
            gcmSpec
        )
        val decryptedBytes = cipher.doFinal(
            decoded,
            GCM_IV_LENGTH,
            decoded.size - GCM_IV_LENGTH
        )

        return decryptedBytes.toString(Charsets.UTF_8)

    } catch (e: Exception) {
        throw RuntimeException("Failed to decrypt RuStore payload", e)
    }
}

fun convertBase64KeyToSecretKey(secretKey: String): SecretKey {
    val decodedKey = Base64.getDecoder()
        .decode(secretKey.toByteArray(StandardCharsets.UTF_8))

    require(decodedKey.size == 32) {
        "AES-256 key must be 32 bytes, got ${decodedKey.size}"
    }

    return SecretKeySpec(decodedKey, "AES")
}