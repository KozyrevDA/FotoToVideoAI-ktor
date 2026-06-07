package data.repository

import data.model.User

interface UserRepository {
    suspend fun createUser(user: User)
    suspend fun getUserByEmailReg(email: String): User?
    suspend fun getUserByIdVk(idVk: String): User?
    suspend fun getUserByIdApple(idApple: String): User?
    suspend fun getUserByRefToken(refreshToken: String): User?
    suspend fun updateRefreshToken(user: User)
    suspend fun addIdVk(user: User, newIdVk: String)
    suspend fun updateFromDevice(user: User)
    suspend fun delete(user: User)
    suspend fun changeFakeEmailToRegistration(fakeEmail: String, user: User)
    suspend fun updateCoins(user: User, resultCount: Int)
    suspend fun updateIsProcessing(user: User, value: Boolean)
    suspend fun getActualCoinsCount(user: User): Int
}