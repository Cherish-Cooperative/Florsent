package app.florsent.service.shizuku

import app.florsent.service.effect.DirtyPackageStore
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuSaturationTest {

    private class FakeShell : ShellHandle {
        var alive = true
        var fail = false
        val levels = mutableMapOf<String, Int>()
        override fun isAlive() = alive
        override fun setLayerSaturation(level: Int, callerPackage: String, targetPackage: String): Int {
            if (fail) return -1
            if (level >= 100) levels.remove(targetPackage) else levels[targetPackage] = level
            return 0
        }
    }

    private class FakeClient : ShizukuClient {
        var running = false
        var installed = false
        var preV11 = false
        var permission = false
        var binds = 0
        var permissionRequests = 0
        private var onConnected: ((ShellHandle) -> Unit)? = null
        private var onDisconnected: (() -> Unit)? = null
        lateinit var binderReceived: () -> Unit
        lateinit var binderDead: () -> Unit
        lateinit var permissionResult: () -> Unit

        override fun pingBinder() = running
        override fun isPreV11() = preV11
        override fun hasPermission() = permission
        override fun requestPermission() { permissionRequests++ }
        override fun isManagerInstalled() = installed
        override fun bind(onConnected: (ShellHandle) -> Unit, onDisconnected: () -> Unit) {
            binds++
            this.onConnected = onConnected
            this.onDisconnected = onDisconnected
        }
        override fun setListeners(onBinderReceived: () -> Unit, onBinderDead: () -> Unit, onPermissionResult: () -> Unit) {
            binderReceived = onBinderReceived
            binderDead = onBinderDead
            permissionResult = onPermissionResult
        }
        fun connect(shell: ShellHandle) = onConnected!!.invoke(shell)
        fun ready() { installed = true; running = true; permission = true }
    }

    private class FakeDirty(vararg initial: String) : DirtyPackageStore {
        val set = initial.toMutableSet()
        override fun all() = set.toSet()
        override fun add(packageName: String) { set += packageName }
        override fun remove(packageName: String) { set -= packageName }
    }

    private val ig = "com.instagram.android"
    private val direct = Executor { it.run() }

    private fun saturation(client: FakeClient, dirty: FakeDirty = FakeDirty()) =
        ShizukuSaturation(client, dirty, "app.florsent", direct)

    @Test
    fun `reports each Shizuku state`() {
        val client = FakeClient()
        val s = saturation(client)
        assertEquals(ShizukuState.NotInstalled, s.state.value)

        client.installed = true; s.refresh()
        assertEquals(ShizukuState.NotRunning, s.state.value)

        client.running = true; client.preV11 = true; s.refresh()
        assertEquals(ShizukuState.TooOld, s.state.value)

        client.preV11 = false; s.refresh()
        assertEquals(ShizukuState.NoPermission, s.state.value)

        client.permission = true; client.permissionResult()
        assertEquals(ShizukuState.Ready, s.state.value)
    }

    @Test
    fun `does not bind the privileged service unless it is needed`() {
        val client = FakeClient().apply { ready() }
        val s = saturation(client)
        s.render(ig, 0.5f, enabled = false)
        assertEquals(0, client.binds)

        s.render(ig, 0.5f, enabled = true)
        assertEquals(1, client.binds)
        s.render(ig, 0.6f, enabled = true)
        assertEquals("binds once", 1, client.binds)
    }

    @Test
    fun `binds right away when an earlier run left something to restore`() {
        val client = FakeClient().apply { ready() }
        saturation(client, FakeDirty(ig))
        assertEquals(1, client.binds)
    }

    @Test
    fun `on connect it restores leftovers, then applies the latest guidance`() {
        val client = FakeClient().apply { ready() }
        val dirty = FakeDirty("com.old.app")
        val s = saturation(client, dirty)
        s.render(ig, 0.5f, enabled = true)
        val shell = FakeShell().apply { levels["com.old.app"] = 0 }

        client.connect(shell)

        assertEquals(mapOf(ig to 50), shell.levels)
        assertEquals(setOf(ig), dirty.set)
        assertFalse(s.pendingRestore.value)
    }

    @Test
    fun `restoreAll brings everything back to full colour`() {
        val client = FakeClient().apply { ready() }
        val s = saturation(client)
        s.render(ig, 1f, enabled = true)
        val shell = FakeShell()
        client.connect(shell)
        assertEquals(0, shell.levels[ig])

        s.restoreAll()
        assertTrue(shell.levels.isEmpty())
    }

    @Test
    fun `Shizuku dying mid-fade is reported and fixed when it comes back`() {
        val client = FakeClient().apply { ready() }
        val dirty = FakeDirty()
        val s = saturation(client, dirty)
        s.render(ig, 1f, enabled = true)
        val shell = FakeShell()
        client.connect(shell)

        shell.alive = false
        client.running = false
        client.binderDead()
        s.render(null, 0f, enabled = true) // left the app, but cannot restore
        assertEquals(ShizukuState.NotRunning, s.state.value)
        assertTrue(s.pendingRestore.value)
        assertEquals(setOf(ig), dirty.set)

        client.running = true
        client.binderReceived()
        assertEquals("rebinds to restore", 2, client.binds)
        val newShell = FakeShell().apply { levels[ig] = 0 }
        client.connect(newShell)
        assertTrue(newShell.levels.isEmpty())
        assertFalse(s.pendingRestore.value)
        assertTrue(dirty.set.isEmpty())
    }

    @Test
    fun `a failing shell degrades the layer until retry`() {
        val client = FakeClient().apply { ready() }
        val s = saturation(client)
        s.render(ig, 0.5f, enabled = true)
        val shell = FakeShell()
        client.connect(shell)
        shell.fail = true
        s.render(ig, 0.9f, enabled = true)
        assertTrue(s.degraded.value)

        shell.fail = false
        s.retry()
        assertFalse(s.degraded.value)
        assertTrue("restored during recovery", shell.levels.isEmpty())
    }

    @Test
    fun `permission is only requested from a running, supported Shizuku`() {
        val client = FakeClient()
        val s = saturation(client)
        s.requestPermission()
        assertEquals(0, client.permissionRequests)
        client.ready()
        client.permission = false
        s.requestPermission()
        assertEquals(1, client.permissionRequests)
    }
}
