package app.florsent.ui.breathe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.florsent.R

/**
 * P2 placeholder that closes the guidance loop (accept → breathe → complete → cooldown).
 * P3 replaces the static visual with the Canvas breathing animation and pattern picker.
 */
@Composable
fun BreatheScreen(onComplete: () -> Unit, onCancel: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(220.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to primary.copy(alpha = 0.55f),
                    0.6f to container.copy(alpha = 0.6f),
                    1f to container.copy(alpha = 0f),
                ),
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(stringResource(R.string.breathe_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.breathe_placeholder_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.breathe_complete))
        }
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.breathe_later))
        }
    }
}
