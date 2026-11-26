package app.florsent.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.florsent.R
import app.florsent.util.SystemSettings

/** Where to find Florsent's switch once the accessibility settings are open, for this phone's skin. */
@Composable
fun accessibilitySteps(): String =
    stringResource(if (SystemSettings.isSamsung) R.string.enable_steps_samsung else R.string.enable_steps_generic)

/** Where to allow Do Not Disturb access, for this phone's skin. */
@Composable
fun zenAccessSteps(): String =
    stringResource(if (SystemSettings.isSamsung) R.string.zen_access_steps_samsung else R.string.zen_access_steps_generic)

/**
 * "Restricted setting" help with a shortcut to App info. Shown only when Florsent was installed from
 * an APK file, the one case where Android 13+ greys out the accessibility switch.
 */
@Composable
fun RestrictedSettingsHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val restricted = remember(context) { SystemSettings.mayBeRestricted(context) }
    if (!restricted) return
    Column(modifier) {
        Text(
            stringResource(R.string.enable_restricted_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { SystemSettings.openAppInfo(context) }) {
            Text(stringResource(R.string.enable_open_app_info))
        }
    }
}
