# VEYRA

**Sua inteligência. No seu controle.**

VEYRA is an Android application for local GGUF inference on arm64 Android devices.

## Version 0.2.0

- Jetpack Compose dark interface based on the VEYRA visual reference.
- One-tap streaming download of Gemma 3 1B Instruct Q4_K_S into app-private storage.
- Native C++/JNI integration with llama.cpp.
- CPU-first inference, 2048-token context, bounded thread count, token streaming and stop flag.
- Chat prompt formatting uses the GGUF model's embedded chat template.
- No remote AI API, account, analytics, or conversation upload.

The INTERNET permission is used only for the explicit model download. Inference and prompt processing run locally after the model is downloaded.

## Model

The download button fetches:

`unsloth/gemma-3-1b-it-GGUF / gemma-3-1b-it-Q4_K_S.gguf`

The GGUF is streamed to `filesDir/models/` through a temporary file and renamed after the download completes. It is not loaded into the Kotlin heap. The native engine opens the final app-private path using memory mapping.

## Native engine

llama.cpp is fetched reproducibly by CMake from upstream commit:

`11fe02151f79c41d0d4af7da708755d73b9c0da6`

Build configuration:
- Android NDK 27.2.12479018
- CMake 3.22.1
- ABI: arm64-v8a
- CPU-only baseline (`n_gpu_layers=0`); Vulkan, CUDA, OpenCL, SYCL, Metal and RPC backends are explicitly disabled in CMake; `GGML_CPU_KLEIDIAI=ON` and `GGML_LTO=ON`
- llama.cpp API: `llama_model_load_from_file`, `llama_init_from_model`, `llama_chat_apply_template`, tokenization, batch decode and sampler chain.

This is an initial CPU-first implementation. Performance and memory requirements vary by device; a 1B quantized model still needs additional RAM for runtime context and working buffers. Context is intentionally limited to 2048 tokens and generation to 512 new tokens.

## Build

Requirements: JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1, Gradle 8.13. The `debug` APK keeps the Android app debuggable, but CMake builds the native inference engine as Release for realistic CPU performance.

```bash
gradle --no-daemon --max-workers=2 :app:assembleDebug --stacktrace
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions performs the same Kotlin and native CMake build on pushes and pull requests to `main`, then uploads `veyra-debug-apk` as a workflow artifact. The extended Material icon pack is intentionally excluded; the settings gear is drawn locally to avoid bundling the unused icon catalogue.

## Current limitations

- The current chat sends the visible conversation as a single user prompt wrapped by the model's chat template; structured multi-turn message arrays and persistent history are not implemented yet.
- Only arm64-v8a is packaged.
- CPU inference only; no Vulkan/ GPU offload configured.
- No model deletion or model switcher UI yet.
- Actual device testing is still required to validate speed, thermal behavior and peak RAM on target phones.

## Privacy

Model file and chat UI state remain in app-private storage/process memory. No remote inference or telemetry is implemented. Network access is used only for the user-initiated HTTPS model download.
