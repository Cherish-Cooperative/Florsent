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

/** Platform side of the grayscale finish, so the switching logic below is testable on the JVM. */
interface ZenRuleGateway {
    fun hasAccess(): Boolean

    /** Creates the rule if needed and turns it on. May throw; the controller handles it. */
    fun activate()

    /** Turns the rule off if it exists; never creates one. May throw. */
    fun deactivate()
}

/**
 * Android 15+ true-grayscale finish. When the guidance level reaches the top, a Florsent-owned
 * AutomaticZenRule (Modes) is activated whose only device effect is grayscale. Its interruption
 * filter is ALL, so notifications and sounds are untouched.
 *
 * Fail-safe: every platform call is guarded; without notification-policy access, on older Android,
 * or on any error, this controller simply does nothing and the dim overlay remains the effect.
 */
class ZenGrayscaleController(
    private val gateway: ZenRuleGateway,
    private val isEnabled: () -> Boolean,
) : VisualEffectController {

    /**
     * Null until the first render. The rule's state outlives our process (crash, update, low-memory
     * kill), so the first render always syncs the platform instead of assuming "off".
     */
    private var active: Boolean? = null

    override fun render(effect: VisualEffect) {
        // Hysteresis so a level hovering around the top does not flicker the whole display.
        val nearTop = effect.level >= if (active == true) OFF_BELOW else ON_AT
        // Binder calls (access check) only when the answer can matter.
        val want = nearTop && isEnabled() && gateway.hasAccess()
        if (want != active) setActive(want)
    }

    override fun release() {
        if (active != false) setActive(false)
    }

    private fun setActive(on: Boolean) {
        runCatching {
            when {
                on -> gateway.activate()
                // Without access there is nothing we may touch (and nothing we could have turned on).
                gateway.hasAccess() -> gateway.deactivate()
            }
        }.onFailure { Log.w(TAG, "Could not switch grayscale finish to $on", it) }
        // Even on failure, don't retry every tick; the next level change re-evaluates.
        active = on
    }

    companion object {
        private const val TAG = "ZenGrayscale"
        const val ON_AT = 0.98f
        const val OFF_BELOW = 0.9f
    }
}

/** The real gateway: one Florsent-owned AutomaticZenRule. */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class PlatformZenRuleGateway(private val context: Context) : ZenRuleGateway {
    override fun hasAccess() = ZenGrayscale.hasAccess(context)

    override fun activate() = setState(ZenGrayscale.ensureRule(context), Condition.STATE_TRUE)

    override fun deactivate() {
        ZenGrayscale.findRule(context)?.let { setState(it, Condition.STATE_FALSE) }
    }

    private fun setState(id: String, state: Int) {
        ZenGrayscale.manager(context).setAutomaticZenRuleState(
            id, Condition(ZenGrayscale.CONDITION_ID, context.getString(R.string.zen_rule_name), state),
        )
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

    /** The id of our rule if it exists. Requires notification-policy access. */
    fun findRule(context: Context): String? =
        manager(context).automaticZenRules.entries.firstOrNull { it.value.conditionId == CONDITION_ID }?.key

    /** Returns the id of our rule, creating it on first use. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun ensureRule(context: Context): String {
        val nm = manager(context)
        findRule(context)?.let { return it }
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
