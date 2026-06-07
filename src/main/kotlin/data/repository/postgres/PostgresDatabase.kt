package data.repository.postgres

import data.model.*
import data.shared.app.TypeApp
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

object UsersTable : IntIdTable("users") {
    val emailReg = varchar("email_reg", 255).uniqueIndex()
    val typeApp = text("type_app").default(TypeApp.FOTO_TO_VIDEO_AI.name)
    val coins = integer("coins").default(0)
    val name = varchar("name", 255).nullable()
    val email = varchar("email", 255).nullable()
    val pass = varchar("pass", 255).nullable()
    val salt = text("salt").default("")
    var refreshToken = text("refresh_token").default("")
    val authMethod = varchar("auth_method", 255).nullable()
    val deviceUid = text("device_uid").nullable()
    val idVk = text("id_vk").nullable()
    val idApple = text("id_apple").nullable()
    val isProcessing = bool("is_processing")
}

class UsersDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UsersDAO>(UsersTable)

    var emailReg by UsersTable.emailReg
    var typeApp by UsersTable.typeApp
    var coins by UsersTable.coins
    var name by UsersTable.name
    var email by UsersTable.email
    var pass by UsersTable.pass
    var salt by UsersTable.salt
    var refreshToken by UsersTable.refreshToken
    var authMethod by UsersTable.authMethod
    var deviceUid by UsersTable.deviceUid
    var idVk by UsersTable.idVk
    var idApple by UsersTable.idApple
    var isProcessing by UsersTable.isProcessing
}

object RuStoreBillingTable : IntIdTable("rustore_billing") {
    val emailReg = text("email_reg")
    val invoiceId = long("invoice_id").uniqueIndex()
    val userCoins = long("user_coins")
    val newCoins = long("new_coins")
    val totalCoins = long("total_coins").nullable()
    val orderId = text("order_id").nullable()
    val purchaseId = text("purchase_id").nullable()
    val productId = text("product_id").nullable()
}

class RuStoreBillingDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RuStoreBillingDAO>(RuStoreBillingTable)

    var emailReg by RuStoreBillingTable.emailReg
    var invoiceId by RuStoreBillingTable.invoiceId
    var userCoins by RuStoreBillingTable.userCoins
    var newCoins by RuStoreBillingTable.newCoins
    var totalCoins by RuStoreBillingTable.totalCoins
    var orderId by RuStoreBillingTable.orderId
    var purchaseId by RuStoreBillingTable.purchaseId
    var productId by RuStoreBillingTable.productId
}

object VideosTable : IntIdTable("videos") {
    val emailReg = text("email_reg")
    val idVideo = text("id_video").uniqueIndex()
    val isDeleted = bool("is_deleted")
    val orientationType = varchar("orientation_type", 15)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
}

class VideosDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<VideosDAO>(VideosTable)

    var emailReg by VideosTable.emailReg
    var idVideo by VideosTable.idVideo
    var isDeleted by VideosTable.isDeleted
    var orientationType by VideosTable.orientationType
    var createdAt by VideosTable.createdAt
}

object QueueGenTable : IdTable<String>("queue_gen") {
    val uid = text("uid").uniqueIndex()
    val emailReg = text("email_reg")
    val orientationType = text("orientation_type")
    val apiAiType = text("api_ai_type")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val status = text("status")
    val idVideo = text("id_video").nullable()
    val userPrompt = text("user_prompt").nullable()

    override val id: Column<EntityID<String>> = uid.entityId()
}

class QueueGenDAO(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, QueueGenDAO>(QueueGenTable)

    var uid by QueueGenTable.uid
    var emailReg by QueueGenTable.emailReg
    var orientationType by QueueGenTable.orientationType
    var apiAiType by QueueGenTable.apiAiType
    var createdAt by QueueGenTable.createdAt
    var status by QueueGenTable.status
    var idVideo by QueueGenTable.idVideo
    var userPrompt by QueueGenTable.userPrompt
}


fun userDaoToModel(dao: UsersDAO) = User(
    emailReg = dao.emailReg,
    typeApp = TypeApp.valueOf(dao.typeApp),
    coins = dao.coins,
    name = dao.name,
    email = dao.email,
    pass = dao.pass,
    salt = dao.salt,
    refreshToken = dao.refreshToken,
    authMethod = dao.authMethod,
    deviceUid = dao.deviceUid,
    idVk = dao.idVk,
    idApple = dao.idApple,
    isProcessing = dao.isProcessing
)

fun rustoreBillingDaoToModel(dao: RuStoreBillingDAO) = PurchaseRuStoreBilling(
    emailReg = dao.emailReg,
    invoiceId = dao.invoiceId,
    userCoins = dao.userCoins,
    newCoins = dao.newCoins,
    totalCoins = dao.totalCoins,
    orderId = dao.orderId,
    purchaseId = dao.purchaseId,
    productId = dao.productId,
)

fun videosDaoToModel(dao: VideosDAO) = Video(
    emailReg = dao.emailReg,
    idVideo = dao.idVideo,
    isDeleted = dao.isDeleted,
    orientationType = OrientationType.valueOf(dao.orientationType),
    createdAt = dao.createdAt
)

fun queueGenDaoToModel(dao: QueueGenDAO) = QueueGen(
    uid = dao.uid,
    emailReg = dao.emailReg,
    orientationType = OrientationType.valueOf(dao.orientationType),
    apiAiType = ApiAiType.valueOf(dao.apiAiType),
    createdAt = dao.createdAt,
    status = QueueGenStatus.valueOf(dao.status),
    idVideo = dao.idVideo,
    userPrompt = dao.userPrompt
)

object TrialUsageTable : IntIdTable("trial_usage") {
    val deviceUid = text("device_uid").uniqueIndex()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
}

class TrialUsageDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TrialUsageDAO>(TrialUsageTable)

    var deviceUid by TrialUsageTable.deviceUid
    var createdAt by TrialUsageTable.createdAt
}

suspend fun <T> suspendTransaction(block: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = block)