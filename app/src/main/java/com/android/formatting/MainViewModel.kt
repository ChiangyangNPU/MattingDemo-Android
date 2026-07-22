package com.android.formatting

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面的 ViewModel，管理抠图推理的 UI 状态。
 *
 * 负责：
 * - 初始化 [RmbgProcessor] 推理引擎
 * - 从 URI 加载原始图片
 * - 在后台线程执行推理并更新 UI 状态
 * - 在 ViewModel 销毁时释放推理引擎资源
 *
 * @param application Android Application 实例
 * @author chiangyang
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var processor: RmbgProcessor? = null

    /**
     * 初始化 ONNX 推理处理器。
     *
     * 在 IO 线程中加载模型文件，尝试 NNAPI（GPU）加速，
     * 失败则自动回退 CPU。加载完成后更新状态栏文本。
     * 重复调用时会跳过已初始化的实例。
     *
     * @author chiangyang
     */
    fun initProcessor() {
        if (processor != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val p = RmbgProcessor(getApplication())
                _uiState.value = _uiState.value.copy(
                    status = "模型已加载 (${p.executionProvider})"
                )
                processor = p
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = "模型加载失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 从指定 URI 加载图片到 UI 状态。
     *
     * 通过 ContentResolver 打开输入流，解码为 Bitmap。
     * 加载成功后会清除之前的推理结果。
     *
     * @param uri 图片的 content:// URI
     * @author chiangyang
     */
    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
            if (bitmap != null) {
                _uiState.value = _uiState.value.copy(
                    originalUri = uri,
                    originalBitmap = bitmap,
                    cutoutBitmap = null,
                    alphaMaskBitmap = null,
                    bbox = null,
                    status = "图片已加载 (${bitmap.width}×${bitmap.height})，点击运行推理"
                )
            }
        }
    }

    /**
     * 对当前加载的图片执行 RMBG-1.4 抠图推理。
     *
     * 在 Default 调度器（后台线程）中运行推理，完成后更新：
     * - [UiState.cutoutBitmap] 透明背景抠图
     * - [UiState.alphaMaskBitmap] 灰度 Alpha Mask
     * - [UiState.bbox] 前景边界坐标
     * - [UiState.status] 状态文本（包含推理耗时）
     *
     * 如果未加载图片或处理器未初始化，则直接返回。
     * 推理失败时会在状态栏显示错误信息。
     *
     * @author chiangyang
     */
    fun runInference() {
        val bitmap = _uiState.value.originalBitmap ?: return
        val p = processor ?: return

        android.util.Log.i(TAG, "runInference: starting, image=${bitmap.width}x${bitmap.height}")
        _uiState.value = _uiState.value.copy(isProcessing = true, status = "推理中...")

        viewModelScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            try {
                val result = p.process(bitmap)
                val elapsed = System.currentTimeMillis() - startTime
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    cutoutBitmap = result.cutout,
                    alphaMaskBitmap = result.alphaMask,
                    bbox = result.bbox,
                    status = "完成 (${p.executionProvider})，耗时 ${elapsed}ms"
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "runInference failed", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    status = "推理失败: ${e.message}"
                )
            }
        }
    }

    /**
     * ViewModel 销毁时的清理回调。
     *
     * 释放 [RmbgProcessor] 持有的 ONNX Runtime 资源，
     * 包括 Session 和 OrtEnvironment。
     *
     * @author chiangyang
     */
    override fun onCleared() {
        super.onCleared()
        processor?.close()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }

    /**
     * 主界面 UI 状态数据类。
     *
     * 通过 StateFlow 驱动 Compose UI 的响应式更新。
     *
     * @property originalUri 用户选择的图片 URI
     * @property originalBitmap 用户选择的原始图片
     * @property cutoutBitmap 透明背景抠图结果，推理完成前为 null
     * @property alphaMaskBitmap 灰度 Alpha Mask，推理完成前为 null
     * @property bbox 前景边界坐标，推理完成前为 null
     * @property isProcessing 是否正在推理中
     * @property status 状态栏显示文本
     * @author chiangyang
     */
    data class UiState(
        val originalUri: Uri? = null,
        val originalBitmap: Bitmap? = null,
        val cutoutBitmap: Bitmap? = null,
        val alphaMaskBitmap: Bitmap? = null,
        val bbox: RmbgProcessor.BoundingBox? = null,
        val isProcessing: Boolean = false,
        val status: String = "正在初始化模型..."
    )
}
