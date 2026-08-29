# MattingDemo — Android Background Removal

[中文文档](README-ZH.md)

## Overview

An Android demo app for **background removal** with **two switchable models**: [RMBG-1.4](https://huggingface.co/briaai/RMBG-1.4) and [u²-netp](https://github.com/xuebinqin/U-2-Net). Models run locally on-device via **ONNX Runtime** with **NNAPI GPU acceleration** (automatic CPU fallback). Switching models releases the previous one before loading the new one.

## Features

- **Image Selection** — Pick a photo from the system gallery via Android Photo Picker
- **Dual Model** — Switch between RMBG-1.4 (high quality) and u²-netp (lightweight) via a dropdown; the loaded model is released before the new one is loaded
- **Background Removal** — Run inference to generate a foreground mask
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
       │                    │
       │            ┌───────┴────────┐
┌──────┴──────┐     │ MattingModel    │
│ Model        │     │ · RMBG-1.4     │
│ Dropdown     │────▶│   1024, /255   │
│ (switch =    │     │ · u²-netp      │
│  release →   │     │   320, ImageNet│
│  load)       │     └───────┬────────┘
└─────────────┘      ┌──────┴──────┐
                     │ NNAPI (GPU) │
                     │   or CPU    │
                     └─────────────┘
```

Each model has its own preprocessing, which must not be mixed: RMBG-1.4 uses 1024×1024 input with `/255`-only normalization (applying ImageNet mean/std breaks its input distribution and misclassifies the whole image as foreground), while u²-netp uses 320×320 input with ImageNet mean/std normalization.

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
| Models | RMBG-1.4 (ONNX, ~42MB) / u²-netp (ONNX, ~4.5MB) |
| Architecture | MVVM (ViewModel + StateFlow) |
| Min SDK | API 35 |

## Models

| Model | File | Input | Normalization | File Size | Peak RAM* | Quality |
|-------|------|-------|---------------|-----------|-----------|---------|
| RMBG-1.4 | `rmbg14.onnx` | 1024×1024 | `/255` only | ~42MB | ~0.9GB | ★★★★☆ fine hair/branch detail |
| u²-netp | `u2netp.onnx` | 320×320 | ImageNet mean/std | ~4.5MB | ~0.5GB | ★★☆☆☆ coarse edges, thin structures lost |

\* Measured with ONNX Runtime **CPU** on desktop (arena & mem-pattern disabled). Actual on-device figures vary.

**Model selection tips:** RMBG-1.4 for quality; u²-netp for speed and low memory (~2× less RAM, much faster load). Note that RMBG-1.4 is **non-commercial** (see License); u²-netp is Apache-2.0 and commercially usable.

## Performance

| Stage | Time |
|-------|------|
| Preprocessing (resize + normalize) | ~195ms |
| Inference (NNAPI GPU) | ~1,959ms |
| Post-processing (mask + composite) | <100ms |
| **Total** | **~2.2s** |

*RMBG-1.4 on Qualcomm Adreno GPU. u²-netp is considerably faster (320×320 input, ~4.5MB model). Actual performance varies by device.*

## Project Structure

```
app/src/main/
├── assets/
│   ├── rmbg14.onnx              # RMBG-1.4 model (~42MB)
│   └── u2netp.onnx              # u²-netp model (~4.5MB)
├── java/com/android/formatting/
│   ├── MainActivity.kt           # Compose UI (incl. model dropdown)
│   ├── MainViewModel.kt          # MVVM state management + model switching
│   └── RmbgProcessor.kt          # ONNX inference engine (per-model preprocessing)
└── res/
    └── layout/
        └── activity_main.xml     # (unused, Compose replaces XML)
```

## Build & Run

1. Open the project in Android Studio
2. Ensure `rmbg14.onnx` and `u2netp.onnx` are in `app/src/main/assets/`
3. Build and run on a device with API 35+

```bash
./gradlew installDebug
```

## Usage

1. Launch the app and wait for the default model to load (status bar shows "RMBG-1.4 已加载")
2. Optionally switch models via the **抠图模型** dropdown below the buttons — switching releases the loaded model first, then loads the new one
3. Tap **选择图片** to pick a photo
4. Tap **运行抠图** and wait for the result:
   - Left: original image
   - Right: cutout result (transparent checkerboard)
   - Below: grayscale alpha mask
   - Bottom: foreground bounding box

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

### u²-netp Model

u²-net / u²-netp is developed by [xuebinqin (U-2-Net project)](https://github.com/xuebinqin/U-2-Net) under the **Apache License 2.0**, free for commercial use. The ONNX weights bundled in this demo come from the [rembg](https://github.com/danielgatis/rembg) project.

> ⚠️ **Disclaimer:** This demo project is for **educational and demonstration purposes only**. The author does not grant any commercial rights to the models. Please verify the latest license terms on the official model pages ([RMBG-1.4](https://huggingface.co/briaai/RMBG-1.4) / [U-2-Net](https://github.com/xuebinqin/U-2-Net)) before any use.

### Third-Party Dependencies

| Dependency | License |
|------------|---------|
| [ONNX Runtime Android](https://github.com/microsoft/onnxruntime) | MIT License |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Apache License 2.0 |
| [Material 3](https://m3.material.io/) | Apache License 2.0 |
| [AndroidX](https://developer.android.com/jetpack/androidx) | Apache License 2.0 |

---

**Author:** chiangyang
