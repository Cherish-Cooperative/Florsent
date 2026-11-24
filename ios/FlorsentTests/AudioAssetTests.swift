import AVFoundation
import XCTest
import FlorsentKit
@testable import Florsent

/// The converted .caf files must decode to exactly the Android loop lengths (a single extra or
/// missing frame would be an audible click at every seam).
final class AudioAssetTests: XCTestCase {
    func testLoopsDecodeToExactLength() throws {
        for (name, frames) in [(SoundTuning.File.breathingAmbient, 48 * 48_000), (SoundTuning.File.fadeAmbient, 42 * 48_000)] {
            let buffer = try BreathingSoundscape.loadBuffer(named: name)
            XCTAssertEqual(Int(buffer.frameLength), frames, name)
            XCTAssertEqual(buffer.format.sampleRate, 48_000, name)
            XCTAssertEqual(buffer.format.channelCount, 2, name)
            // The seam is continuous: the jump from the last to the first frame is no bigger than a
            // typical step inside the file.
            let data = try XCTUnwrap(buffer.floatChannelData)
            let n = Int(buffer.frameLength)
            var maxStep: Float = 0
            for i in stride(from: 1, to: n, by: 97) { maxStep = max(maxStep, abs(data[0][i] - data[0][i - 1])) }
            XCTAssertLessThanOrEqual(abs(data[0][0] - data[0][n - 1]), maxStep, "\(name) seam")
        }
    }

    func testCueAndNotificationSound() throws {
        let cue = try BreathingSoundscape.loadBuffer(named: SoundTuning.File.cue)
        XCTAssertEqual(Double(cue.frameLength) / cue.format.sampleRate, 1.6, accuracy: 0.01)

        let url = try XCTUnwrap(Bundle.main.url(forResource: SoundTuning.File.rainNotification, withExtension: "caf"))
        let file = try AVAudioFile(forReading: url)
        let seconds = Double(file.length) / file.fileFormat.sampleRate
        XCTAssertEqual(seconds, 12, accuracy: 0.01)
        XCTAssertLessThan(seconds, 30, "notification sounds must be shorter than 30 s")
        XCTAssertEqual(file.fileFormat.streamDescription.pointee.mFormatID, kAudioFormatAppleIMA4)
        // Fades in and out: the first and last 100 ms are much quieter than the middle.
        let buffer = try BreathingSoundscape.loadBuffer(named: SoundTuning.File.rainNotification)
        let data = try XCTUnwrap(buffer.floatChannelData)[0]
        func rms(_ from: Int, _ count: Int) -> Float {
            var sum: Float = 0
            for i in from..<(from + count) { sum += data[i] * data[i] }
            return (sum / Float(count)).squareRoot()
        }
        let tenth = Int(buffer.format.sampleRate / 10), n = Int(buffer.frameLength)
        let middle = rms(n / 2, tenth)
        XCTAssertLessThan(rms(0, tenth), middle * 0.1)
        XCTAssertLessThan(rms(n - tenth, tenth), middle * 0.1)
    }
}
