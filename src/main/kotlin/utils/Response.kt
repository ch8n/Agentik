package utils

sealed class Response<out T> {
    data class Success<T>(val value: T) : Response<T>()
    data class Error(val error: Throwable) : Response<Nothing>()

    fun onSuccess(action: (value: T) -> Unit) {
        if (this is Success) {
            action.invoke(this.value)
        }
    }

    fun onError(action: (error: Throwable) -> Unit) {
        if (this is Error) {
            action.invoke(this.error)
        }
    }

    companion object {
        fun <T> build(action: () -> T): Response<T> {
            return try {
                Success(action.invoke())
            } catch (e: Throwable) {
                Error(e)
            }
        }
    }
}