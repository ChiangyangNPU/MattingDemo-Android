package com.android.formatting

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer

/**
 * RMBG-1.4 背景移除模型的 ONNX 推理处理器。
 *
 * 负责加载 ONNX 模型、执行图像预处理、运行推理、解析输出 mask，
 * 并生成透明背景合成图和前景边界坐标。
 * 优先使用 NNAPI（GPU）加速，不可用时自动回退 CPU。
 *
 * @param context Android 上下文，用于从 assets 加载模型文件
 * @author chiangyang
 */
class RmbgProcessor(context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    val executionProvider: String
    private val inputShape: LongArray

    init {
        val modelBytes = context.assets.open(MODEL_ASSET).readBytes()

        // 尝试 NNAPI（GPU）加速，失败则回退 CPU
        val (s, provider) = try {
            val opts = OrtSession.SessionOptions()
            opts.addNnapi()
            Pair(env.createSession(modelBytes, opts), "NNAPI (GPU)")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "NNAPI unavailable, falling back to CPU", e)
            Pair(env.createSession(modelBytes), "CPU")
        }
        session = s
        executionProvider = provider

        // 从模型元数据读取输入 shape（动态维度为 -1，需要替换为默认值）
        val nodeInfo = session.inputInfo.values.firstOrNull()
        val rawShape = (nodeInfo?.info as? TensorInfo)?.shape
        inputShape = if (rawShape != null && rawShape.size == 4) {
            longArrayOf(
                if (rawShape[0] > 0) rawShape[0] else 1,
                3,
                if (rawShape[2] > 0) rawShape[2] else DEFAULT_INPUT_SIZE.toLong(),
                if (rawShape[3] > 0) rawShape[3] else DEFAULT_INPUT_SIZE.toLong()
            )
        } else {
            longArrayOf(1, 3, DEFAULT_INPUT_SIZE.toLong(), DEFAULT_INPUT_SIZE.toLong())
        }
        android.util.Log.i(TAG, "Model raw shape: ${rawShape?.joinToString()}, using: ${inputShape.joinToString()}")
    }

    /**
     * 对输入图像运行 RMBG-1.4 抠图推理。
     *
     * 处理流程：
     * 1. 将输入图像 resize 到模型要求的尺寸（默认 1024x1024）
     * 2. RGB 通道归一化（ImageNet mean/std）
     * 3. 创建 ONNX 输入张量并执行推理
     * 4. 解析输出 mask，生成灰度 Alpha Mask
     * 5. 将 Alpha Mask 缩放回原图尺寸，合成透明背景图
     * 6. 计算前景物体的边界坐标
     *
     * @param bitmap 输入的原始图像 Bitmap
     * @return [Result] 包含透明背景抠图、Alpha Mask 和前景边界坐标
     * @throws IllegalStateException 如果输出张量格式异常
     * @author chiangyang
     */
    fun process(bitmap: Bitmap): Result {
        val startTime = System.currentTimeMillis()
        val chw = inputShape
        val h = chw[2].toInt()
        val w = chw[3].toInt()
        val origW = bitmap.width
        val origH = bitmap.height
        android.util.Log.i(TAG, "process: input=${origW}x${origH}, resize to ${w}x${h}")

        // 1. 预处理：resize → normalize → NCHW float array
        val resized = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        resized.getPixels(pixels, 0, w, 0, 0, w, h)

        val floatBuf = FloatBuffer.allocate(3 * w * h)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        for (c in 0 until 3) {
            for (i in pixels.indices) {
                val channel = when (c) {
                    0 -> (pixels[i] shr 16 and 0xFF)  // R
                    1 -> (pixels[i] shr 8 and 0xFF)   // G
                    else -> (pixels[i] and 0xFF)       // B
                }
                floatBuf.put((channel / 255f - mean[c]) / std[c])
            }
        }
        floatBuf.rewind()

        // 2. 创建输入张量并推理
        val preprocessTime = System.currentTimeMillis()
        android.util.Log.i(TAG, "process: preprocessing done in ${preprocessTime - startTime}ms")
        val inputTensor = OnnxTensor.createTensor(env, floatBuf, chw)
        val output = session.run(mapOf(session.inputNames.first() to inputTensor))
        val inferenceTime = System.currentTimeMillis()
        android.util.Log.i(TAG, "process: inference done in ${inferenceTime - preprocessTime}ms")

        // 3. 解析输出 mask — 从 OnnxTensor 提取 float 数据
        val outputTensor = output.first().value as OnnxTensor
        val outputShape = outputTensor.info.shape  // [1, 1, H, W]
        val maskH = outputShape[2].toInt()
        val maskW = outputShape[3].toInt()
        val maskData = FloatArray(maskH * maskW)
        outputTensor.floatBuffer.get(maskData)
        android.util.Log.i(TAG, "process: output shape=${outputShape.joinToString()}, mask=${maskW}x${maskH}")

        inputTensor.close()
        output.close()

        // 4. 生成 alpha mask（灰度）
        val alphaMask = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(maskW * maskH)
        for (i in maskData.indices) {
            val v = (maskData[i].coerceIn(0f, 1f) * 255).toInt()
            maskPixels[i] = 0xFF shl 24 or (v shl 16) or (v shl 8) or v
        }
        alphaMask.setPixels(maskPixels, 0, maskW, 0, 0, maskW, maskH)

        // 5. 合成透明背景图
        val scaledAlpha = Bitmap.createScaledBitmap(alphaMask, origW, origH, true)
        val result = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(origW * origH)
        val alphaPixels = IntArray(origW * origH)
        bitmap.getPixels(srcPixels, 0, origW, 0, 0, origW, origH)
        scaledAlpha.getPixels(alphaPixels, 0, origW, 0, 0, origW, origH)
        val resultPixels = IntArray(origW * origH)
        for (i in srcPixels.indices) {
            val a = alphaPixels[i] and 0xFF
            val r = (srcPixels[i] shr 16 and 0xFF) * a / 255
            val g = (srcPixels[i] shr 8 and 0xFF) * a / 255
            val b = (srcPixels[i] and 0xFF) * a / 255
            resultPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        result.setPixels(resultPixels, 0, origW, 0, 0, origW, origH)

        // 6. 计算前景边界坐标
        val bbox = findBoundingBox(maskData, maskW, maskH, origW, origH)
        val totalTime = System.currentTimeMillis() - startTime
        android.util.Log.i(TAG, "process: total done in ${totalTime}ms, mask=${maskW}x${maskH}, bbox=$bbox")

        return Result(result, alphaMask, bbox)
    }

    /**
     * 在 mask 数据中查找前景区域的边界矩形。
     *
     * 遍历 mask 中所有超过阈值（0.5）的像素，找出最小/最大 x/y 坐标，
     * 然后按比例映射回原图的像素坐标。
     *
     * @param mask 模型输出的 float 型 mask 数据，值域 [0, 1]
     * @param maskW mask 的宽度（像素）
     * @param maskH mask 的高度（像素）
     * @param origW 原始图像的宽度（像素）
     * @param origH 原始图像的高度（像素）
     * @return [BoundingBox] 前景边界坐标，如果没有前景像素则返回 null
     * @author chiangyang
     */
    private fun findBoundingBox(
        mask: FloatArray, maskW: Int, maskH: Int, origW: Int, origH: Int
    ): BoundingBox? {
        var minX = maskW; var minY = maskH; var maxX = 0; var maxY = 0
        var found = false
        for (y in 0 until maskH) {
            for (x in 0 until maskW) {
                if (mask[y * maskW + x] > THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    found = true
                }
            }
        }
        if (!found) return null
        // 映射回原图坐标
        return BoundingBox(
            left = minX * origW / maskW,
            top = minY * origH / maskH,
            right = maxX * origW / maskW,
            bottom = maxY * origH / maskH
        )
    }

    /**
     * 释放 ONNX Runtime 资源（Session 和 Environment）。
     *
     * 在不再使用处理器时调用，避免内存泄漏。
     *
     * @author chiangyang
     */
    fun close() {
        session.close()
        env.close()
    }

    /**
     * 推理结果数据类。
     *
     * @property cutout 透明背景合成图（ARGB_8888），背景 alpha 为 0
     * @property alphaMask 灰度 Alpha Mask，白色表示前景，黑色表示背景
     * @property bbox 前景物体的边界矩形坐标，无前景时为 null
     * @author chiangyang
     */
    data class Result(
        val cutout: Bitmap,
        val alphaMask: Bitmap,
        val bbox: BoundingBox?
    )

    /**
     * 前景边界矩形坐标数据类。
     *
     * 坐标值为原图像素坐标系下的绝对值。
     *
     * @property left 左边界 x 坐标
     * @property top 上边界 y 坐标
     * @property right 右边界 x 坐标
     * @property bottom 下边界 y 坐标
     * @author chiangyang
     */
    data class BoundingBox(val left: Int, val top: Int, val right: Int, val bottom: Int)

    companion object {
        private const val TAG = "RmbgProcessor"
        private const val MODEL_ASSET = "rmbg14.onnx"
        private const val THRESHOLD = 0.5f
        private const val DEFAULT_INPUT_SIZE = 1024
    }
}
