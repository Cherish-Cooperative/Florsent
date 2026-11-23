package app.florsent.data

import app.florsent.domain.OpenSpans
import app.florsent.domain.UsageCodec
import app.florsent.domain.UsageRecord
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The on-device usage log: one tab-separated file per UTC day under `files/usage/`, 30-day rolling.
 * Every operation runs on one dedicated IO thread, so appends, the open-span state, reads and
 * pruning never race and never touch the main thread.
 */
class UsageLogStore(
    private val dir: File,
    val io: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "florsent-usage-log").apply { isDaemon = true }
    }.asCoroutineDispatcher(),
) {
    private val openFile get() = File(dir, "open.state")

    suspend fun append(records: List<UsageRecord>) = withContext(io) {
        if (records.isEmpty()) return@withContext
        dir.mkdirs()
        records.groupBy { fileFor(it.startSec) }.forEach { (file, lines) ->
            FileOutputStream(file, true).bufferedWriter().use { w ->
                lines.forEach { w.write(UsageCodec.encode(it)); w.newLine() }
            }
        }
    }

    suspend fun saveOpen(open: OpenSpans) = withContext(io) {
        dir.mkdirs()
        val tmp = File(dir, "open.state.tmp")
        tmp.writeText(
            listOf(open.running, open.screenStartMs ?: -1, open.appStartMs ?: -1, open.appPackage ?: "", open.lastEventMs)
                .joinToString("\t"),
        )
        tmp.renameTo(openFile) // atomic replace on the same filesystem
    }

    suspend fun loadOpen(): OpenSpans = withContext(io) {
        val f = openFile.takeIf { it.exists() }?.readText()?.split('\t') ?: return@withContext OpenSpans()
        runCatching {
            OpenSpans(
                running = f[0].toBoolean(),
                screenStartMs = f[1].toLong().takeIf { it >= 0 },
                appStartMs = f[2].toLong().takeIf { it >= 0 },
                appPackage = f[3].ifEmpty { null },
                lastEventMs = f[4].toLong(),
            )
        }.getOrDefault(OpenSpans())
    }

    /**
     * Records starting in [fromSec, toSec), oldest first; malformed lines are skipped. Only the day
     * files that exist are opened (at most ~31), whatever the requested range.
     */
    suspend fun read(fromSec: Long, toSec: Long): List<UsageRecord> = withContext(io) {
        val firstDay = Math.floorDiv(fromSec, 86_400L) - 1 // a record may start the UTC day before
        val lastDay = Math.floorDiv(toSec, 86_400L)
        dayFiles()
            .filter { (day, _) -> day.toEpochDay() in firstDay..lastDay }
            .sortedBy { it.first }
            .flatMap { (_, file) -> file.readLines().mapNotNull(UsageCodec::decode) }
            .filter { it.startSec in fromSec until toSec }
            .sortedBy { it.startSec }
    }

    private fun dayFiles(): List<Pair<LocalDate, File>> =
        dir.listFiles { f -> f.name.endsWith(".tsv") }.orEmpty().mapNotNull { f ->
            runCatching { LocalDate.parse(f.name.removeSuffix(".tsv")) }.getOrNull()?.let { it to f }
        }

    /** Deletes day files older than [keepDays]. */
    suspend fun prune(nowSec: Long, keepDays: Int = KEEP_DAYS) = withContext(io) {
        val cutoff = LocalDate.ofEpochDay(Math.floorDiv(nowSec, 86_400L) - keepDays)
        dayFiles().filter { it.first < cutoff }.forEach { it.second.delete() }
    }

    /** Removes everything (the user's "clear usage history"). */
    suspend fun clear() = withContext(io) {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(startSec: Long) = File(dir, "${LocalDate.ofEpochDay(Math.floorDiv(startSec, 86_400L))}.tsv")

    companion object {
        const val KEEP_DAYS = 30
    }
}
