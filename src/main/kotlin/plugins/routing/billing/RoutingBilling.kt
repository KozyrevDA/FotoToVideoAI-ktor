package plugins.routing.billing

import app.Constants
import data.dto.RuStoreDataPayloadDto
import data.dto.RuStorePayloadDto
import data.dto.RuStoreWebhookDto
import data.model.PurchaseRuStoreBilling
import data.model.Settings
import data.model.getId
import data.repository.ServerRepository
import extensions.getClaim
import features.billing.google.ConfirmationGoogle
import features.billing.google.PurchaseGoogleType
import features.billing.rustore.SECRET_KEY
import features.billing.rustore.SECRET_KEY_TEST
import features.billing.rustore.decryptRuStorePayload
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.flow.first

fun Routing.billing(
    confirmationGoogle: ConfirmationGoogle,
    serverRepository: ServerRepository,
    settings: Settings,
    logger: Logger
) {
    authenticate {
        get("purchase/google/confirm") {
            val emailRegistration = call.getClaim("email_registration") ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                return@get
            }
            val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                return@get
            }
            val purchaseToken = call.request.queryParameters["token"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "token field is required")
                return@get
            }
            val productId = call.request.queryParameters["product_id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "product_id field is required")
                return@get
            }
            val packageName = call.request.queryParameters["package_name"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "package_name field is required")
                return@get
            }
            val purchaseGoogleType = call.request.queryParameters["purchase_type"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "purchase_type field is required")
                return@get
            }

            val response = try {
                confirmationGoogle.verifyPurchase(
                    token = purchaseToken,
                    productId = productId,
                    packageName = packageName,
                    purchaseGoogleType = PurchaseGoogleType.valueOf(purchaseGoogleType),
                    user = user
                )
            } catch (e: Exception) {
                logger.error("Google route: ${user.getId()} failed to get purchase status", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to get purchase status")
                return@get
            }

            if (response) {
                val amountCurrent = when (productId) {
                    "av_monthly_subscription" -> settings.monthSubCoins.first()
                    "av_annual_subscription" -> settings.yearSubCoins.first()
                    "1000_tokenov" -> 1000
                    "2000_tokenov" -> 2000
                    else -> {
                        logger.info("Google route, user: ${user.getId()}, error amount current, order: $productId")
                        0
                    }
                }

                logger.info("Google route, user: ${user.getId()}, plus $amountCurrent tokens")

                serverRepository.user.updateCoins(user = user, resultCount = user.coins + amountCurrent)
            }

            call.respond(HttpStatusCode.OK, response)
        }
    }

    post("purchase/rustore/webhook") {
        val webhook = call.receive<RuStoreWebhookDto>()
        val decryptedJson = decryptRuStorePayload(
            encryptedInput = webhook.payload,
            secretKey = SECRET_KEY
        )

        logger.info("post(\"purchase/rustore/webhook\"), $decryptedJson")

        val payload = Constants.JSON.decodeFromString<RuStorePayloadDto>(decryptedJson)
        val dataPayload = Constants.JSON.decodeFromString<RuStoreDataPayloadDto>(payload.data)
        val emailReg = dataPayload.developerPayload ?: run {
            logger.info("post(\"purchase/rustore/webhook\"), orderId: ${dataPayload.orderId}, email_reg null")
            return@post
        }
        val user = serverRepository.user.getUserByEmailReg(emailReg) ?: run {
            logger.info("post(\"purchase/rustore/webhook\"), orderId: ${dataPayload.orderId}, User $emailReg not found")
            return@post
        }
        val amountCurrent = when (dataPayload.productCode) {
            "FV_monthly_subscription" -> settings.monthSubCoins.first()
            "FV_annual_subscription" -> if (dataPayload.periodNew?.equals("PROMO", true) == true) {
                settings.yearSubPromoStartCoins.first()
            } else {
                settings.yearSubPromoCoins.first()
            }

            "400_Tokenov" -> 400
            "1600_Tokenov" -> 1600
            "2600_Tokenov" -> 2600
            "14000_Tokenov" -> 14000
            else -> {
                logger.info("post(\"purchase/rustore/webhook\"), user: $emailReg, orderId: ${dataPayload.orderId}, error amount current")
                0
            }
        }

        if (
            dataPayload.statusNew?.equals("confirmed", true) == true ||
            dataPayload.statusNew?.equals("ACTIVE", true) == true
        ) {
            val purchaseExist = serverRepository.ruStoreBilling.getAllPurchases(user = user)
                .any { it.orderId == dataPayload.orderId }

            if (purchaseExist) {
                logger.info("post(\"purchase/rustore/webhook\"), user: $emailReg, orderId: ${dataPayload.orderId}, already exists")
                call.respond(HttpStatusCode.OK)
                return@post
            }

            val newTotal = user.coins + amountCurrent
            logger.info("post(\"purchase/rustore/webhook\"), user: $emailReg, plus $amountCurrent tokens")

            serverRepository.user.updateCoins(user = user, resultCount = newTotal)
            serverRepository.ruStoreBilling.upsertPurchase(
                PurchaseRuStoreBilling(
                    emailReg = user.emailReg,
                    invoiceId = dataPayload.invoiceId?.toLongOrNull() ?: 0L,
                    userCoins = user.coins.toLong(),
                    newCoins = amountCurrent.toLong(),
                    totalCoins = newTotal.toLong(),
                    orderId = dataPayload.orderId,
                    purchaseId = dataPayload.purchaseId,
                    productId = dataPayload.productCode,
                )
            )
        } else if (dataPayload.statusNew?.equals("refunded", true) == true) {
            val newTotal = user.coins - amountCurrent
            logger.info("post(\"purchase/rustore/webhook\"), user: $emailReg, minus $amountCurrent tokens")

            serverRepository.user.updateCoins(user = user, resultCount = newTotal)
            serverRepository.ruStoreBilling.upsertPurchase(
                PurchaseRuStoreBilling(
                    emailReg = user.emailReg,
                    invoiceId = dataPayload.invoiceId?.toLongOrNull() ?: 0L,
                    userCoins = user.coins.toLong(),
                    newCoins = -amountCurrent.toLong(),
                    totalCoins = newTotal.toLong(),
                    orderId = dataPayload.orderId,
                    purchaseId = dataPayload.purchaseId,
                    productId = dataPayload.productCode,
                )
            )
        }

        call.respond(HttpStatusCode.OK)
    }

    post("purchase/rustore/webhook/test") {
        val webhook = call.receive<RuStoreWebhookDto>()
        val decryptedJson = decryptRuStorePayload(
            encryptedInput = webhook.payload,
            secretKey = SECRET_KEY_TEST
        )

        logger.info("post(\"purchase/rustore/webhook/test\"), $decryptedJson")

        val payload = Constants.JSON.decodeFromString<RuStorePayloadDto>(decryptedJson)
        val dataPayload = Constants.JSON.decodeFromString<RuStoreDataPayloadDto>(payload.data)
        val emailReg = dataPayload.developerPayload ?: run {
            logger.info("post(\"purchase/rustore/webhook/test\"), orderId: ${dataPayload.orderId}, email_reg null")
            return@post
        }
        val user = serverRepository.user.getUserByEmailReg(emailReg) ?: run {
            logger.info("post(\"purchase/rustore/webhook/test\"), orderId: ${dataPayload.orderId}, User $emailReg not found")
            return@post
        }
        val amountCurrent = when (dataPayload.productCode) {
            "FV_monthly_subscription" -> settings.monthSubCoins.first()
            "FV_annual_subscription" -> if (dataPayload.periodNew?.equals("PROMO", true) == true) {
                settings.yearSubPromoStartCoins.first()
            } else {
                settings.yearSubPromoCoins.first()
            }

            "400_Tokenov" -> 400
            "1600_Tokenov" -> 1600
            "2600_Tokenov" -> 2600
            "14000_Tokenov" -> 14000
            else -> {
                logger.info("post(\"purchase/rustore/webhook/test\"), user: $emailReg, orderId: ${dataPayload.orderId}, error amount current")
                0
            }
        }

        if (
            dataPayload.statusNew?.equals("confirmed", true) == true ||
            dataPayload.statusNew?.equals("ACTIVE", true) == true
        ) {
            val purchaseExist = serverRepository.ruStoreBilling.getAllPurchases(user = user)
                .any { it.orderId == dataPayload.orderId }

            if (purchaseExist) {
                logger.info("post(\"purchase/rustore/webhook/test\"), user: $emailReg, orderId: ${dataPayload.orderId}, already exists")
                call.respond(HttpStatusCode.OK)
                return@post
            }

            val newTotal = user.coins + amountCurrent
            logger.info("post(\"purchase/rustore/webhook/test\"), user: $emailReg, plus $amountCurrent tokens")

            serverRepository.user.updateCoins(user = user, resultCount = newTotal)
            serverRepository.ruStoreBilling.upsertPurchase(
                PurchaseRuStoreBilling(
                    emailReg = user.emailReg,
                    invoiceId = dataPayload.invoiceId?.toLongOrNull() ?: 0L,
                    userCoins = user.coins.toLong(),
                    newCoins = amountCurrent.toLong(),
                    totalCoins = newTotal.toLong(),
                    orderId = dataPayload.orderId,
                    purchaseId = dataPayload.purchaseId,
                    productId = dataPayload.productCode,
                )
            )
        } else if (dataPayload.statusNew?.equals("refunded", true) == true) {
            val newTotal = user.coins - amountCurrent
            logger.info("post(\"purchase/rustore/webhook/test\"), user: $emailReg, minus $amountCurrent tokens")

            serverRepository.user.updateCoins(user = user, resultCount = newTotal)
            serverRepository.ruStoreBilling.upsertPurchase(
                PurchaseRuStoreBilling(
                    emailReg = user.emailReg,
                    invoiceId = dataPayload.invoiceId?.toLongOrNull() ?: 0L,
                    userCoins = user.coins.toLong(),
                    newCoins = -amountCurrent.toLong(),
                    totalCoins = newTotal.toLong(),
                    orderId = dataPayload.orderId,
                    purchaseId = dataPayload.purchaseId,
                    productId = dataPayload.productCode,
                )
            )
        }

        call.respond(HttpStatusCode.OK)
    }
}