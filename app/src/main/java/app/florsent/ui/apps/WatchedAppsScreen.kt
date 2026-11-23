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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
            // edge-to-edge + adjustResize: keep the last rows above the keyboard while searching.
            modifier = Modifier.fillMaxSize().imePadding(),
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
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_clear))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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

/** Synchronous cache lookup for icons; provided by screens that own an InstalledAppsRepository. */
val LocalIconPeek = staticCompositionLocalOf<(String, Int) -> Bitmap?> { { _, _ -> null } }

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
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
internal fun AppIcon(packageName: String, loadIcon: suspend (String, Int) -> Bitmap?) {
    val peekIcon = LocalIconPeek.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    // Seed from the cache so scrolling back never flashes the placeholder.
    val bitmap by produceState(initialValue = peekIcon(packageName, sizePx), packageName) {
        if (value == null) value = loadIcon(packageName, sizePx)
    }
    val shape = RoundedCornerShape(12.dp)
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = remember(image) { image.asImageBitmap() },
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(shape),
        )
    } else {
        Box(Modifier.size(40.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}
