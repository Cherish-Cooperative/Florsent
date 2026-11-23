package app.florsent.domain

/**
 * One line of the on-device usage log. Times are epoch seconds plus the UTC offset in effect at the
 * time, so past nights keep their original wall-clock times after travel or DST changes.
 */
sealed interface UsageRecord {
    val startSec: Long
    val offsetSec: Int

    /** A watched app in front with the screen unlocked. */
    data class App(override val startSec: Long, val durationSec: Long, override val offsetSec: Int, val packageName: String) : UsageRecord

    /** The screen unlocked and in use (no app identity). */
    data class Screen(override val startSec: Long, val durationSec: Long, override val offsetSec: Int) : UsageRecord

    /** Recording started (service connected). */
    data class Begin(override val startSec: Long, override val offsetSec: Int) : UsageRecord

    /** Recording stopped cleanly. */
    data class End(override val startSec: Long, override val offsetSec: Int) : UsageRecord

    /** We do not know what happened (process killed, phone off): never treat as "screen off". */
    data class Unknown(override val startSec: Long, val durationSec: Long, override val offsetSec: Int) : UsageRecord
}

/** Tab-separated, one record per line; tolerant of damaged lines. */
object UsageCodec {
    fun encode(r: UsageRecord): String = when (r) {
        is UsageRecord.App -> "A\t${r.startSec}\t${r.durationSec}\t${r.offsetSec}\t${r.packageName}"
        is UsageRecord.Screen -> "S\t${r.startSec}\t${r.durationSec}\t${r.offsetSec}"
        is UsageRecord.Begin -> "B\t${r.startSec}\t0\t${r.offsetSec}"
        is UsageRecord.End -> "E\t${r.startSec}\t0\t${r.offsetSec}"
        is UsageRecord.Unknown -> "U\t${r.startSec}\t${r.durationSec}\t${r.offsetSec}"
    }

    /** Null for anything malformed (e.g. a half-written last line after a crash). */
    fun decode(line: String): UsageRecord? {
        val f = line.split('\t')
        if (f.size < 4) return null
        val start = f[1].toLongOrNull() ?: return null
        val duration = f[2].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val offset = f[3].toIntOrNull()?.takeIf { it in -18 * 3600..18 * 3600 } ?: return null
        return when (f[0]) {
            "A" -> f.getOrNull(4)?.takeIf { it.isNotBlank() }?.let { UsageRecord.App(start, duration, offset, it) }
            "S" -> UsageRecord.Screen(start, duration, offset)
            "B" -> UsageRecord.Begin(start, offset)
            "E" -> UsageRecord.End(start, offset)
            "U" -> UsageRecord.Unknown(start, duration, offset)
            else -> null
        }
    }
}

/**
 * What is open right now, persisted on every change so a killed process can be detected and the
 * missing time turned into an [UsageRecord.Unknown] interval on the next start.
 */
data class OpenSpans(
    val running: Boolean = false,
    val screenStartMs: Long? = null,
    val appStartMs: Long? = null,
    val appPackage: String? = null,
    val lastEventMs: Long = 0,
)

/** Output of one recorder step: records to append and the new open state to persist. */
data class RecorderStep(val records: List<UsageRecord>, val open: OpenSpans)

/**
 * Turns "is the screen in use" / "which watched app is in front" into log records. Pure: the caller
 * supplies time and offset and persists [RecorderStep.open] after appending the records.
 */
object UsageRecorder {

    /** Spans shorter than this are noise (a flicker between two events). */
    private const val MIN_SPAN_MS = 1_000L

    /** Recording starts: close anything left open by a killed process as Unknown, then Begin. */
    fun start(previous: OpenSpans, nowMs: Long, offsetSec: Int): RecorderStep {
        val records = mutableListOf<UsageRecord>()
        if (previous.running) {
            // No clean End: from the earliest thing we cannot vouch for until now is unknown.
            val from = listOfNotNull(previous.screenStartMs, previous.appStartMs, previous.lastEventMs.takeIf { it > 0 })
                .minOrNull()
            if (from != null && nowMs > from) {
                records += UsageRecord.Unknown(from / 1000, (nowMs - from) / 1000, offsetSec)
            }
        }
        records += UsageRecord.Begin(nowMs / 1000, offsetSec)
        return RecorderStep(records, OpenSpans(running = true, lastEventMs = nowMs))
    }

    /** Recording stops cleanly: close open spans, then End. */
    fun stop(open: OpenSpans, nowMs: Long, offsetSec: Int): RecorderStep {
        val closed = update(open, screenInUse = false, watchedApp = null, nowMs = nowMs, offsetSec = offsetSec)
        return RecorderStep(
            closed.records + UsageRecord.End(nowMs / 1000, offsetSec),
            OpenSpans(running = false, lastEventMs = nowMs),
        )
    }

    /** A state change while recording. */
    fun update(open: OpenSpans, screenInUse: Boolean, watchedApp: String?, nowMs: Long, offsetSec: Int): RecorderStep {
        if (!open.running) return RecorderStep(emptyList(), open)
        val records = mutableListOf<UsageRecord>()
        var screenStart = open.screenStartMs
        var appStart = open.appStartMs
        var appPkg = open.appPackage
        val app = watchedApp?.takeIf { screenInUse }

        // App session ends when the app changes or the screen stops being used.
        if (appStart != null && appPkg != app) {
            if (nowMs - appStart >= MIN_SPAN_MS) {
                records += UsageRecord.App(appStart / 1000, (nowMs - appStart) / 1000, offsetSec, appPkg!!)
            }
            appStart = null
            appPkg = null
        }
        if (screenStart != null && !screenInUse) {
            if (nowMs - screenStart >= MIN_SPAN_MS) {
                records += UsageRecord.Screen(screenStart / 1000, (nowMs - screenStart) / 1000, offsetSec)
            }
            screenStart = null
        }
        if (screenStart == null && screenInUse) screenStart = nowMs
        if (appStart == null && app != null) {
            appStart = nowMs
            appPkg = app
        }
        return RecorderStep(
            records,
            OpenSpans(running = true, screenStartMs = screenStart, appStartMs = appStart, appPackage = appPkg, lastEventMs = nowMs),
        )
    }
}
