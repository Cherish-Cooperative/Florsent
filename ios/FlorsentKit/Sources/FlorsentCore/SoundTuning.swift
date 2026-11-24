/// Playback levels, shared with Android (`BreathingSoundscape`, `FadeSoundCurve`) and pinned by the
/// golden file. Masters: singing bowl -13.0 LUFS (true peak -2.6 dBFS), cue peak -15.5 dBFS, rain
/// -13.6 LUFS. At 0.75 the bowl peaks around -5 dBFS, so it cannot clip.
public enum SoundTuning {
    public static let ambientVolume: Float = 0.75
    public static let cueVolume: Float = 0.42
    public static let ambientFadeInSeconds = 2.5
    public static let ambientFadeOutSeconds = 1.2
    public static let fadeRainTargetVolume: Float = 0.6

    /// One clip, pitched per phase: rising for inhale, settling for exhale.
    public static func cueRate(_ kind: BreathPhaseKind) -> Float {
        switch kind {
        case .inhale: 1.0
        case .holdIn: 0.89
        case .exhale: 0.75
        case .holdOut: 0.84
        }
    }

    /// Rain fade-in curve: quadratic, so the first seconds are barely audible.
    public static func fadeRainVolume(progress: Float) -> Float {
        let p = min(max(progress, 0), 1)
        return fadeRainTargetVolume * p * p
    }

    /// Bundled audio, converted from the Android masters by ios/tools/convert_audio.sh.
    public enum File {
        public static let breathingAmbient = "breathing_ambient"   // 48.000 s gapless loop, AAC in CAF
        public static let fadeAmbient = "fade_ambient"             // 42.000 s gapless loop, AAC in CAF
        public static let cue = "cue_soft"                         // 1.6 s chime, LPCM in CAF
        public static let rainNotification = "fade_rain_notification" // 12 s, fades in and out, IMA4 in CAF
    }
}
