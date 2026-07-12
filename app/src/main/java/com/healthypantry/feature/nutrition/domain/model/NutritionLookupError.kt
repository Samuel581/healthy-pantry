package com.healthypantry.feature.nutrition.domain.model

/**
 * Failure modes for a nutrition lookup. [RateLimited] is kept distinct from [NotFound] per
 * spec scenario "USDA API key not configured" — the two must not be reported the same way, so
 * a user hitting the shared `DEMO_KEY` quota understands to configure a real key rather than
 * assuming their search simply had no matches.
 */
sealed interface NutritionLookupError {
    /** No matching product/food found for the given barcode or name query. */
    data object NotFound : NutritionLookupError

    /** The source rejected the request due to rate limiting (e.g. `DEMO_KEY` quota exhausted). */
    data object RateLimited : NutritionLookupError

    /** Any other non-2xx HTTP response. */
    data class ApiError(val code: Int, val message: String?) : NutritionLookupError

    /** Transport-level failure (no connectivity, timeout, etc.). */
    data class NetworkError(val message: String?) : NutritionLookupError
}
