package features.billing.google.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductPurchaseDto(
    val kind: String? = null,
    val purchaseTimeMillis: String? = null,
    val purchaseState: Int? = null,
    val consumptionState: Int? = null,
    val developerPayload: String? = null,
    val orderId: String? = null,
    val purchaseType: Int? = null,
    val acknowledgementState: Int? = null,
    val productId: String? = null,
    val purchaseToken: String? = null,
    val quantity: Int? = null,
    val refundableQuantity: Int? = null,
    val regionCode: String? = null,
    val obfuscatedExternalAccountId: String? = null,
    val obfuscatedExternalProfileId: String? = null
)