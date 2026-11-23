package app.florsent.service.effect

import app.florsent.domain.SaturationCommand
import app.florsent.domain.SaturationPlanner

/** Something that can set an app's layer saturation; the Shizuku implementation lives elsewhere. */
interface SaturationBackend {
    /** Cheap check: the backend is reachable and authorized right now. */
    fun isReady(): Boolean

    /** Applies one level. Returns false on any failure; must never throw. */
    fun setLayerSaturation(callerPackage: String, targetPackage: String, saturation: Int): Boolean
}

/**
 * Packages we may have left desaturated. Persisted synchronously *before* a package is desaturated,
 * so after a crash, a kill or a reboot of our process we know exactly what to restore.
 */
interface DirtyPackageStore {
    fun all(): Set<String>
    fun add(packageName: String)
    fun remove(packageName: String)
}

/**
 * Plain-Kotlin state holder for the per-app saturation layer (no Android types, unit-tested on the
 * JVM). Not thread-safe: the controller confines all calls to one worker thread.
 *
 * Guarantees:
 * - every package that stops being the target is restored to 100 before anything else happens;
 * - a package is recorded as dirty before it is ever desaturated, and only cleared after a
 *   successful restore, so [recover] can always undo leftovers;
 * - any failure switches the session to degraded: it stops desaturating, tries to restore what it
 *   touched, and leaves the other effect layers to carry the guidance.
 */
class SaturationSession(
    private val backend: SaturationBackend,
    private val dirty: DirtyPackageStore,
    private val callerPackage: String,
) {
    private val applied = mutableMapOf<String, Int>()

    /** True after a failure; cleared by a successful [recover]. */
    var degraded = false
        private set

    /** Packages that still need a restore (for status UI). */
    val pendingRestores: Set<String> get() = dirty.all() + applied.keys

    /**
     * Restores everything left over from an earlier run and clears [degraded]. Call when the
     * backend becomes ready (service start, Shizuku binder received, permission granted).
     */
    fun recover(): Boolean {
        if (!backend.isReady()) return false
        val ok = restoreAll()
        if (ok) degraded = false
        return ok
    }

    fun render(targetPackage: String?, level: Float, enabled: Boolean) {
        if (!backend.isReady()) return // cannot change anything; recover() will clean up later
        // Never desaturate ourselves (e.g. the preview starts while Florsent is still in front).
        val target = targetPackage?.takeUnless { it == callerPackage }
        val desired = if (degraded) emptyMap() else SaturationPlanner.desired(target, level, enabled)
        for (command in SaturationPlanner.commands(applied, desired)) {
            if (!execute(command)) {
                degraded = true
                restoreAll()
                return
            }
        }
    }

    /** Restores every package we touched or may have touched. Returns true if all succeeded. */
    fun restoreAll(): Boolean {
        var ok = true
        for (pkg in (applied.keys + dirty.all()).toSortedSet()) {
            if (!execute(SaturationCommand(pkg, SaturationPlanner.FULL))) ok = false
        }
        return ok
    }

    private fun execute(command: SaturationCommand): Boolean {
        val restoring = command.saturation >= SaturationPlanner.FULL
        if (!restoring) dirty.add(command.packageName) // persist intent before acting
        val ok = backend.setLayerSaturation(callerPackage, command.packageName, command.saturation)
        if (ok) {
            if (restoring) {
                applied.remove(command.packageName)
                dirty.remove(command.packageName)
            } else {
                applied[command.packageName] = command.saturation
            }
        }
        return ok
    }
}
