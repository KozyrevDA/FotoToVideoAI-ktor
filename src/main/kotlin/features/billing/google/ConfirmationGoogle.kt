package features.billing.google

import app.Constants
import com.google.auth.oauth2.GoogleCredentials
import data.model.User
import data.model.getId
import extensions.initHttpClientCIO
import features.billing.google.PurchaseGoogleType.*
import features.billing.google.dto.ProductPurchaseDto
import features.billing.google.dto.SubscriptionPurchaseV2Dto
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import java.io.File

class ConfirmationGoogle(private val logger: Logger) {
    private val client = initHttpClientCIO(smallTimeOuts = true)

    suspend fun verifyPurchase(
        packageName: String,
        productId: String,
        token: String,
        purchaseGoogleType: PurchaseGoogleType,
        user: User
    ): Boolean {
        val accessToken = getAccessToken()

        val url = when (purchaseGoogleType) {
            CONSUMABLE ->
                "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$packageName/purchases/products/$productId/tokens/$token"

            SUBS ->
                "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$packageName/purchases/subscriptionsv2/tokens/$token"

            NON_CONSUMABLE -> TODO("Not provided PurchaseGoogleType.NON_CONSUMABLE")
        }


        return try {
            val response = client.get(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                    append(HttpHeaders.Accept, "application/json")
                }
            }
            val bodyText = response.bodyAsText()

            if (!response.status.isSuccess()) {
                logger.warn("ConfirmationGoogle, verifyPurchase(), user: ${user.getId()}, verification failed (${response.status.value}): $bodyText")
                return false
            }

            when (purchaseGoogleType) {
                CONSUMABLE -> {
                    val data = Constants.JSON.decodeFromString<ProductPurchaseDto>(bodyText)
                    logger.info("ConfirmationGoogle, verifyPurchase(), user: ${user.getId()}, Verified product: $data")

                    val isPurchased = data.purchaseState == 0 // PURCHASED
                    val isAcknowledged = data.acknowledgementState == 1 // PURCHASED

                    isPurchased && isAcknowledged
                }

                SUBS -> {
                    val data = Constants.JSON.decodeFromString<SubscriptionPurchaseV2Dto>(bodyText)
                    logger.info("ConfirmationGoogle, verifyPurchase(), user: ${user.getId()}, Verified subscription: $data")
                    val isActive = data.subscriptionState == "SUBSCRIPTION_STATE_ACTIVE"
                    val isAcknowledged = data.acknowledgementState == "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"

                    isActive && isAcknowledged
                }

                NON_CONSUMABLE -> TODO("Not provided PurchaseGoogleType.NON_CONSUMABLE")
            }
        } catch (e: Exception) {
            logger.error(
                "ConfirmationGoogle, verifyPurchase(), user: ${user.getId()}, ConfirmationGoogle: error verifying purchase",
                e
            )
            false
        }
    }

    /** Получение токена сервисного аккаунта */
    private fun getAccessToken(): String {
        val credentials = GoogleCredentials
            .fromStream(File("raw/google_key/service-account.json").inputStream())
            .createScoped(listOf("https://www.googleapis.com/auth/androidpublisher"))
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}