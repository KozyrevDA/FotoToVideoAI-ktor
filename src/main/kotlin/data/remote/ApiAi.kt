package data.remote

import data.model.ChatMessage
import data.model.QueueGen
import data.model.User

interface ApiAi {
    suspend fun generateVideo(
        chatMessage: ChatMessage,
        photo1: ByteArray?,
        photo2: ByteArray?,
        user: User,
        queueGen: QueueGen,
        kieAi: Boolean,
        retry: Int = 0
    ): ResultApiAI<Any, Any>

    suspend fun getVideo(
        idVideo: String,
        user: User
    ): ResultApiAI<Any, Any>

    fun close()
}