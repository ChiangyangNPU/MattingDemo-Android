# RMBG-1.4 Android Demo

[中文文档](README-zh.md)

## Overview

An Android demo app for **background removal** using the [RMBG-1.4](https://huggingface.co/briaai/RMBG-1.4) model. The model runs locally on-device via **ONNX Runtime** with **NNAPI GPU acceleration** (automatic CPU fallback).

## Features

- **Image Selection** — Pick a photo from the system gallery via Android Photo Picker
- **Background Removal** — Run RMBG-1.4 inference to generate a foreground mask
- **Transparent Background** — Composite the cutout onto a transparent (ARGB) background
- **Alpha Mask** — Display the raw grayscale alpha mask
- **Bounding Box** — Show the foreground object's pixel coordinates (`left`, `top`, `right`, `bottom`)
- **Image Dimensions** — Display real width×height for original image, cutout result, and alpha mask
- **GPU Acceleration** — NNAPI (GPU) preferred, automatic fallback to CPU if unavailable
- **Jetpack Compose UI** — Modern declarative UI with Material 3 dark theme

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Photo       │────▶│  RmbgProcessor│────▶│  Transparent     │
│  Picker      │     │  (ONNX RT)   │     │  Cutout + Mask   │
└─────────────┘     └──────────────┘     └─────────────────┘
                           │
                    ┌──────┴──────┐
                    │ NNAPI (GPU) │
                    │   or CPU    │
                    └─────────────┘
```

## Development Environment

| Item | Version |
|------|---------|
| Android Studio | Narwhal 4 Feature Drop \| 2025.1.4 |
| JDK | JetBrains Runtime 21 (JBR-21) |
| Gradle | 8.13 |
| Android Gradle Plugin (AGP) | 8.13.2 |
| Kotlin | 2.0.21 |
| Compose Compiler | 2.0.21 (bundled with Kotlin) |
| compileSdk | 36 (Android 16) |
| minSdk | 35 (Android 15) |
| targetSdk | 36 (Android 16) |

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Inference | ONNX Runtime Android 1.20.0 |
| Acceleration | NNAPI (GPU), CPU fallback |
| Model | RMBG-1.4 (ONNX, ~42MB) |
| Architecture | MVVM (ViewModel + StateFlow) |
| Min SDK | API 35 |

## Performance

| Stage | Time |
|-------|------|
| Preprocessing (resize + normalize) | ~195ms |
| Inference (NNAPI GPU) | ~1,959ms |
| Post-processing (mask + composite) | <100ms |
| **Total** | **~2.2s** |

*Tested on Qualcomm Adreno GPU. Actual performance varies by device.*

## Project Structure

```
app/src/main/
├── assets/
│   └── rmbg14.onnx              # ONNX model file (~42MB)
├── java/com/android/formatting/
│   ├── MainActivity.kt           # Compose UI
│   ├── MainViewModel.kt          # MVVM state management
│   └── RmbgProcessor.kt          # ONNX inference engine
└── res/
    └── layout/
        └── activity_main.xml     # (unused, Compose replaces XML)
```

## Build & Run

1. Open the project in Android Studio
2. Ensure `rmbg14.onnx` is in `app/src/main/assets/`
3. Build and run on a device with API 35+

```bash
./gradlew installDebug
```

## License

### This Project (Source Code)

This project's source code is released under the [MIT License](https://opensource.org/licenses/MIT).

### RMBG-1.4 Model

The RMBG-1.4 model is developed by [BRIA AI](https://www.bria.ai/) and hosted on [Hugging Face](https://huggingface.co/briaai/RMBG-1.4). It is **NOT** released under a standard open-source license (such as Apache 2.0 or MIT).

**Key license terms:**

| Usage | Allowed? | Details |
|-------|----------|---------|
| Personal / Research | ✅ Yes | Free for non-commercial research and personal learning |
| Demo / Evaluation | ✅ Yes | Free for demo apps and internal evaluation |
| Commercial Products | ❌ No (requires license) | Must obtain a commercial license from BRIA AI |

**For commercial use**, you need to contact [BRIA AI](https://www.bria.ai/) to obtain a commercial license or use their paid API service.

> ⚠️ **Disclaimer:** This demo project is for **educational and demonstration purposes only**. The author does not grant any commercial rights to the RMBG-1.4 model. Please verify the latest license terms on the [official model page](https://huggingface.co/briaai/RMBG-1.4) before any use.

### Third-Party Dependencies

| Dependency | License |
|------------|---------|
| [ONNX Runtime Android](https://github.com/microsoft/onnxruntime) | MIT License |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Apache License 2.0 |
| [Material 3](https://m3.material.io/) | Apache License 2.0 |
| [AndroidX](https://developer.android.com/jetpack/androidx) | Apache License 2.0 |

---

**Author:** chiangyang
