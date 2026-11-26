package app.florsent.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import app.florsent.service.FlorsentAccessibilityService

/**
 * Opens system settings pages as close to Florsent's own switch as each phone allows.
 *
 * Android has no public way to open one accessibility service's page: the details action needs
 * OPEN_ACCESSIBILITY_DETAILS_SETTINGS, a signature|installer permission. So the accessibility list
 * is opened with the search-highlight extras that AOSP-based Settings (Pixel and most others) use
 * to scroll to and flash an entry. Samsung's One UI puts downloaded services on a separate
 * "Installed apps" page that no public intent reaches, so the app shows Samsung-specific steps
 * instead (see [isSamsung]). Every launch falls back to the Settings home if a page is missing.
 */
object SystemSettings {

    /** One UI lists downloaded accessibility services under Accessibility › Installed apps. */
    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun openAccessibility(context: Context): Boolean {
        val key = ComponentName(context, FlorsentAccessibilityService::class.java).flattenToString()
        val highlight = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })
        return launch(context, highlight, Intent(Settings.ACTION_SETTINGS))
    }

    /** The Do Not Disturb (Modes) access list; some skins only have it under Special app access. */
    fun openZenAccess(context: Context): Boolean = launch(
        context,
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    /** Florsent's App info page, where "Allow restricted settings" lives (⋮ menu). */
    fun openAppInfo(context: Context): Boolean = launch(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        Intent(Settings.ACTION_SETTINGS),
    )

    /**
     * Android 13+ greys out the accessibility switch ("Restricted setting") for apps installed from a
     * downloaded or local APK file, until the user allows restricted settings in App info. Installs
     * from Google Play, the Galaxy Store and adb are not affected.
     */
    fun mayBeRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val source = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        }.getOrNull() ?: return false
        return source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
            source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    }

    /** Starts the first intent the phone can handle. */
    private fun launch(context: Context, vararg intents: Intent): Boolean {
        for (intent in intents) {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    /** AOSP SettingsActivity extras: the preference key to scroll to and highlight. */
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
}
