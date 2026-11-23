package app.florsent.ui.apps

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.florsent.R
import app.florsent.ui.components.FlorsentTopBar

@Composable
fun WatchedAppsScreen(
    state: WatchedAppsUiState,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    loadIcon: suspend (String, Int) -> Bitmap?,
) {
    Scaffold(
        topBar = { FlorsentTopBar(stringResource(R.string.watched_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        var query by rememberSaveable { mutableStateOf("") }
        val matches: (AppRow) -> Boolean = { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        val suggested = state.suggested.filter(matches)
        val others = state.others.filter(matches)
        val selectedCount = state.suggested.count { it.watched } + state.others.count { it.watched }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                Text(
                    stringResource(R.string.watched_intro, selectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.watched_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            if (suggested.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.watched_suggested)) }
                items(suggested, key = { it.packageName }) { AppToggleRow(it, onToggle, loadIcon) }
            }
            if (others.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.watched_others)) }
                items(others, key = { it.packageName }) { AppToggleRow(it, onToggle, loadIcon) }
            }
            if (suggested.isEmpty() && others.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.watched_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
internal fun AppToggleRow(
    row: AppRow,
    onToggle: (String, Boolean) -> Unit,
    loadIcon: suspend (String, Int) -> Bitmap?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = row.watched, role = Role.Switch) { onToggle(row.packageName, it) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(row.packageName, loadIcon)
        Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = row.watched, onCheckedChange = null)
    }
}

@Composable
private fun AppIcon(packageName: String, loadIcon: suspend (String, Int) -> Bitmap?) {
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, packageName) {
        value = loadIcon(packageName, sizePx)
    }
    val shape = RoundedCornerShape(12.dp)
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(shape),
        )
    } else {
        Box(Modifier.size(40.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}
