package dev.gatsyuk.soloranking.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class SheetAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Overflow ("3 dots") actions are presented as a bottom sheet app-wide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionBottomSheet(
    title: String? = null,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            actions.forEach { action ->
                val tint = if (action.destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
                ListItem(
                    headlineContent = { Text(action.label, color = tint) },
                    leadingContent = action.icon?.let {
                        { Icon(it, contentDescription = null, tint = tint) }
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        action.onClick()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
