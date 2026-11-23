package app.florsent.ui.grounding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.florsent.R
import app.florsent.ui.breathe.BreathingFlower
import kotlin.random.Random

/**
 * "Back to the present": one short sensory prompt, lots of space, one clear way out.
 *
 * @param cooldownMinutes shown when finishing starts a quiet period; null otherwise.
 */
@Composable
fun GroundingScreen(
    cooldownMinutes: Int?,
    bedtime: Boolean = false,
    onReturnToLife: () -> Unit,
) {
    val prompts = stringArrayResource(if (bedtime) R.array.grounding_prompts_bedtime else R.array.grounding_prompts)
    var index by rememberSaveable { mutableIntStateOf(Random.nextInt(prompts.size)) }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BreathingFlower(expansion = { 0.35f }, timeSeconds = { 0f }, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.grounding_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(28.dp))
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(350)) },
            label = "prompt",
        ) { i ->
            Text(
                prompts[i],
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal,
                    lineHeight = 40.sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { index = nextIndex(index, prompts.size) }) {
            Text(stringResource(R.string.grounding_another), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1.3f))
        if (cooldownMinutes != null) {
            Text(
                stringResource(R.string.grounding_cooldown, cooldownMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Button(
            onClick = onReturnToLife,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).height(56.dp),
        ) {
            Text(
                stringResource(if (bedtime) R.string.grounding_return_bedtime else R.string.grounding_return),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
    }
}

private fun nextIndex(current: Int, size: Int): Int {
    if (size <= 1) return current
    val next = Random.nextInt(size - 1)
    return if (next >= current) next + 1 else next
}
