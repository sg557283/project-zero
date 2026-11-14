Offline audio model assets

Sherpa-ONNX runtime 2.0.1 is included as app/libs/sherpa-onnx-2.0.1.aar.
Its upstream project is Apache-2.0 licensed. The AAR also includes ONNX Runtime
native libraries; their MIT license is in ONNX-RUNTIME-LICENSE.txt.

Wake keyword spotting
- Model: sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
- Model card: https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
- License: Apache-2.0 (see sherpa-kws/LICENSE.txt)
- Language: English only.
- "3.3M" is the parameter count, not the package size. This app bundles the INT8
  encoder and joiner, the mobile decoder, tokenizer, and three built-in phrases.
  The selected runtime assets total approximately 5.5 MB.
- The source mobile archive SHA-256 is
  2e6ac2577310bfa2f4b6b5fab0478b868c9d0b2cb2c51b3e13b50581b588864d.

Voice activity detection
- Model: Silero VAD v4 ONNX from https://github.com/snakers4/silero-vad/tree/v4.0/files
- License: MIT (see vad/LICENSE.txt)
- The bundled file is 1,807,522 bytes; its SHA-256 is
  a35ebf52fd3ce5f1469b2a36158dba761bc47b973ea3382b3186ca15b1f5af28.

Audio behavior
- Background wake listening and push-to-talk use VOICE_RECOGNITION, 16 kHz,
  mono, 16-bit PCM, optional device AGC, and 1.6x tanh soft-clipped pre-gain.
- Silero VAD gates Vosk audio using a short pre-roll and speech hangover.
- Sherpa KWS detects the built-in English phrases. User-added phrases, Hindi
  phrases, and full command recognition remain on the selected Vosk model.
- Microphone capture stops before local TTS playback, preventing the assistant
  from recognizing its own spoken response.
