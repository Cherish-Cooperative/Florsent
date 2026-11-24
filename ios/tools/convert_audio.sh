#!/bin/sh
# Converts the Android audio masters (app/src/main/res/raw, read-only) into iOS .caf files.
#
#  breathing_ambient.caf   singing bowl, 48.000 s gapless loop   AAC 160 kbps in CAF
#  fade_ambient.caf        distant rain, 42.000 s gapless loop   AAC 160 kbps in CAF
#  cue_soft.caf            phase chime, 1.6 s                    16-bit LPCM in CAF
#  fade_rain_notification  12 s of the rain, fades in 3 s / out 4 s at the fade volume (0.6),
#                          IMA4 in CAF (a notification sound must be <30 s and LPCM/IMA4/µ-law/a-law)
#
# The Opus files are decoded with ffmpeg, which honours Opus pre-skip and end trimming, so the PCM is
# exactly 48 × 48000 / 42 × 48000 frames. CAF keeps AAC priming/remainder in its packet table, so
# AVAudioFile hands back the exact frame count again and a looped AVAudioPCMBuffer has no gap
# (checked by AudioAssetTests). Needs ffmpeg and afconvert (macOS).
set -eu
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
RAW="$ROOT/app/src/main/res/raw"
OUT="$ROOT/ios/Florsent/Resources/Audio"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"

for name in breathing_ambient fade_ambient; do
  ffmpeg -v error -y -i "$RAW/$name.ogg" -c:a pcm_s16le "$WORK/$name.wav"
  afconvert -f caff -d aac -b 160000 -q 127 "$WORK/$name.wav" "$OUT/$name.caf"
done
afconvert -f caff -d LEI16 "$RAW/cue_soft.wav" "$OUT/cue_soft.caf"

ffmpeg -v error -y -ss 6 -t 12 -i "$WORK/fade_ambient.wav" \
  -af "volume=0.6,afade=t=in:st=0:d=3:curve=qsin,afade=t=out:st=8:d=4:curve=qsin,aresample=44100" \
  -c:a pcm_s16le "$WORK/rain12.wav"
afconvert -f caff -d ima4 "$WORK/rain12.wav" "$OUT/fade_rain_notification.caf"

for f in "$OUT"/*.caf; do
  printf '%s: ' "$(basename "$f")"; afinfo "$f" | grep -E 'valid frames|estimated duration' | tr '\n' ' '; echo
done
