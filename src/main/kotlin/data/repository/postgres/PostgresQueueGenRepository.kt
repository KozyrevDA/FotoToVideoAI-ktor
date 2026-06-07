package data.repository.postgres

import data.model.QueueGen
import data.model.User
import data.repository.QueueGenRepository
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.update

class PostgresQueueGenRepository : QueueGenRepository {
    override suspend fun upsert(queueGen: QueueGen): Unit = suspendTransaction {
        val updatedRows = QueueGenTable.update({ QueueGenTable.uid eq queueGen.uid }) {
            it[status] = queueGen.status.name
            it[idVideo] = queueGen.idVideo
        }

        if (updatedRows == 0) {
            QueueGenTable.insertIgnore {
                it[uid] = queueGen.uid
                it[emailReg] = queueGen.emailReg
                it[orientationType] = queueGen.orientationType.name
                it[apiAiType] = queueGen.apiAiType.name
                it[status] = queueGen.status.name
                it[idVideo] = queueGen.idVideo
                it[createdAt] = queueGen.createdAt
                it[userPrompt] = queueGen.userPrompt
            }
        }
    }

    override suspend fun getByUid(
        uid: String,
        user: User
    ): QueueGen? = suspendTransaction {
        QueueGenDAO.find { (QueueGenTable.emailReg eq user.emailReg) and (QueueGenTable.uid eq uid) }
            .limit(1)
            .map(::queueGenDaoToModel)
            .firstOrNull()
    }

    override suspend fun getAll(status: String, user: User): List<QueueGen> = suspendTransaction {
        QueueGenDAO.find {
            (QueueGenTable.emailReg eq user.emailReg) and (QueueGenTable.status eq status)
        }.map(::queueGenDaoToModel)
    }
}