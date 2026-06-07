package data.repository

import data.model.PurchaseRuStoreBilling
import data.model.User

interface RuStoreBillingRepository {
    suspend fun upsertPurchase(purchase: PurchaseRuStoreBilling)
    suspend fun getAllPurchases(user: User): List<PurchaseRuStoreBilling>
}