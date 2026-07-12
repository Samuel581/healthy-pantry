package com.healthypantry.core.common

/**
 * Domain-level result type carrying a typed error instead of a [Throwable].
 *
 * Kotlin's built-in `kotlin.Result` only models failure as a [Throwable], which loses
 * structured domain information (e.g. which unit pair failed to resolve, and for which
 * food item). Accuracy-critical domain logic — starting with [com.healthypantry.core.unit.UnitConverter] —
 * needs to surface a specific, matchable error type to callers rather than an exception message,
 * so this project defines its own two-type-parameter [Result].
 */
sealed interface Result<out T, out E> {
    data class Success<out T>(val value: T) : Result<T, Nothing>
    data class Failure<out E>(val error: E) : Result<Nothing, E>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): E? = (this as? Failure)?.error

    companion object {
        fun <T> success(value: T): Result<T, Nothing> = Success(value)
        fun <E> failure(error: E): Result<Nothing, E> = Failure(error)
    }
}

inline fun <T, E, R> Result<T, E>.fold(onSuccess: (T) -> R, onFailure: (E) -> R): R =
    when (this) {
        is Result.Success -> onSuccess(value)
        is Result.Failure -> onFailure(error)
    }

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(value))
        is Result.Failure -> this
    }
