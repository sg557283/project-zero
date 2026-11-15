Offline audio model assets

Sherpa-ONNX runtime 2.0.1 is included as app/libs/sherpa-onnx-2.0.1.aar.
Its upstream project is Apache-2.0 licensed. The AAR includes ONNX Runtime
native libraries; their MIT license is in ONNX-RUNTIME-LICENSE.txt.

Wake keyword spotting
- Model: sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
- Model card: https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
- License: Apache-2.0 (see sherpa-kws/LICENSE.txt)
- Language: English only.
- "3.3M" is the parameter count, not the package size. The app bundles the
  encoder, decoder, joiner, tokenizer, and three built-in wake phrases.

Audio behavior
- Wake listening uses VOICE_RECOGNITION at 16 kHz, mono, with software pre-gain.
- Push-to-talk and post-wake command transcription use Android's on-device
  SpeechRecognizer when available. ZERO does not fall back to network recognition.
- The bundled wake model does not recognize Hindi or custom wake phrases.
