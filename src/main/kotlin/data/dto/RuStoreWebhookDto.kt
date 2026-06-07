package data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RuStoreWebhookDto(
    val id: String,
    val timestamp: String,
    val payload: String
)

@Serializable
data class RuStorePayloadDto(
    @SerialName("app_id") val appId: Long,
    @SerialName("notification_type") val notificationType: String,
    val data: String
)

@Serializable
data class RuStoreDataPayloadDto(
    @SerialName("change_status_time") val changeStatusTime: String? = null,
    @SerialName("product_code") val productCode: String? = null,
    @SerialName("status_new") val statusNew: String? = null,
    @SerialName("status_old") val statusOld: String? = null,
    @SerialName("purchase_token") val purchaseToken: String? = null,
    @SerialName("invoice_id") val invoiceId: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("purchase_id") val purchaseId: String? = null,
    @SerialName("developer_payload") val developerPayload: String? = null,
    @SerialName("period_new") val periodNew: String? = null,
)