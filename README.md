# ZERO

ZERO is a native Android personal assistant built with Kotlin, Jetpack Compose, and Material 3. Its launcher branding is a black-and-white ZERO wordmark; Android notifications use a separate monochrome icon. Typed commands, command parsing, app launching, flashlight control, local time, battery status, call intents, command history, notes, reminders, battery alerts, and shortcuts run on-device. ZERO has no account, cloud AI, analytics, or ads.

## Offline speech

ZERO uses Vosk Android for push-to-talk recognition. The Hindi small model `vosk-model-small-hi-0.22` is bundled in the APK and installed into app-private storage on first launch. The English US small model `vosk-model-small-en-us-0.15` is available from Speech Models; after its one-time download and installation, recognition runs locally without a network connection. The model catalog is versioned in `app/src/main/assets/speech-models.json`; downloads use fixed HTTPS URLs, byte lengths, and SHA-256 checksums. ZIP paths, extraction size, required Vosk files, and native Vosk loading are checked before a model can be marked installed. Active recognition stays on the previous model until the replacement validates.

| Model | Language | Version | Archive size | Source and license |
| --- | --- | --- | ---: | --- |
| `vosk-model-small-hi-0.22` | Hindi | 0.22 | 44,458,845 bytes | [Official archive](https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip), Apache-2.0 per the [Vosk model catalog](https://alphacephei.com/vosk/models) |
| `vosk-model-small-en-us-0.15` | English (US) | 0.15 | 41,205,931 bytes | [Official archive](https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip), Apache-2.0 per the Vosk model catalog |

The bundled Hindi archive adds 44,458,845 bytes (about 42.4 MiB) before APK packaging; the downloaded English model is not included in the APK. Model archive checksums are stored in the catalog. The Vosk Android runtime is `com.alphacephei:vosk-android:0.3.75`, Apache-2.0. Attribution and the full license text are included in app assets under `third-party/`.

Speech models are separate from Android Text-to-Speech voices. ZERO initializes Android TTS before speaking and selects only a local voice that reports no network requirement. If a local Hindi or English voice is missing, install it in Android Settings > Text-to-speech output. Settings contains English/Hindi test buttons and a stop control. Voice availability depends on the device's installed local TTS engine and voice data.

Wake listening is off by default. In Settings, users can enable foreground-only wake listening and edit the phrase; the default is “Hey ZERO.” Enabling it requests `RECORD_AUDIO`. While ZERO remains visible, the selected offline Vosk model listens for the phrase and can handle a command that follows it. Capture stops when ZERO leaves the foreground or wake listening is turned off. Push-to-talk remains available, and ZERO does not save raw audio. Hindi and English recognition use the selected model. Hinglish is not advertised as a supported automatic recognition mode.

## Commands and permissions

- Open YouTube, WhatsApp, Camera, and Android Settings when installed.
- Use the home dashboard for local time/date, battery level, the next reminder, microphone status, and quick actions for apps, flashlight, battery, Wi-Fi, Bluetooth, and the dialer. Wi-Fi and Bluetooth actions open Android Settings for user-controlled changes; calls still require confirmation or dialer review.
- Turn the flashlight on/off on supported hardware after runtime camera permission.
- Tell local time and battery status, show help, and maintain up to 50 local command-history entries.
- Save notes with `note <text>`, open the planner with `reminders`, `tasks`, or `planner`, and create a reminder draft with `remind me to <title>`. Spoken text is shown for review; date/time/repeat are set in the planner form.
- Run an enabled saved shortcut by its exact name or `run <shortcut name>`. Shortcut actions are limited to native app/Settings intents, planner/notes views, battery status, flashlight control, and local speech.
- Request a call to a parsed phone number. ZERO displays and confirms the target first, requests runtime `CALL_PHONE` only after confirmation, and opens the dialer for review if permission or direct calling is unavailable.
- Keep typed commands available offline regardless of speech model or voice availability.

Reminders are stored locally and scheduled with WorkManager. Scheduling is inexact; Android can delay delivery. Reminder and battery notifications use a minimal ZERO-branded local style. Notification permission is requested when needed. WorkManager reschedules across reboot, and ZERO recalculates reminders after time/time-zone changes. No exact-alarm permission is requested. Battery alerts support a 1-100% threshold and alert once per above-to-at/below crossing; they re-arm after recovery. Background checks are periodic (15 minutes minimum) and can be delayed or stopped by Android force-stop/power management. Charging-start and full alerts are optional.

Runtime permissions are `RECORD_AUDIO` for push-to-talk and explicitly enabled foreground wake listening, `CAMERA` for flashlight control, `CALL_PHONE` only for confirmed direct calling, `POST_NOTIFICATIONS` for alerts, and `RECEIVE_BOOT_COMPLETED` for reminder rescheduling. Internet permission is used only to download the fixed catalog's model archive; it is not needed for commands, recognition after installation, local model activation, TTS, notes, or shortcuts. Models and user data stay in app-private storage.

## Build

Requirements: JDK 17 or 21, Android SDK platform 36, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, and the included Gradle 8.13 wrapper.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew packageDebugZip
```

The normal debug APK remains at `app/build/outputs/apk/debug/app-debug.apk`. `packageDebugZip` stages `app/build/downloadable/Zero-Assistant.apk` and creates `app/build/downloadable/Zero-Assistant.zip`, whose only root entry is `Zero-Assistant.apk`. It verifies APK/ZIP integrity and required Android archive entries. GitHub Actions uploads one artifact named `Zero-Assistant.zip`: open the repository's **Actions** page, select **Build ZERO Android APK**, open a successful run, and download the artifact in its **Artifacts** section. Install the APK with `adb install -r Zero-Assistant.apk`.

Unit tests cover command parsing/history, TTS initialization and language failures, catalog validation, model transfer state, bundled installation, checksum and path-traversal rejection, cancellation, Vosk validation gates, active-model persistence, recognition error states, planner recurrence/persistence, battery crossing/re-arming, and note/shortcut persistence/action validation. They do not replace physical-device verification of microphone capture, on-device TTS voices, real speech accuracy, WorkManager delivery timing, or battery broadcasts.

## Privacy and terms

Typed commands, command execution, history, notes, reminders, shortcuts, battery evaluation, and model inference are processed on-device. Network use is limited to the fixed HTTPS speech-model downloads in the catalog. ZERO does not upload commands, audio, notes, reminders, or history, and has no accounts, cloud AI, analytics, or ads. Local history, notes, reminders, shortcuts, alert settings, and installed models use app-private storage. Audio is not retained. Phone calls are external actions: ZERO displays and confirms the intended number before requesting permission or opening the dialer. Reminder and battery notification delivery depends on Android notification permission, scheduling, power management, and whether ZERO has been force-stopped. Calls, app availability, flashlight hardware, speech recognition quality, and installed Android TTS voices depend on the device and user confirmation.