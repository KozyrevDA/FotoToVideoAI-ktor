package data.remote

sealed class ResultApiAI<out T, out E> {
    data class Success<T>(val data: T) : ResultApiAI<T, Nothing>()
    data class Error<E>(val error: E?) : ResultApiAI<Nothing, E>()
    data object ErrorSafetySystem : ResultApiAI<Nothing, Nothing>()
    data object Empty : ResultApiAI<Nothing, Nothing>()
}