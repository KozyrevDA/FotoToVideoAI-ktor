package features.billing.google.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionPurchaseV2Dto(
    val kind: String? = null,
    val regionCode: String? = null,
    val startTime: String? = null,
    val subscriptionState: String? = null,
    val latestOrderId: String? = null,
    val linkedPurchaseToken: String? = null,
    val pausedStateContext: String? = null,
    val canceledStateContext: String? = null,
    val testPurchase: String? = null,
    val acknowledgementState: String? = null,
    val externalAccountIdentifiers: ExternalAccountIdentifiers? = null,
    val subscribeWithGoogleInfo: SubscribeWithGoogleInfo? = null,
    val lineItems: List<LineItem>? = null
) {
    @Serializable
    data class ExternalAccountIdentifiers(
        val externalAccountId: String? = null,
        val obfuscatedExternalAccountId: String? = null,
        val obfuscatedExternalProfileId: String? = null
    )

    @Serializable
    data class SubscribeWithGoogleInfo(
        val profileId: String? = null,
        val profileName: String? = null,
        val emailAddress: String? = null,
        val givenName: String? = null,
        val familyName: String? = null
    )

    @Serializable
    data class LineItem(
        val productId: String? = null,
        val expiryTime: String? = null,
        val autoRenewingPlan: AutoRenewingPlan? = null,
        val prepaidPlan: String? = null,
        val offerDetails: OfferDetails? = null
    ) {
        @Serializable
        data class AutoRenewingPlan(
            val autoRenewEnabled: Boolean? = null,
            val recurringPrice: RecurringPrice? = null
        )

        @Serializable
        data class RecurringPrice(
            val units: String? = null,
            val nanos: Int? = null,
            val currencyCode: String? = null
        )

        @Serializable
        data class OfferDetails(
            val basePlanId: String? = null,
            val offerId: String? = null,
            val offerTags: List<String>? = null
        )
    }
}