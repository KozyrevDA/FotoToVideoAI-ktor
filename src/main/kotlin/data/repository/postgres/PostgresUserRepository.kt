package data.repository.postgres

import data.model.User
import data.repository.UserRepository
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update

class PostgresUserRepository : UserRepository {
    override suspend fun createUser(user: User): Unit = suspendTransaction {
        UsersDAO.new {
            name = user.name
            email = user.email
            coins = user.coins
            emailReg = user.emailReg
            pass = user.pass
            salt = user.salt
            refreshToken = user.refreshToken
            authMethod = user.authMethod
            deviceUid = user.deviceUid
            typeApp = user.typeApp.name
            idVk = user.idVk
            idApple = user.idApple
            isProcessing = user.isProcessing
        }
    }

    override suspend fun getUserByEmailReg(emailReg: String): User? = suspendTransaction {
        UsersDAO.find { (UsersTable.emailReg eq emailReg) }
            .limit(1)
            .map(::userDaoToModel)
            .firstOrNull()
    }

    override suspend fun getUserByIdVk(idVk: String): User? = suspendTransaction {
        UsersDAO.find { (UsersTable.idVk eq idVk) and (UsersTable.authMethod eq "VK") }
            .limit(1)
            .map(::userDaoToModel)
            .firstOrNull()
    }

    override suspend fun getUserByIdApple(idApple: String): User? = suspendTransaction {
        UsersDAO.find { (UsersTable.idApple eq idApple) and (UsersTable.authMethod eq "APPLE") }
            .limit(1)
            .map(::userDaoToModel)
            .firstOrNull()
    }

    override suspend fun getUserByRefToken(refreshToken: String): User? = suspendTransaction {
        UsersDAO.find { (UsersTable.refreshToken eq refreshToken) }
            .limit(1)
            .map(::userDaoToModel)
            .firstOrNull()
    }

    override suspend fun updateRefreshToken(user: User): Unit = suspendTransaction {
        UsersTable.update({ UsersTable.emailReg eq user.emailReg }) {
            it[refreshToken] = user.refreshToken
        }
    }

    override suspend fun addIdVk(user: User, newIdVk: String): Unit = suspendTransaction {
        UsersTable.update({ UsersTable.emailReg eq user.emailReg }) {
            it[idVk] = newIdVk
        }
    }

    override suspend fun updateFromDevice(user: User): Unit = suspendTransaction {
        UsersTable.update({ UsersTable.emailReg eq user.emailReg }) {
            if (!user.name.isNullOrBlank()) it[name] = user.name
            if (!user.email.isNullOrBlank()) it[email] = user.email
            it[deviceUid] = user.deviceUid
            if (!user.idVk.isNullOrBlank()) it[idVk] = user.idVk
        }
    }

    override suspend fun delete(user: User): Unit = suspendTransaction {
        UsersTable.deleteWhere { emailReg eq user.emailReg }
    }

    override suspend fun changeFakeEmailToRegistration(fakeEmail: String, user: User): Unit = suspendTransaction {
        val userTemp = UsersDAO.find { (UsersTable.emailReg eq fakeEmail) }
            .limit(1)
            .map(::userDaoToModel)
            .firstOrNull() ?: return@suspendTransaction

        UsersTable.update({ UsersTable.emailReg eq user.emailReg }) {
            if (!userTemp.name.isNullOrBlank()) it[name] = userTemp.name
            if (!userTemp.email.isNullOrBlank()) it[email] = userTemp.email
            it[deviceUid] = null
            if (!user.idVk.isNullOrBlank()) it[idVk] = user.idVk
        }

        UsersTable.deleteWhere { emailReg eq fakeEmail }
    }

    override suspend fun updateCoins(user: User, resultCount: Int): Unit = suspendTransaction {
        UsersTable.update({ UsersTable.emailReg eq user.emailReg }) {
            it[coins] = resultCount
        }
    }

    override suspend fun updateIsProcessing(user: User, value: Boolean): Unit = suspendTransaction {
        UsersTable.update({ UsersTable.emailReg eq user.emailReg }) {
            it[isProcessing] = value
        }
    }

    override suspend fun getActualCoinsCount(user: User): Int = suspendTransaction {
        UsersDAO.find { (UsersTable.emailReg eq user.emailReg) }
            .limit(1)
            .map(::userDaoToModel)
            .first()
            .coins
    }
}