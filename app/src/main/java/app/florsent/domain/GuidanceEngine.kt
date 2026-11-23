package app.florsent.domain

import app.florsent.domain.GuidanceEvent.BreathingCancelled
import app.florsent.domain.GuidanceEvent.BreathingCompleted
import app.florsent.domain.GuidanceEvent.ForegroundChanged
import app.florsent.domain.GuidanceEvent.NudgeAccepted
import app.florsent.domain.GuidanceEvent.NudgeDismissed
import app.florsent.domain.GuidanceEvent.PreviewStarted
import app.florsent.domain.GuidanceEvent.PreviewStopped
import app.florsent.domain.GuidanceEvent.ScreenChanged
import app.florsent.domain.GuidanceEvent.Tick
import java.time.Instant
import java.time.ZoneId

/**
 * Pure state machine for budget tracking and gradual guidance. Every call is deterministic given
 * (state, event, config), which keeps the Android side a thin adapter.
 */
class GuidanceEngine(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun reduce(state: GuidanceState, event: GuidanceEvent, config: GuidanceConfig): GuidanceState {
        val now = event.nowMs
        val wasCounting = state.isCounting(config)

        // Account for the time since the previous event under the *previous* foreground app (and the
        // previous day), then roll over the day.
        var s = state.accumulate(now, config).rollDay(now).expireTimers(now, config)

        s = when (event) {
            is ForegroundChanged -> {
                val moved = s.copy(foregroundPackage = event.packageName)
                // Going back to a watched app abandons an unfinished breathing session.
                if (moved.mode == GuidanceMode.Breathing && moved.isWatched(config)) moved.toTracking(now) else moved
            }
            is ScreenChanged -> s.copy(screenOn = event.on)
            is Tick -> s
            is NudgeAccepted -> s.copy(
                mode = GuidanceMode.Breathing,
                modeSinceMs = now,
                breathingFromPreview = s.previewStartedAtMs != null,
                previewStartedAtMs = null,
            )
            is NudgeDismissed ->
                if (s.previewStartedAtMs != null) s.copy(previewStartedAtMs = null)
                else s.copy(nudgeSnoozedUntilMs = now + config.nudgeSnoozeMs)
            is BreathingCompleted -> when {
                s.mode == GuidanceMode.Breathing && s.breathingFromPreview -> s.toTracking(now)
                s.mode == GuidanceMode.Breathing -> s.startCooldown(now, config)
                // Breathing started from the home screen while already over budget also earns a rest.
                s.mode == GuidanceMode.Tracking && s.overBudgetMs > 0 -> s.startCooldown(now, config)
                else -> s
            }
            is BreathingCancelled -> if (s.mode == GuidanceMode.Breathing) s.toTracking(now) else s
            is PreviewStarted -> s.copy(previewStartedAtMs = now)
            is PreviewStopped -> s.copy(previewStartedAtMs = null)
        }

        if (!wasCounting && s.isCounting(config)) {
            s = s.resetSessionIfStale(now, config).copy(lastWatchedAtMs = now)
        }
        return s
    }

    fun effect(state: GuidanceState, config: GuidanceConfig, nowMs: Long): GuidanceEffect {
        state.previewStartedAtMs?.let { start ->
            val elapsed = (nowMs - start).coerceAtLeast(0)
            if (elapsed >= config.preview.durationMs) return GuidanceEffect.None
            return GuidanceEffect(
                level = Easing.smoothstep(elapsed.toFloat() / config.preview.fadeMs),
                showNudge = elapsed >= config.preview.nudgeAtMs,
                targetPackage = null,
                isPreview = true,
            )
        }
        if (state.mode != GuidanceMode.Tracking || !state.isCounting(config) || state.overBudgetMs <= 0) {
            return GuidanceEffect.None
        }
        val progress = fadeProgress(state, config)
        return GuidanceEffect(
            level = Easing.smoothstep(progress),
            showNudge = progress >= config.nudgeAtProgress && nowMs >= state.nudgeSnoozedUntilMs,
            targetPackage = state.foregroundPackage,
            isPreview = false,
        )
    }

    fun status(state: GuidanceState, config: GuidanceConfig, nowMs: Long): GuidanceStatus {
        val preview = state.previewStartedAtMs
        return when {
            preview != null && nowMs - preview < config.preview.durationMs -> GuidanceStatus.Preview
            state.mode == GuidanceMode.Breathing -> GuidanceStatus.Breathing
            state.mode == GuidanceMode.Cooldown && nowMs < state.cooldownUntilMs ->
                GuidanceStatus.Cooldown(state.cooldownUntilMs)
            state.mode == GuidanceMode.Tracking && state.overBudgetMs > 0 -> GuidanceStatus.Guiding
            else -> GuidanceStatus.Idle
        }
    }

    /** Whether the runtime should send [Tick] events every second. */
    fun needsTick(state: GuidanceState, config: GuidanceConfig): Boolean =
        state.previewStartedAtMs != null || state.isCounting(config)

    fun dailyUsedMs(state: GuidanceState, nowMs: Long): Long =
        if (state.epochDay == epochDay(nowMs)) state.dailyUsedMs else 0

    fun fadeProgress(state: GuidanceState, config: GuidanceConfig): Float =
        if (config.fadeDurationMs <= 0) 1f
        else (state.overBudgetMs.toFloat() / config.fadeDurationMs).coerceIn(0f, 1f)

    private fun epochDay(nowMs: Long): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun GuidanceState.rollDay(now: Long): GuidanceState {
        val today = epochDay(now)
        return if (today == epochDay) this else copy(epochDay = today, dailyUsedMs = 0)
    }

    private fun GuidanceState.accumulate(now: Long, config: GuidanceConfig): GuidanceState {
        val last = lastTickAtMs
        val base = copy(lastTickAtMs = now)
        if (last == null || !isCounting(config)) return base
        val delta = (now - last).coerceIn(0, config.maxTickGapMs)
        if (delta == 0L) return base

        val daily = dailyUsedMs + delta
        if (mode != GuidanceMode.Tracking) return base.copy(dailyUsedMs = daily, lastWatchedAtMs = now)

        val session = sessionUsedMs + delta
        val overPortion = maxOf(session - config.sessionBudgetMs, daily - config.dailyBudgetMs)
            .coerceIn(0, delta)
        return base.copy(
            dailyUsedMs = daily,
            sessionUsedMs = session,
            overBudgetMs = overBudgetMs + overPortion,
            lastWatchedAtMs = now,
        )
    }

    private fun GuidanceState.expireTimers(now: Long, config: GuidanceConfig): GuidanceState {
        var s = this
        if (s.mode == GuidanceMode.Cooldown && now >= s.cooldownUntilMs) {
            s = s.copy(mode = GuidanceMode.Tracking, modeSinceMs = now).clearSession()
        }
        if (s.mode == GuidanceMode.Breathing && now - s.modeSinceMs >= config.breathingTimeoutMs) {
            s = s.toTracking(now)
        }
        val preview = s.previewStartedAtMs
        if (preview != null && now - preview >= config.preview.durationMs) {
            s = s.copy(previewStartedAtMs = null)
        }
        return s
    }

    private fun GuidanceState.resetSessionIfStale(now: Long, config: GuidanceConfig): GuidanceState {
        val lastWatched = lastWatchedAtMs ?: return this
        val stale = mode == GuidanceMode.Tracking && now - lastWatched >= config.sessionGapMs
        return if (stale) clearSession() else this
    }

    private fun GuidanceState.clearSession() =
        copy(sessionUsedMs = 0, overBudgetMs = 0, nudgeSnoozedUntilMs = 0)

    private fun GuidanceState.toTracking(now: Long) =
        copy(mode = GuidanceMode.Tracking, modeSinceMs = now, breathingFromPreview = false)

    private fun GuidanceState.startCooldown(now: Long, config: GuidanceConfig) =
        clearSession().copy(
            mode = GuidanceMode.Cooldown,
            modeSinceMs = now,
            cooldownUntilMs = now + config.cooldownMs,
            breathingFromPreview = false,
        )
}
