package com.healthypantry.feature.expiry.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthypantry.app.theme.LocalHealthyPantryExtraColors
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.domain.model.displayLabel

/**
 * Spec: Notification-Denied Fallback
 * (openspec/changes/pantry-tracker/specs/expiry-reminders/spec.md).
 *
 * Stateless in-app fallback surface listing expiring/expired items, so expiry visibility does
 * not depend solely on the system notification permission. Renders nothing when
 * [expiringItems] is empty — callers don't need to guard the call site.
 *
 * Design mockup ("Pantry tab" expiry banner): accent-100 background, a warning icon, a "N items
 * expiring soon" headline, a comma-joined list of item names, and a dismiss (X) button. The
 * dismissed flag is keyed on [expiringItems] itself ([remember] with [expiringItems] as the key)
 * so a dismissed banner reappears automatically once the underlying expiring/expired set actually
 * changes (e.g. a new batch ages into the window) rather than staying hidden forever.
 */
@Composable
fun ExpiryBanner(
    expiringItems: List<ExpiringBatch>,
    modifier: Modifier = Modifier,
) {
    if (expiringItems.isEmpty()) return

    var dismissed by remember(expiringItems) { mutableStateOf(false) }
    if (dismissed) return

    val extraColors = LocalHealthyPantryExtraColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Expiring soon banner" },
        shape = MaterialTheme.shapes.medium,
        color = extraColors.accent100,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = extraColors.accent700,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = "${expiringItems.size} item${if (expiringItems.size == 1) "" else "s"} expiring soon",
                    style = MaterialTheme.typography.titleSmall,
                    color = extraColors.accent800,
                )
                Text(
                    text = expiringItems.joinToString(", ") { it.displayLabel() },
                    style = MaterialTheme.typography.bodySmall,
                    color = extraColors.accent800,
                )
            }
            IconButton(
                onClick = { dismissed = true },
                modifier = Modifier.semantics { contentDescription = "Dismiss expiring items banner" },
            ) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = null, tint = extraColors.accent800)
            }
        }
    }
}
