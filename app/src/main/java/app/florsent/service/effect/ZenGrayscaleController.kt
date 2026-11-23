package app.florsent.service.effect

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenDeviceEffects
import android.util.Log
import androidx.annotation.RequiresApi
import app.florsent.MainActivity
import app.florsent.R

/**
 * Android 15+ true-grayscale finish. When the guidance level reaches the top, a Florsent-owned
 * AutomaticZenRule (Modes) is activated whose only device effect is grayscale. Its interruption
 * filter is ALL, so notifications and sounds are untouched.
 *
 * Fail-safe: every platform call is guarded; without notification-policy access, on older Android,
 * or on any error, this controller simply does nothing and the dim overlay remains the effect.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class ZenGrayscaleController(
    private val context: Context,
    private val isEnabled: () -> Boolean,
) : VisualEffectController {

    private var active = false

    override fun render(effect: VisualEffect) {
        val allowed = isEnabled() && ZenGrayscale.hasAccess(context)
        // Hysteresis so a level hovering around the top does not flicker the whole display.
        val want = allowed && effect.level >= if (active) OFF_BELOW else ON_AT
        if (want != active) setActive(want)
    }

    override fun release() {
        if (active) setActive(false)
    }

    private fun setActive(on: Boolean) {
        runCatching {
            val id = ZenGrayscale.ensureRule(context)
            val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
            ZenGrayscale.manager(context).setAutomaticZenRuleState(
                id, Condition(ZenGrayscale.CONDITION_ID, context.getString(R.string.zen_rule_name), state),
            )
        }.onFailure { Log.w(TAG, "Could not switch grayscale finish to $on", it) }
        // Even on failure, don't retry every tick; the next level change re-evaluates.
        active = on
    }

    private companion object {
        const val TAG = "ZenGrayscale"
        const val ON_AT = 0.98f
        const val OFF_BELOW = 0.9f
    }
}

/** Rule bookkeeping and permission helpers shared by the controller and the settings UI. */
object ZenGrayscale {
    val CONDITION_ID: Uri = Uri.parse("condition://app.florsent/grayscale_finish")

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    fun manager(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun hasAccess(context: Context): Boolean =
        runCatching { manager(context).isNotificationPolicyAccessGranted }.getOrDefault(false)

    fun accessSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /** Returns the id of our rule, creating it on first use. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun ensureRule(context: Context): String {
        val nm = manager(context)
        nm.automaticZenRules.entries.firstOrNull { it.value.conditionId == CONDITION_ID }?.let { return it.key }
        val rule = AutomaticZenRule.Builder(context.getString(R.string.zen_rule_name), CONDITION_ID)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            .setDeviceEffects(ZenDeviceEffects.Builder().setShouldDisplayGrayscale(true).build())
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setTriggerDescription(context.getString(R.string.zen_rule_trigger))
            .setManualInvocationAllowed(false)
            .build()
        return nm.addAutomaticZenRule(rule)
    }

    /** Deactivates and deletes our rule so nothing is left in the user's Modes list. */
    fun removeRule(context: Context) {
        if (!isSupported || !hasAccess(context)) return
        runCatching {
            val nm = manager(context)
            nm.automaticZenRules.entries
                .filter { it.value.conditionId == CONDITION_ID }
                .forEach { nm.removeAutomaticZenRule(it.key) }
        }.onFailure { Log.w("ZenGrayscale", "Could not remove rule", it) }
    }
}
