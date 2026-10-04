# VEYRA

**Sua inteligência. No seu controle.**

VEYRA is an Android application focused on running GGUF language models locally on the device.

## Current status

This repository starts from an empty baseline. The first implementation stage establishes the native Android/Compose project, VEYRA dark visual identity, GGUF document selection through Android Storage Access Framework, and a debug APK workflow.

**Important:** selecting a GGUF file is implemented, but inference is not wired yet. The llama.cpp C++/JNI integration, streaming chat, cancellation, and safe model lifecycle are subsequent implementation stages. The UI intentionally does not simulate model responses.

## Stack

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.10.1 |
| Kotlin | 2.1.21 |
| Gradle | 8.13 |
| JDK | 17 |
| Compile / Target SDK | 35 |
| Minimum SDK | 26 |
| Android NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| ABI | arm64-v8a |
| Compose BOM | 2025.05.00 |
| Material 3 | 1.3.2 |

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35 installed, or run:

```bash
gradle --no-daemon :app:assembleDebug
```

The APK is generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs the same debug build on pushes and pull requests to `main`. The APK is uploaded as the `veyra-debug-apk` workflow artifact.

## Privacy

The initial app does not include analytics, account sign-in, remote AI APIs, or network permissions. The selected model is accessed through the Android Storage Access Framework; it is not copied into the Java heap.

## Roadmap

- [x] Android project baseline and VEYRA Compose theme
- [x] Responsive empty-state screen
- [x] GGUF document selection and persisted URI permission
- [x] GitHub Actions debug APK workflow
- [ ] llama.cpp pinned source and Android CMake build
- [ ] JNI model lifecycle and inference
- [ ] Streaming chat and generation cancellation
- [ ] Local conversation persistence
