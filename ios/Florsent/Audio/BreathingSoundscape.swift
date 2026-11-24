import AVFoundation
import FlorsentKit

/// Sound for a breathing session: the singing-bowl bed and a soft chime at phase changes.
///
/// - Gapless loop: the 48.000 s bowl is decoded once into an `AVAudioPCMBuffer` and scheduled with
///   `.loops`, so the player node wraps sample-accurately with no decoder re-priming at the seam
///   (the CAF carries the AAC priming info, so the buffer is exactly 2,304,000 frames).
/// - The chime is a small LPCM buffer through `AVAudioUnitVarispeed`, pitched per phase like
///   Android's `playbackRate` (rising for inhale, settling for exhale). Restarting it never touches
///   the bowl's node, so the bed never stutters.
/// - Audio session: with the bowl on, `.playback` without mixing (others pause and resume after, like
///   Android's transient focus); chime only, `.playback` + `.mixWithOthers` so music keeps going.
/// - Interruptions (calls, Siri) and route changes restart the engine and the loop if still wanted.
@MainActor
final class BreathingSoundscape {
    private let engine = AVAudioEngine()
    private let ambient = AVAudioPlayerNode()
    private let cue = AVAudioPlayerNode()
    private let varispeed = AVAudioUnitVarispeed()
    private var ambientBuffer: AVAudioPCMBuffer?
    private var cueBuffer: AVAudioPCMBuffer?
    private var wired = false
    private var ambientWanted = false
    private var ambientScheduled = false
    /// 0...1 of `SoundTuning.ambientVolume`.
    private var level: Float = 0
    private var fadeTask: Task<Void, Never>?
    private var observers: [NSObjectProtocol] = []

    /// Decodes the cue now, so the first "inhale" chime is not late.
    func warmUp() { wire() }

    func startAmbient() {
        ambientWanted = true
        wire()
        guard let buffer = ambientBuffer, activate(exclusive: true) else { return }
        if !ambientScheduled {
            ambient.scheduleBuffer(buffer, at: nil, options: [.loops])
            ambientScheduled = true
        }
        if !ambient.isPlaying { ambient.play() }
        fade(to: 1, seconds: SoundTuning.ambientFadeInSeconds)
    }

    func stopAmbient() {
        ambientWanted = false
        guard wired else { return }
        fade(to: 0, seconds: SoundTuning.ambientFadeOutSeconds * Double(level)) { [weak self] in
            guard let self, !self.ambientWanted else { return }
            self.ambient.stop()
            self.ambientScheduled = false
            self.deactivate()
        }
    }

    /// App went to the background mid-session: hold the loop where it is.
    func pause() {
        fadeTask?.cancel()
        setLevel(0)
        if ambient.isPlaying { ambient.pause() }
        if engine.isRunning { engine.pause() }
        deactivate()
    }

    /// Back in front: resume a paused loop with the usual fade-in.
    func resume() {
        if ambientWanted { startAmbient() }
    }

    func playCue(_ kind: BreathPhaseKind) {
        wire()
        guard let buffer = cueBuffer, activate(exclusive: ambientWanted) else { return }
        varispeed.rate = SoundTuning.cueRate(kind)
        cue.stop() // restart if still ringing
        cue.scheduleBuffer(buffer, at: nil)
        cue.volume = SoundTuning.cueVolume
        cue.play()
    }

    func shutdown() {
        ambientWanted = false
        fadeTask?.cancel()
        ambient.stop()
        cue.stop()
        ambientScheduled = false
        engine.stop()
        deactivate()
    }

    // MARK: - Engine

    nonisolated static func loadBuffer(named name: String, in bundle: Bundle = .main) throws -> AVAudioPCMBuffer {
        guard let url = bundle.url(forResource: name, withExtension: "caf") else {
            throw CocoaError(.fileNoSuchFile, userInfo: [NSFilePathErrorKey: "\(name).caf"])
        }
        let file = try AVAudioFile(forReading: url)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(file.length)) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        try file.read(into: buffer)
        return buffer
    }

    private func wire() {
        guard !wired else { return }
        ambientBuffer = try? Self.loadBuffer(named: SoundTuning.File.breathingAmbient)
        cueBuffer = try? Self.loadBuffer(named: SoundTuning.File.cue)
        engine.attach(ambient)
        engine.attach(cue)
        engine.attach(varispeed)
        engine.connect(ambient, to: engine.mainMixerNode, format: ambientBuffer?.format)
        engine.connect(cue, to: varispeed, format: cueBuffer?.format)
        engine.connect(varispeed, to: engine.mainMixerNode, format: cueBuffer?.format)
        ambient.volume = 0
        engine.prepare()
        observeSession()
        wired = true
    }

    private func activate(exclusive: Bool) -> Bool {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: exclusive ? [] : [.mixWithOthers])
            try session.setActive(true)
            if !engine.isRunning { try engine.start() }
            return true
        } catch {
            return false
        }
    }

    private func deactivate() {
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func setLevel(_ value: Float) {
        level = value
        ambient.volume = SoundTuning.ambientVolume * value
    }

    /// Wall-clock volume ramp (about 30 steps a second), independent of animation settings.
    private func fade(to target: Float, seconds: Double, then done: (@MainActor () -> Void)? = nil) {
        fadeTask?.cancel()
        let from = level
        guard seconds > 0.01, from != target else {
            setLevel(target)
            done?()
            return
        }
        fadeTask = Task { [weak self] in
            let start = ContinuousClock.now
            while !Task.isCancelled {
                let t = min((ContinuousClock.now - start) / .seconds(seconds), 1)
                self?.setLevel(from + (target - from) * Float(t))
                if t >= 1 { break }
                try? await Task.sleep(for: .milliseconds(33))
            }
            if !Task.isCancelled { done?() }
        }
    }

    private func observeSession() {
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { note in
            let type = (note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap(AVAudioSession.InterruptionType.init)
            let options = AVAudioSession.InterruptionOptions(rawValue: note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0)
            MainActor.assumeIsolated { [weak self] in
                guard let self else { return }
                switch type {
                case .began: self.ambientScheduled = false; self.setLevel(0)
                case .ended where options.contains(.shouldResume): self.resume()
                default: break
                }
            }
        })
        observers.append(center.addObserver(forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main) { _ in
            MainActor.assumeIsolated { [weak self] in
                // Headphones in or out: the engine stopped and dropped its schedule.
                guard let self else { return }
                self.ambientScheduled = false
                self.setLevel(0)
                self.resume()
            }
        })
    }
}

/// Puts the 12-second distant-rain notification sound where notifications scheduled by the Screen
/// Time extensions can find it: `Library/Sounds` in the App Group container.
enum NotificationSound {
    static let fileName = "\(SoundTuning.File.rainNotification).caf"

    static func install() {
        guard let container = AppGroup.containerURL,
              let source = Bundle.main.url(forResource: SoundTuning.File.rainNotification, withExtension: "caf") else { return }
        let sounds = container.appendingPathComponent("Library/Sounds", isDirectory: true)
        let target = sounds.appendingPathComponent(fileName)
        let fm = FileManager.default
        try? fm.createDirectory(at: sounds, withIntermediateDirectories: true)
        if let a = try? fm.attributesOfItem(atPath: target.path)[.size] as? Int,
           let b = try? fm.attributesOfItem(atPath: source.path)[.size] as? Int, a == b { return }
        try? fm.removeItem(at: target)
        try? fm.copyItem(at: source, to: target)
    }
}
