package app.florsent.service.effect

/**
 * One frame of guidance intent handed to the visual layer.
 *
 * @property level eased guidance strength, 0 (untouched) to 1 (strongest).
 * @property targetPackage the app being guided, or null for the preview. Per-app back ends use it.
 */
data class VisualEffect(val level: Float, val targetPackage: String?) {
    companion object {
        val None = VisualEffect(level = 0f, targetPackage = null)
    }
}

/**
 * Output side of the gradual guidance. Implementations must be idempotent (the same effect may
 * arrive repeatedly) and smooth transitions themselves; [render] is called roughly once a second.
 *
 * Planned implementations beyond the base [DimOverlayController]:
 * - Android 15+ true-grayscale finish via an AutomaticZenRule with
 *   `ZenDeviceEffects.Builder.setShouldDisplayGrayscale(true)`, switched on when level reaches 1.
 * - P4 opt-in per-app desaturation through Shizuku
 *   (`cmd color_display set-layer-saturation <level> <caller> <targetPackage>`).
 */
interface VisualEffectController {
    fun render(effect: VisualEffect)

    /** Removes every visual trace immediately. Called when the service shuts down. */
    fun release()
}

/** Fans one effect out to several back ends so they can be layered. */
class CompositeVisualEffectController(
    private val controllers: List<VisualEffectController>,
) : VisualEffectController {
    override fun render(effect: VisualEffect) = controllers.forEach { it.render(effect) }
    override fun release() = controllers.forEach { it.release() }
}
