package features.billing.rustore.dto

import kotlinx.serialization.Serializable

@Serializable
data class RustorePurchaseResponse(
    val code: String,
    val message: String? = null,
    val body: PurchaseBody? = null,
    val timestamp: String
)

@Serializable
data class PurchaseBody(
    val invoiceId: Long,
    val invoiceDate: String,
    val refundDate: String? = null,
    val invoiceStatus: String,
    val developerPayload: String? = null,
    val appId: Long,
    val ownerCode: Long,
    val purchaseId: String,
    val paymentInfo: PaymentInfo? = null,
    val order: Order? = null
)

@Serializable
data class PaymentInfo(
    val paymentDate: String? = null,
    val maskedPan: String? = null,
    val paymentSystem: String? = null,
    val paymentWay: String? = null,
    val paymentWayCode: String? = null,
    val bankName: String? = null
)

@Serializable
data class Order(
    val orderId: String,
    val orderNumber: String? = null,
    val visualName: String,
    val amountCreate: Long,
    val amountCurrent: Long,
    val currency: String,
    val itemCode: String,
    val description: String,
    val language: String
)