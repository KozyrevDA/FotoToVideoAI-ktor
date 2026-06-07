package plugins

import data.model.DatabaseConfig
import data.repository.postgres.QueueGenTable
import data.repository.postgres.RuStoreBillingTable
import data.repository.postgres.TrialUsageTable
import data.repository.postgres.UsersTable
import data.repository.postgres.VideosTable
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases(config: DatabaseConfig) {
    Database.connect(url = config.url, user = config.user, password = config.password)

    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            UsersTable,
            RuStoreBillingTable,
            VideosTable,
            QueueGenTable,
            TrialUsageTable
        )
    }
}