# MattingDemo — Android 抠图 Demo

[English](README.md)

## 项目简介

一个支持**双模型切换**的 Android 抠图 Demo 应用：[RMBG-1.4](https://huggingface.co/briaai/RMBG-1.4) 与 [u²-netp](https://github.com/xuebinqin/U-2-Net)。通过 **ONNX Runtime** 在设备端本地运行推理，支持 **NNAPI GPU 加速**（自动回退 CPU）。切换模型时会先释放已加载的模型，再加载新模型。

## 功能特性

- **选择图片** — 通过系统 Photo Picker 从相册选择照片
- **双模型切换** — 下拉框在 RMBG-1.4（高质量）与 u²-netp（轻量）之间切换，切换时先释放已加载模型再加载新模型
- **背景移除** — 运行推理，生成前景 mask
- **透明背景合成** — 将抠图结果与透明（ARGB）背景合成
- **Alpha Mask** — 展示原始灰度 alpha mask
- **前景边界坐标** — 显示前景物体的像素坐标（`left`、`top`、`right`、`bottom`）
- **图片尺寸显示** — 在原始图、抠图结果和 Alpha Mask 标题右侧显示真实宽高
- **GPU 加速** — 优先使用 NNAPI（GPU），不可用时自动回退 CPU
- **Compose UI** — 基于 Jetpack Compose + Material 3 暗色主题

## 技术架构

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  选择图片    │────▶│  RmbgProcessor│────▶│  透明背景抠图    │
│  Photo Picker│    │  (ONNX RT)   │     │  + Alpha Mask    │
└─────────────┘     └──────────────┘     └─────────────────┘
       │                    │
       │            ┌───────┴────────┐
┌──────┴──────┐     │ MattingModel    │
│ 模型下拉框   │     │ · RMBG-1.4     │
│ （切换 =     │────▶│   1024, /255   │
│  释放→加载） │     │ · u²-netp      │
└─────────────┘     │   320, ImageNet│
                    └───────┬────────┘
                     ┌──────┴──────┐
                     │ NNAPI (GPU) │
                     │  或 CPU 兜底 │
                     └─────────────┘
```

每个模型的预处理方式不同，不可混用：RMBG-1.4 为 1024×1024 输入、仅 `/255` 归一化（套用 ImageNet mean/std 会破坏输入分布，导致整图误判为前景）；u²-netp 为 320×320 输入、需要 ImageNet mean/std 归一化。

## 开发环境

| 项目 | 版本 |
|------|------|
| Android Studio | Narwhal 4 Feature Drop \| 2025.1.4 |
| JDK | JetBrains Runtime 21 (JBR-21) |
| Gradle | 8.13 |
| Android Gradle Plugin (AGP) | 8.13.2 |
| Kotlin | 2.0.21 |
| Compose Compiler | 2.0.21（Kotlin 内置） |
| compileSdk | 36 (Android 16) |
| minSdk | 35 (Android 15) |
| targetSdk | 36 (Android 16) |

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 推理引擎 | ONNX Runtime Android 1.20.0 |
| 加速方式 | NNAPI（GPU），CPU 兜底 |
| 模型 | RMBG-1.4（ONNX，约 42MB）/ u²-netp（ONNX，约 4.5MB） |
| 架构模式 | MVVM（ViewModel + StateFlow） |
| 最低 SDK | API 35 |

## 模型说明

| 模型 | 文件 | 输入 | 归一化 | 文件体积 | 内存峰值* | 质量 |
|------|------|------|--------|----------|-----------|------|
| RMBG-1.4 | `rmbg14.onnx` | 1024×1024 | 仅 /255 | ~42MB | ~0.9GB | ★★★★☆ 发丝/树枝细节完整 |
| u²-netp | `u2netp.onnx` | 320×320 | ImageNet | ~4.5MB | ~0.5GB | ★★☆☆☆ 边缘偏糊、细结构丢失 |

\* 为 ONNX Runtime **CPU**（关闭 arena 与 mem pattern）在桌面端的实测值，真机数值因设备而异。

**选型建议**：追求抠图质量选 RMBG-1.4；追求速度与低内存选 u²-netp（内存约省一半、加载快一个量级）。注意 RMBG-1.4 **不可商用**（见许可证），u²-netp 为 Apache-2.0 可商用。

## 推理性能

| 阶段 | 耗时 |
|------|------|
| 预处理（resize + normalize） | ~195ms |
| 推理（NNAPI GPU） | ~1,959ms |
| 后处理（mask + 合成） | <100ms |
| **总计** | **约 2.2 秒** |

*RMBG-1.4 在 Qualcomm Adreno GPU 上的数据；u²-netp 为 320×320 输入、模型仅 4.5MB，明显更快。实际耗时因设备而异。*

## 项目结构

```
app/src/main/
├── assets/
│   ├── rmbg14.onnx              # RMBG-1.4 模型（约 42MB）
│   └── u2netp.onnx              # u²-netp 模型（约 4.5MB）
├── java/com/android/formatting/
│   ├── MainActivity.kt           # Compose UI 界面（含模型下拉框）
│   ├── MainViewModel.kt          # MVVM 状态管理 + 模型切换
│   └── RmbgProcessor.kt          # ONNX 推理引擎（按模型分支预处理）
└── res/
    └── layout/
        └── activity_main.xml     # （未使用，已改用 Compose）
```

## 构建与运行

1. 使用 Android Studio 打开项目
2. 确保 `rmbg14.onnx` 与 `u2netp.onnx` 模型文件位于 `app/src/main/assets/` 目录
3. 在 API 35+ 的设备上构建运行

```bash
./gradlew installDebug
```

## 使用流程

1. 启动应用，等待默认模型加载完成（状态栏显示 "RMBG-1.4 已加载"）
2. 如需切换模型，点按钮下方的 **「抠图模型」** 下拉框——切换会先释放已加载模型，再加载新模型
3. 点击 **「选择图片」** 从相册选一张照片
4. 点击 **「运行抠图」** 开始推理，查看结果：
   - 左侧：原始图片
   - 右侧：抠图结果（透明棋盘格背景）
   - 下方：Alpha Mask 灰度图
   - 底部：前景边界坐标

## 许可证

### 本项目（源代码）

本项目源代码采用 [MIT 许可证](https://opensource.org/licenses/MIT) 开源。

### RMBG-1.4 模型

RMBG-1.4 模型由 [BRIA AI](https://www.bria.ai/) 开发，托管在 [Hugging Face](https://huggingface.co/briaai/RMBG-1.4) 上。该模型**不是**标准开源许可证（如 Apache 2.0 或 MIT）。

**许可证要点：**

| 使用场景 | 是否允许 | 说明 |
|----------|----------|------|
| 个人学习 / 学术研究 | ✅ 允许 | 免费用于非商业研究和个人学习 |
| Demo 演示 / 内部评估 | ✅ 允许 | 免费用于 Demo 应用和内部评估 |
| 商业产品 | ❌ 不允许（需授权） | 必须向 BRIA AI 申请商用许可 |

**商业用途**需要联系 [BRIA AI](https://www.bria.ai/) 获取商用许可，或使用其付费 API 服务。

### u²-netp 模型

u²-net / u²-netp 由 [xuebinqin（U-2-Net 项目）](https://github.com/xuebinqin/U-2-Net)开发，采用 **Apache 许可证 2.0**，可免费商用。本 Demo 内置的 ONNX 权重来自 [rembg](https://github.com/danielgatis/rembg) 项目。

> ⚠️ **免责声明：** 本 Demo 项目**仅供学习和演示目的**。作者不授予任何模型的商业使用权。请在使用前前往官方模型页面（[RMBG-1.4](https://huggingface.co/briaai/RMBG-1.4) / [U-2-Net](https://github.com/xuebinqin/U-2-Net)）核实最新许可条款。

### 第三方依赖

| 依赖库 | 许可证 |
|--------|--------|
| [ONNX Runtime Android](https://github.com/microsoft/onnxruntime) | MIT 许可证 |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Apache 许可证 2.0 |
| [Material 3](https://m3.material.io/) | Apache 许可证 2.0 |
| [AndroidX](https://developer.android.com/jetpack/androidx) | Apache 许可证 2.0 |

---

**作者：** chiangyang
