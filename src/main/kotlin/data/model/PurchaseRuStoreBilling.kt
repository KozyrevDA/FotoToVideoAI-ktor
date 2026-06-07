package data.model

data class PurchaseRuStoreBilling(
    val emailReg: String,
    val invoiceId: Long,
    val userCoins: Long,
    val newCoins: Long,
    val totalCoins: Long? = null,
    val orderId: String? = null,
    val purchaseId: String? = null,
    val productId: String? = null,
)