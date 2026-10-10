# Research and architecture — 2026-10-09

## Alternatives and reuse

- [TranSlander](https://github.com/hatsch/TranSlander): local voice-message and keyboard transcription, OPUS/AAC/OGG/M4A/MP3/WAV. Useful proof that sharing is a viable entry point. We implement independently; no copied code or bundled model.
- [Transcribro](https://github.com/soupslurpr/Transcribro): whisper.cpp plus Silero VAD, keyboard and recognition service. Its documented English-only scope does not satisfy Portuguese by itself.
- [mytts](https://github.com/brenorb/mytts): reuse the interaction principle: immediate share intake, reading as primary surface, secondary settings, newest share wins. No reuse of its 36-MiB release dependencies.
- [Android recognition](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE): API 33 introduces a PCM file descriptor, sample rate, encoding, channels and segmented sessions. Closing the producer ends the session. Providers can ignore extras; capability checking is required but cannot prove provider compliance. Device-only validation remains necessary. SwiftKey/Gboard are input methods, not a public file-transcription API.
- [Supported formats](https://developer.android.com/media/platform/supported-formats): platform MediaExtractor/MediaCodec handles common containers/codecs without ffmpeg in the APK. Opus in Ogg supported since Android 5, decoding since Android 5. Container and actual codec matter more than extension or messenger name. No universal promise for every social-media format.

## Streaming versus latency

A shared file is already complete; it need not be played to transcribe. Decode without real-time pacing. Native PCM pipe is bounded by OS backpressure and can deliver provisional and segmented results before EOF, but a provider may internally pace processing. Server mode submits sequential 15-second PCM windows, renders each result immediately, retains only one window and never uploads the original compressed file. This is progressive chunk inference, not an assertion that batch Parakeet becomes a streaming neural model. Chunk boundaries can cut words and sacrifice cross-window context; timestamps are audio ranges, not word alignments.

## Benchmark implications

Local sibling fast-transcript canonical ASR v2 (2026-09-30, M5 Max) compares ONNX int8 Parakeet v3, Redux, Ultra, Phonon-2. Model caches: 639.6 / 170.7 / 1198.3 / 325.4 MiB. PT lecture WER: 9.11 / 7.89 / 4.05 / 16.19% CPU. These are desktop measurements, not Android estimates. Keep weights off the phone; optional server adapter runs the existing fscript CLI with its selected backend, keeping the mobile interface unchanged.

## Visual research

[Pixel Recorder](https://support.google.com/pixelphone/answer/16267698) and image search for “Google Recorder transcript screen app” show a dedicated transcript surface, sparse controls and clear recording status. [Material typography](https://m3.material.io/styles/typography/applying-type) guides hierarchy. Escriba uses system typography at 20sp, generous line spacing, selectable text, numbered time ranges, a maximum reading width on tablets, light/dark system theme and accessible text buttons. Settings stay behind the overflow control. Avoid a waveform occupying the reading area. Copy/share only committed text; provisional native text is visually marked.

## Choice

Java + platform Views, minSdk 33, no app runtime dependencies, no model weights, no WebView or microphone recording code. Native offline recognizer is default. Missing native support gives an actionable message; remote mode is explicit and configured once. Never silently send audio to a server. Distribution requires HTTPS; debug tests allow HTTP on loopback only. Self-hosted computers use a TLS reverse proxy. Lifecycle cancellation closes codec/pipe/socket and generation IDs prevent stale results replacing a newer share.
