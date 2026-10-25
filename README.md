# ZERO

ZERO is a native Android personal assistant built with Kotlin and Jetpack Compose. Typed commands, command parsing, app launching, flashlight controls, the system clock, and command history run locally. ZERO has no account, server, API key, analytics, ads, or internet permission.

## Offline features

- Open YouTube, WhatsApp, Camera, and Android Settings when installed. Opening YouTube or WhatsApp does not make their own content available offline.
- Turn the flashlight on or off on supported devices after camera permission is granted.
- Tell the current local time, answer help requests, and report unsupported commands.
- Parse English, common Hinglish, and supported Hindi-script commands.
- Keep up to 50 recent commands in app-private local storage. Clear history in the History screen.
- Use push-to-talk with Android's explicit on-device speech recognizer on Android 12/API 31+ when the device provides it. English and Hindi can be selected in Settings. Voice recognition also requires the matching offline model to be installed by the device speech service; ZERO does not download one or fall back to a network recognizer.
- Speak responses with an installed Android text-to-speech voice only when that voice reports it does not require a network connection.

Voice input and output are therefore offline-capable, but their availability depends on the phone, Android speech/TTS providers, installed language data, and device locale. ZERO does not record raw audio. Microphone use starts only after the user taps Mic and is stopped when ZERO leaves the foreground. “Hey ZERO” is not implemented; there is no always-on microphone service, wake-word engine, or wake-word model.

## Speech engine and model evaluation

| Need | Decision | Offline behavior, model, and licensing |
| --- | --- | --- |
| STT | Android `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+) with a runtime on-device availability check; no third-party STT dependency | Uses the device's on-device recognizer, not the general recognizer API. ZERO bundles no model and adds no APK model bytes. The Android/provider-managed English or Hindi language model must already be installed; provider-specific model name, license, download size, and RAM use are not exposed by Android's app API and vary by device. Language-unavailable errors direct users to Android Settings. [Android API reference](https://developer.android.com/reference/android/speech/SpeechRecognizer#createOnDeviceSpeechRecognizer(android.content.Context)) |
| TTS | Android `TextToSpeech`; ZERO selects an installed local English or Hindi voice | No app-bundled voice or model. A voice with `isNetworkConnectionRequired == false` is required. Install voice data through Android Settings > Text-to-speech output. The voice engine and voice-data license/size are provider-specific. [Android API reference](https://developer.android.com/reference/android/speech/tts/TextToSpeech) |
| Wake word | Not integrated; push-to-talk instead | `openWakeWord` has Apache-2.0 library code, but its documented integration is Python/ONNX/TFLite-oriented rather than a ready-to-use Android SDK; a suitable “Hey ZERO” model and its separate license would also need verification. No model is bundled. Always-listening would additionally require a microphone foreground service and version-specific permissions. [Project](https://github.com/dscripka/openWakeWord) |

Alternatives considered:

- [Vosk](https://github.com/alphacep/vosk-api) provides offline Android-compatible STT. Its small English US model is about 40 MB and its Hindi model about 42 MB; the model list labels both Apache-2.0 and describes small models as suitable for mobile (about 300 MB runtime memory). It was not integrated to avoid adding engine/native-library size plus roughly 82 MB of model assets or introducing a separate model download/setup flow. [Models and licenses](https://alphacephei.com/vosk/models)
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) supports Android and is MIT-licensed. Its multilingual tiny model is 75 MiB (about 273 MB runtime memory); base is 142 MiB (about 388 MB). It is capable but requires NDK/native integration and a large model file for this small command set, so it was not included. [Models and sizes](https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md)

The only speech models used by ZERO are those managed by the phone's installed Android providers. ZERO downloads no model at runtime and includes no STT, TTS, or wake-word model files.

## Permissions

- `RECORD_AUDIO`: requested at runtime only after the user taps Mic, for push-to-talk.
- `CAMERA`: requested at runtime only for flashlight control, which uses `CameraManager.setTorchMode`; camera hardware is optional.

No internet, location, contacts, SMS, accessibility, device-admin, foreground-service, or notification permission is needed. A foreground service is not used because ZERO has no background or wake-word listening.

## Build and install

Requirements: JDK 17 or 21, Android SDK platform 36, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, and the included Gradle 8.13 wrapper. Use JDK 17 or 21; the Kotlin compiler in this project does not accept the current JDK 25 version string.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or transfer it to the phone and open it (allow installation from that source if Android asks).

On the phone, grant microphone access when first using Mic. In ZERO Settings, select English or Hindi. If recognition reports that a language is unavailable, use Android Settings search for “offline speech recognition” or “speech services” and install that language's offline speech data. To enable spoken responses, use Android Settings search for “Text-to-speech output,” install an English or Hindi voice for the preferred engine, and return to ZERO. Both speech features can be unavailable on devices without a compatible local provider/model; typed commands remain fully offline.

## Privacy

Command history is held in app-private preferences and can be cleared in the app. ZERO does not save raw audio or send commands to a ZERO server. The explicit on-device Android recognizer is used only for a user-initiated recording; speech and TTS providers and their separately managed language assets are external system components, so their behavior and data licensing are controlled by the device/provider.