package app.florsent.service

import app.florsent.data.UsageLogStore
import app.florsent.domain.OpenSpans
import app.florsent.domain.RecorderStep
import app.florsent.domain.UsageRecord
import app.florsent.domain.UsageRecorder
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feeds the usage log from guidance state. Every step runs on the log's single IO thread in submit
 * order, so the persisted open-span state always matches what has been appended.
 *
 * Process death: the open state says "running" but this process never started recording. The first
 * step then closes the unknown stretch as [UsageRecord.Unknown] before anything else, so the
 * learner never mistakes a dead process for a dark screen.
 *
 * Process start: the accessibility service connects a moment after the process starts, so the first
 * input usually says "not recording" for under a second. Closing the leftover stretch right away
 * would write a meaningless End/Begin pair on every restart, so a leftover "running" state waits up
 * to [startupGraceMs] for the service; only if it never comes is the stretch abandoned (Unknown + End).
 */
class UsageRecording(
    private val store: UsageLogStore,
    private val scope: CoroutineScope,
    private val startupGraceMs: Long = STARTUP_GRACE_MS,
    private val offsetAt: (Long) -> Int = { ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(it)).totalSeconds },
) {
    /** What the recorder should reflect right now. */
    data class Input(val recording: Boolean, val screenInUse: Boolean, val watchedApp: String?)

    // Confined to the store's IO thread.
    private var open: OpenSpans? = null
    private var startedHere = false
    private var latest: Input? = null
    private var abandonScheduled = false

    fun submit(input: Input, nowMs: Long) {
        scope.launch(store.io) { step(input, nowMs) }
    }

    /** Deletes all history; if recording, starts a fresh stretch so the learner has a clean Begin. */
    fun clearHistory(nowMs: Long, current: Input) {
        scope.launch(store.io) {
            store.clear()
            open = OpenSpans()
            startedHere = false
            step(current, nowMs)
        }
    }

    private suspend fun step(input: Input, nowMs: Long) {
        latest = input
        val offset = offsetAt(nowMs)
        var state = open ?: store.loadOpen()
        val steps = mutableListOf<RecorderStep>()

        if (state.running && !startedHere && !input.recording) {
            // Left running by a previous process; give the service time to connect first.
            open = state
            if (!abandonScheduled) {
                abandonScheduled = true
                scope.launch(store.io) {
                    delay(startupGraceMs)
                    abandonScheduled = false
                    abandonIfStillIdle(nowMs + startupGraceMs)
                }
            }
            return
        }

        if (state.running && !startedHere) {
            // Left running by a previous process: close it as unknown first.
            steps += UsageRecorder.start(state, nowMs, offset).also { state = it.open }
            startedHere = true
        }
        when {
            input.recording && !state.running ->
                steps += UsageRecorder.start(state, nowMs, offset).also { state = it.open; startedHere = true }
            !input.recording && state.running ->
                steps += UsageRecorder.stop(state, nowMs, offset).also { state = it.open }
        }
        if (input.recording) {
            steps += UsageRecorder.update(state, input.screenInUse, input.watchedApp, nowMs, offset).also { state = it.open }
        }

        store.append(steps.flatMap { it.records })
        if (state != open) store.saveOpen(state)
        open = state
    }

    private suspend fun abandonIfStillIdle(nowMs: Long) {
        val state = open ?: return
        if (startedHere || !state.running || latest?.recording == true) return
        val step = UsageRecorder.abandon(state, nowMs, offsetAt(nowMs))
        store.append(step.records)
        store.saveOpen(step.open)
        open = step.open
    }

    companion object {
        const val STARTUP_GRACE_MS = 10_000L
    }
}
