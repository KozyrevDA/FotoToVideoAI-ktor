package data.repository.postgres

import data.model.PurchaseRuStoreBilling
import data.model.User
import data.repository.RuStoreBillingRepository
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.update

class PostgresRuStoreBillingRepository : RuStoreBillingRepository {
    override suspend fun upsertPurchase(purchase: PurchaseRuStoreBilling): Unit = suspendTransaction {
        val updatedRows = RuStoreBillingTable.update({ RuStoreBillingTable.invoiceId eq purchase.invoiceId }) {
            it[orderId] = purchase.orderId
            it[userCoins] = purchase.userCoins
            it[newCoins] = purchase.newCoins
            it[totalCoins] = purchase.totalCoins
            it[purchaseId] = purchase.purchaseId
            it[productId] = purchase.productId
        }

        if (updatedRows == 0) {
            RuStoreBillingTable.insertIgnore {
                it[emailReg] = purchase.emailReg
                it[invoiceId] = purchase.invoiceId
                it[userCoins] = purchase.userCoins
                it[newCoins] = purchase.newCoins
                it[totalCoins] = purchase.totalCoins
                it[orderId] = purchase.orderId
                it[purchaseId] = purchase.purchaseId
                it[productId] = purchase.productId
            }
        }
    }

    override suspend fun getAllPurchases(user: User): List<PurchaseRuStoreBilling> = suspendTransaction {
        RuStoreBillingDAO.find { (RuStoreBillingTable.emailReg eq user.emailReg) }
            .map(::rustoreBillingDaoToModel)
    }
}