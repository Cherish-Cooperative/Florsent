package app.florsent.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

/**
 * Lists launchable apps. Relies on the MAIN/LAUNCHER `<queries>` entry in the manifest, so no
 * QUERY_ALL_PACKAGES permission is needed.
 */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val iconCache = LruCache<String, Bitmap>(64)

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.getDefault())
        packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != appContext.packageName }
            .distinctBy { it.packageName }
            .map { InstalledApp(it.packageName, it.loadLabel(packageManager).toString()) }
            .sortedWith(compareBy(collator) { it.label })
            .toList()
    }

    suspend fun installedPackages(candidates: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        candidates.filterTo(linkedSetOf()) { pkg ->
            runCatching { packageManager.getApplicationInfo(pkg, 0) }.isSuccess
        }
    }

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? {
        iconCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
            }.getOrNull()?.also { iconCache.put(packageName, it) }
        }
    }
}
