package com.healthypantry.feature.expiry.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthypantry.feature.expiry.data.repo.ExpiryAlertRepository
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ExpiryAlertUiState(
    val expiringItems: List<ExpiringBatch> = emptyList(),
)

/**
 * Spec: Notification-Denied Fallback
 * (openspec/changes/pantry-tracker/specs/expiry-reminders/spec.md).
 *
 * Reactively surfaces the same expiring/expired items [com.healthypantry.feature.expiry.worker
 * .ExpiryCheckWorker] would notify about, so the in-app banner/badge
 * ([com.healthypantry.feature.expiry.ui.ExpiryBanner]) doesn't depend on the worker having run or
 * on the `POST_NOTIFICATIONS` permission being granted.
 */
@HiltViewModel
class ExpiryAlertViewModel @Inject constructor(
    expiryAlertRepository: ExpiryAlertRepository,
) : ViewModel() {

    val uiState: StateFlow<ExpiryAlertUiState> =
        expiryAlertRepository.observeExpiringSoon()
            .map { items -> ExpiryAlertUiState(expiringItems = items) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ExpiryAlertUiState(),
            )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
