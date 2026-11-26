package app.florsent.ui.grounding

import android.content.Context
import kotlin.random.Random

/**
 * Picks the next grounding prompt so the same few never come back soon. Plain random picks from a
 * few dozen sentences repeat surprisingly often (a tester felt there were only five or six), so the
 * most recent picks, about two thirds of the list, are left out until the rest have had a turn.
 */
object PromptPicker {

    /** How many of the latest picks are skipped, for a list of [size] prompts. */
    fun memory(size: Int): Int = (size * 2 / 3).coerceAtMost(size - 1).coerceAtLeast(0)

    /** A prompt index not among [recent]; [recent] is newest first and may hold stale indices. */
    fun next(size: Int, recent: List<Int>, random: Random = Random): Int {
        require(size > 0)
        val skip = recent.asSequence().filter { it in 0 until size }.distinct().take(memory(size)).toSet()
        val candidates = (0 until size).filter { it !in skip }
        return candidates[random.nextInt(candidates.size)]
    }

    /** [recent] with [picked] in front, trimmed to what [next] looks at. */
    fun remember(size: Int, recent: List<Int>, picked: Int): List<Int> =
        (listOf(picked) + recent.filter { it != picked && it in 0 until size }).take(memory(size))
}

/** Remembers recent picks across sessions, one list per prompt set. */
class PromptHistory(context: Context, private val key: String) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pick(size: Int): Int {
        val recent = load()
        val picked = PromptPicker.next(size, recent)
        save(PromptPicker.remember(size, recent, picked))
        return picked
    }

    private fun load(): List<Int> =
        prefs.getString(key, null).orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    private fun save(recent: List<Int>) {
        prefs.edit().putString(key, recent.joinToString(",")).apply()
    }

    private companion object {
        const val PREFS = "grounding_prompts"
    }
}
