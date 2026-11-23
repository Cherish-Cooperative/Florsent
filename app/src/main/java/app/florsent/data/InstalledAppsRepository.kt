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
    /** Sized in bytes (about 4 MB, ~650 icons at 40dp/xxhdpi) and keyed by size. */
    private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

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

    /** Cache-only lookup for the first frame; never touches PackageManager. */
    fun peekIcon(packageName: String, sizePx: Int): Bitmap? = iconCache.get(cacheKey(packageName, sizePx))

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? {
        val key = cacheKey(packageName, sizePx)
        iconCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
            }.getOrNull()?.also { iconCache.put(key, it) }
        }
    }

    private fun cacheKey(packageName: String, sizePx: Int) = "$packageName@$sizePx"

    private companion object {
        const val ICON_CACHE_BYTES = 4 * 1024 * 1024
    }
}
