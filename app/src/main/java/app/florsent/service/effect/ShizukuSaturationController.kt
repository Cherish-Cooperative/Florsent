package app.florsent.service.effect

import app.florsent.service.shizuku.ShizukuSaturation

/**
 * Third visual layer: per-app desaturation through Shizuku, layered on top of the dim overlay and
 * the Zen grayscale finish. It never blocks or replaces them: if Shizuku is missing, stopped or
 * failing, this controller does nothing and the other layers carry the guidance.
 */
class ShizukuSaturationController(
    private val saturation: ShizukuSaturation,
    private val isEnabled: () -> Boolean,
) : VisualEffectController {

    override fun render(effect: VisualEffect) {
        saturation.render(effect.targetPackage, effect.level, isEnabled())
    }

    override fun release() {
        saturation.restoreAll()
    }
}
