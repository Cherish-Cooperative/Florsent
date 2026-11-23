package app.florsent.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** A real Preferences DataStore backed by a temp file, for JVM tests of the data layer. */
fun testPreferences(dir: File, scope: CoroutineScope, name: String = "test"): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope) { File(dir, "$name.preferences_pb") }
