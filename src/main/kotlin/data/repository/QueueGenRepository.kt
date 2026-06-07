package data.repository

import data.model.QueueGen
import data.model.User

interface QueueGenRepository {
    suspend fun upsert(queueGen: QueueGen)
    suspend fun getByUid(
        uid: String,
        user: User
    ): QueueGen?

    suspend fun getAll(
        status: String,
        user: User
    ): List<QueueGen>
}