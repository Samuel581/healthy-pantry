package com.healthypantry.feature.expiry.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.domain.model.displayLabel

/**
 * Spec: Notification-Denied Fallback
 * (openspec/changes/pantry-tracker/specs/expiry-reminders/spec.md).
 *
 * Stateless in-app fallback surface listing expiring/expired items, so expiry visibility does
 * not depend solely on the system notification permission. Renders nothing when
 * [expiringItems] is empty — callers don't need to guard the call site.
 */
@Composable
fun ExpiryBanner(
    expiringItems: List<ExpiringBatch>,
    modifier: Modifier = Modifier,
) {
    if (expiringItems.isEmpty()) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Expiring soon banner" },
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Expiring soon (${expiringItems.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            expiringItems.forEach { item ->
                Text(
                    text = item.displayLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
