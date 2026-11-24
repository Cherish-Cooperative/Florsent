package app.florsent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * Chips that never squeeze a label. A plain Row hands the last chip whatever width is left, so with
 * a large font "20 分鐘" wrapped letter by letter into a tall narrow pill. Every label here stays on
 * one line, and when the chips do not fit side by side they move onto another line.
 *
 * Use [EqualChipGrid] for one choice among a few options (10 / 15 / 20 min) and [ChipRow] for
 * independent toggles that keep their natural width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChipGap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(ChipGap),
        content = content,
    )
}

/**
 * Options in equal-width cells: all in one row when the widest label fits a cell, otherwise in
 * fewer columns (down to one per line). Fill it with [PillOption]s.
 */
@Composable
fun EqualChipGrid(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = ChipGap.roundToPx()
        val width = constraints.maxWidth
        val count = measurables.size
        if (count == 0) return@Layout layout(width, 0) {}
        val widest = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val columns = EqualChipGridMath.columns(count, width, gap, widest)
        val cellWidth = EqualChipGridMath.cellWidth(width, gap, columns)
        val rows = measurables.chunked(columns).map { row ->
            val height = row.maxOf { it.minIntrinsicHeight(cellWidth) }
            row.map { it.measure(Constraints.fixed(cellWidth, height)) }
        }
        val totalHeight = rows.sumOf { row -> row.maxOf { it.height } } + gap * (rows.size - 1)
        layout(width, totalHeight) {
            var y = 0
            rows.forEach { row ->
                row.forEachIndexed { i, placeable -> placeable.placeRelative(i * (cellWidth + gap), y) }
                y += row.maxOf { it.height } + gap
            }
        }
    }
}

/** The column count and cell width of [EqualChipGrid]; plain math so it can be unit tested. */
object EqualChipGridMath {
    /**
     * Most columns (at most [count]) whose cells are still at least [widest] px wide, preferring a
     * count that fills every row (3 options: 3 across or one per line, never 2 + 1); at least 1.
     */
    fun columns(count: Int, width: Int, gap: Int, widest: Int): Int {
        val fitting = (count downTo 1).filter { cellWidth(width, gap, it) >= widest }
        return fitting.firstOrNull { count % it == 0 } ?: fitting.firstOrNull() ?: 1
    }

    fun cellWidth(width: Int, gap: Int, columns: Int): Int = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
}

private val ChipGap = 8.dp

/**
 * One option of an [EqualChipGrid]: a pill with a single-line, centered label. Narrower side padding
 * than a FilterChip, so three options still fit across at larger font sizes; the touch target stays
 * 48 dp.
 */
@Composable
fun PillOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .wrapContentHeight()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** Florsent's pill-shaped toggle chip (for a [ChipRow]) with a single-line label. */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, maxLines = 1, softWrap = false)
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier,
    )
}
