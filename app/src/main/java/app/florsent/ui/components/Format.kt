package app.florsent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.florsent.R

/** 23:40 from minutes after midnight. */
fun clockText(minuteOfDay: Int): String {
    val m = ((minuteOfDay % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}

/** "45 分鐘", "2 小時", "1 小時 10 分鐘". */
@Composable
fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.duration_minutes, m)
        m == 0 -> stringResource(R.string.duration_hours, h)
        else -> stringResource(R.string.duration_hours_minutes, h, m)
    }
}
