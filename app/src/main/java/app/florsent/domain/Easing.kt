package app.florsent.domain

object Easing {
    /** Hermite smoothstep: imperceptible start, steady middle, soft landing. */
    fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
