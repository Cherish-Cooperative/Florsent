package app.florsent.service

import app.florsent.data.UsageLogStore
import app.florsent.domain.BedtimeLearner
import app.florsent.domain.BedtimeLearning
import app.florsent.domain.UsageInsights
import app.florsent.domain.UsageSummary
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class UsageAnalysisResult(val learning: BedtimeLearning, val summary: UsageSummary)

/**
 * Reads the last 30 days off the main thread and runs the learner and the insight rules. Results are
 * cached; a refresh within a minute of the last one is free.
 */
class UsageAnalysis(
    private val store: UsageLogStore,
    private val clock: () -> Long,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val _result = MutableStateFlow<UsageAnalysisResult?>(null)
    val result: StateFlow<UsageAnalysisResult?> = _result.asStateFlow()

    private val mutex = Mutex()
    private var computedAtMs = Long.MIN_VALUE

    suspend fun refresh(force: Boolean = false): UsageAnalysisResult = mutex.withLock {
        val now = clock()
        _result.value?.takeIf { !force && now - computedAtMs < MIN_INTERVAL_MS }?.let { return it }
        val records = store.read(now / 1000 - LOOKBACK_SEC, now / 1000 + 1)
        val result = withContext(Dispatchers.Default) {
            val offset = zone().rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds
            val learning = BedtimeLearner.estimate(BedtimeLearner.observe(records, now), records, now, offset)
            val today = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
            UsageAnalysisResult(learning, UsageInsights.summarize(records, today, now, learning))
        }
        computedAtMs = now
        _result.value = result
        result
    }

    fun invalidate() {
        computedAtMs = Long.MIN_VALUE
    }

    private companion object {
        const val MIN_INTERVAL_MS = 60_000L
        const val LOOKBACK_SEC = 31L * 86_400
    }
}
