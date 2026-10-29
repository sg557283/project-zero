# ZERO

ZERO is a native Android personal assistant built with Kotlin, Jetpack Compose, and Material 3. Its launcher branding is a black-and-white ZERO wordmark; Android notifications use a separate monochrome icon. Typed commands, command parsing, app launching, flashlight control, local time, battery status, call intents, command history, notes, reminders, battery alerts, and shortcuts run on-device. Optional online AI is disabled by default; core commands and identity do not depend on it. ZERO has no account, analytics, or ads.

Home uses an original, procedurally drawn Canvas sphere: a rotating, wave-deformed wireframe mesh with state-reactive color/glow and sparse particles. It is a projected 3D-style native animation, not a static image or a 3D-engine scene. Suggestions dispatch through the normal local command parser. The animation pauses off Home and can be disabled with Reduce motion in Settings. No uploaded orb image was available in the repository, so the mesh is an original implementation.

## Offline speech

ZERO uses Vosk Android for push-to-talk recognition. The Hindi small model `vosk-model-small-hi-0.22` is bundled in the APK and installed into app-private storage on first launch. The English US small model `vosk-model-small-en-us-0.15` is available from Speech Models; after its one-time download and installation, recognition runs locally without a network connection. The model catalog is versioned in `app/src/main/assets/speech-models.json`; downloads use fixed HTTPS URLs, byte lengths, and SHA-256 checksums. ZIP paths, extraction size, required Vosk files, and native Vosk loading are checked before a model can be marked installed. Active recognition stays on the previous model until the replacement validates.

| Model | Language | Version | Archive size | Source and license |
| --- | --- | --- | ---: | --- |
| `vosk-model-small-hi-0.22` | Hindi | 0.22 | 44,458,845 bytes | [Official archive](https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip), Apache-2.0 per the [Vosk model catalog](https://alphacephei.com/vosk/models) |
| `vosk-model-small-en-us-0.15` | English (US) | 0.15 | 41,205,931 bytes | [Official archive](https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip), Apache-2.0 per the Vosk model catalog |

The bundled Hindi archive adds 44,458,845 bytes (about 42.4 MiB) before APK packaging; the downloaded English model is not included in the APK. Model archive checksums are stored in the catalog. The Vosk Android runtime is `com.alphacephei:vosk-android:0.3.75`, Apache-2.0. Attribution and the full license text are included in app assets under `third-party/`.

Speech models are separate from Android Text-to-Speech voices. ZERO initializes Android TTS before speaking and selects only a local voice that reports no network requirement. If a local Hindi or English voice is missing, install it in Android Settings > Text-to-speech output. Settings contains English/Hindi test buttons and a stop control. Voice availability depends on the device's installed local TTS engine and voice data.

Voice Library has exactly two voice categories: Simple and Emotion-Based. Simple lists installed local Android TTS voices. Emotion-Based remains unavailable unless an installed offline engine or model reports genuine emotion controls; ZERO does not label basic pitch, speed, or pause changes as model-supported emotions. Vosk STT models are listed separately as recognition models and are never presented as TTS voices.

Wake listening is off by default. Settings always retains the default “Hey ZERO” phrase plus up to two optional custom phrases; each can be edited, enabled/disabled, or (custom only) removed. Empty/duplicate phrases are rejected and preferences persist locally. Enabled phrases are supplied to Vosk's grammar-constrained recognizer; this is not a dedicated neural low-power hotword model, and phrase detection quality depends on the selected model's vocabulary/language. The recognizer continues local microphone processing under the foreground service and persistent notification, so battery use is higher than a dedicated keyword detector.

Enabling wake listening requires `RECORD_AUDIO` and notification permission where applicable. A foreground microphone service and partial CPU wake lock keep capture independent from the Activity; Home shows the service heartbeat, errors, enabled phrases, and last detection. After a wake phrase, capture pauses, ZERO says “I am here” with an installed offline Android TTS voice, then starts a bounded command capture automatically. Silence or recognition failure produces a local retry prompt. Identity, time, battery, and supported flashlight actions can complete in the service; unsupported or sensitive commands are handed to ZERO for foreground review rather than silently launching activities. The service uses a persistent Stop action and resumes after normal app navigation. Android may still stop microphone access because of force-stop, permission revocation, privacy controls, device power management, or vendor restrictions. After reboot, Android requires the user to tap a resume notification/reopen ZERO before microphone access starts. Background operation is not guaranteed on every device.

Hindi and English recognition use their corresponding models; Hinglish is not promised as a separate automatic mode. ZERO does not save raw audio. Android TTS uses an installed voice that reports no network requirement; the wake service prefers the selected offline voice and falls back to another installed local voice.

## Commands and permissions

- Open YouTube, WhatsApp, Camera, and Android Settings when installed.
- Use Home's animated orb and actionable suggestions for YouTube, planner, battery, camera, time, WhatsApp, and notes. Settings and History remain accessible from the top bar.
- Turn the flashlight on/off on supported hardware after runtime camera permission.
- Tell local time and battery status, show help, and maintain up to 50 local command-history entries.
- Save notes with `note <text>`, open the planner with `reminders`, `tasks`, or `planner`, and create a reminder draft with `remind me to <title>`. Spoken text is shown for review; date/time/repeat are set in the planner form.
- Run an enabled saved shortcut by its exact name or `run <shortcut name>`. Shortcut actions are limited to native app/Settings intents, planner/notes views, battery status, flashlight control, and local speech.
- Request a call to a parsed phone number. ZERO displays and confirms the target first, requests runtime `CALL_PHONE` only after confirmation, and opens the dialer for review if permission or direct calling is unavailable.
- Keep typed commands available offline regardless of speech model or voice availability.

Reminders are stored locally and scheduled with WorkManager. Scheduling is inexact; Android can delay delivery. Reminder and battery notifications use a minimal ZERO-branded local style. Notification permission is requested when needed. WorkManager reschedules across reboot, and ZERO recalculates reminders after time/time-zone changes. No exact-alarm permission is requested. Battery alerts support a 1-100% threshold and alert once per above-to-at/below crossing; they re-arm after recovery. Background checks are periodic (15 minutes minimum) and can be delayed or stopped by Android force-stop/power management. Charging-start and full alerts are optional.

Runtime permissions are `RECORD_AUDIO` for push-to-talk and explicitly enabled foreground wake listening, `CAMERA` for flashlight control, `CALL_PHONE` only for confirmed direct calling, `POST_NOTIFICATIONS` for alerts and foreground controls, `RECEIVE_BOOT_COMPLETED` for reminder rescheduling, and `WAKE_LOCK` only while the opt-in listening service is active. Internet access downloads the fixed speech-model archives; when optional online AI is enabled, it also sends a text question only after a per-request confirmation. `ACCESS_NETWORK_STATE` gates that option on validated connectivity. Local commands, identity, installed-model recognition, TTS, notes, reminders, and shortcuts remain offline. The provider endpoint/model and API key are configured in Settings; the API key is AES-GCM encrypted with an Android Keystore key. Secrets embedded in a distributed app cannot be guaranteed safe from extraction. ZERO sends no microphone audio and does not silently upload conversation history.

## Build

Requirements: JDK 17 or 21, Android SDK platform 36, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, and the included Gradle 8.13 wrapper.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew packageDebugZip
```

The normal debug APK remains at `app/build/outputs/apk/debug/app-debug.apk`. `packageDebugZip` stages `app/build/downloadable/Zero-Assistant.apk` and creates a validated `app/build/downloadable/Zero-Assistant.zip` for local downloads. GitHub Actions uploads the staged APK directly as the `Zero-Assistant` artifact, so downloading it requires only the standard single artifact extraction and does not create a ZIP containing another ZIP. Install the APK with `adb install -r Zero-Assistant.apk`.

Unit tests cover command parsing/history and offline identity, TTS initialization/language/speaking states, the exact two Voice Library categories, wake phrase limits/validation/migration/matching/heartbeat freshness, online route gating and HTTPS endpoint validation, actionable Home suggestions, catalog validation, model transfers, bundled installation, checksum/path-traversal rejection, cancellation, Vosk validation gates, active-model persistence, planner recurrence/persistence, battery crossing/re-arming, and note/shortcut action validation. They do not replace physical-device verification of background microphone capture, Android TTS voices, speech accuracy, Android Keystore behavior, HTTP provider interoperability, WorkManager delivery, or battery broadcasts.

## Privacy and terms

Typed commands, command execution, history, notes, reminders, shortcuts, battery evaluation, wake matching, and model inference are processed on-device. Network use downloads the fixed HTTPS speech models. If a user enables and configures online AI, ZERO sends only an unsupported text query to the configured HTTPS provider after a confirmation dialog; this provider may process/store that text under its own terms. ZERO never sends microphone audio or local conversation history. The feature can be disabled or left unconfigured, and local identity/commands remain offline. Local history, notes, reminders, shortcuts, settings, credentials, and installed models use app-private storage. Audio is not retained. Phone calls are external actions: ZERO confirms the intended number before requesting permission or opening the dialer. Background microphone operation and notification delivery depend on Android permissions, power management, device policy, and whether ZERO has been force-stopped.