# ZERO

ZERO is a native Android personal assistant built with Kotlin, Jetpack Compose, and Material 3. Typed commands, command parsing, app launching, flashlight control, local time, battery status, call intents, command history, notes, reminders, battery alerts, and shortcuts run on-device. Optional online AI is disabled by default; core commands and identity do not depend on it.

## Speech

The app bundles Sherpa-ONNX for English keyword spotting. Its three built-in wake phrases are "Hey ZERO", "OK ZERO", and "Hey Shree". Wake listening is opt-in and uses a foreground microphone service. The KWS model does not recognize Hindi or custom wake phrases.

Push-to-talk and command transcription use Android's on-device `SpeechRecognizer` on Android 12+ when the device reports that an on-device recognizer is installed. ZERO does not fall back to network speech recognition. Typed commands remain available on all devices. Recognition coverage and language switching depend on Android's installed recognizer; this build does not bundle Hindi/Hinglish STT.

The Voice Library lists downloadable Humanoid Female and Male Piper VITS voices, plus an optional Kokoro v1.1 multi-speaker pack. Voice archives require internet for the initial download, then synthesize on-device with Sherpa-ONNX without a cloud speech API or API key. Archives are size- and SHA-256-verified and installed under `filesDir/tts_models/`; none of the voice models are bundled in the APK. Kokoro v1.1 supports English and Chinese and has 103 speakers, but only its three documented English voices (Maple, Sol, and Vale) are exposed as English presets here; it does not support Hindi. Hindi/Hinglish voices are provided by the dedicated Piper Hindi models. Android system TTS voices are not shown in the Voice Library; the Android engine remains a spoken-output fallback when a neural voice is not selected or does not match the output language.

The startup migration removes the old Vosk speech-model directory, Vosk cache files, and previous Kokoro model files. It does not scan or delete user documents or unrelated model directories. ZERO does not retain raw microphone audio.

## Reminders, Vault, and privacy

Reminders use exact Android alarms when exact-alarm access is allowed. Otherwise, WorkManager provides a best-effort fallback; the Planner links to Android's exact-alarm access screen. High-importance reminder notifications are rescheduled after boot, clock, and time-zone changes. Android notification settings and power management can still suppress or delay delivery.

ZERO Vault stores encrypted files in app-private storage. Export uses Android's Storage Access Framework. Permanent deletion overwrites the encrypted app-private file before deleting it and attempts to delete an imported source only if a persisted URI grant permits that operation. Flash storage cannot guarantee forensic overwriting, and Android/provider behavior can prevent source deletion.

Typed commands, history, notes, reminders, shortcuts, wake matching, and model inference are processed locally. Model downloads use HTTPS only when requested. If online AI is enabled, ZERO sends only a confirmed text query to the configured provider; it never sends microphone audio or local conversation history. External calls require confirmation before requesting phone permission or opening the dialer.

## Build

Requirements: JDK 17 or 21, Android SDK platform 36, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, and the included Gradle 8.13 wrapper.

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew packageDebugZip
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. `packageDebugZip` validates `app/build/downloadable/Zero-Assistant.zip`.
