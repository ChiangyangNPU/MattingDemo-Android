package com.android.formatting

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * MattingDemo 主 Activity。
 *
 * 使用 Jetpack Compose 构建 UI，通过 [MainViewModel] 管理推理状态。
 * 继承自 [ComponentActivity] 以支持 Compose 的 [setContent]。
 *
 * @author chiangyang
 */
class MainActivity : ComponentActivity() {
    /**
     * Activity 创建时的初始化回调。
     *
     * 设置 Compose 内容根节点，应用 Material 3 暗色主题，
     * 并加载 [RmbgApp] 主界面 Composable。
     *
     * @param savedInstanceState 之前保存的实例状态，可为 null
     * @author chiangyang
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                RmbgApp()
            }
        }
    }
}

/**
 * MattingDemo 主界面 Composable。
 *
 * 包含完整的 UI 布局：
 * - 顶部应用标题栏
 * - 选择图片 / 运行推理 按钮组
 * - 原始图与抠图结果并排展示（棋盘格透明背景）
 * - Alpha Mask 灰度图展示
 * - 前景边界坐标卡片
 * - 底部状态栏
 *
 * 通过 [MainViewModel] 管理 UI 状态，使用 [PickVisualMedia] 选择图片。
 *
 * @param vm 主界面的 ViewModel 实例，默认通过 [viewModel] 创建
 * @author chiangyang
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RmbgApp(vm: MainViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.initProcessor() }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.loadImage(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MattingDemo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 选图 & 运行按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("选择图片") }

                Button(
                    onClick = { vm.runInference() },
                    enabled = state.originalBitmap != null && !state.isProcessing,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.isProcessing) "推理中..." else "运行抠图") }
            }

            // 原始图 & 抠图结果
            if (state.originalBitmap != null) {
                Text("原始图 & 抠图结果", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ImageCard("原始图", state.originalBitmap!!, Modifier.weight(1f))
                    if (state.cutoutBitmap != null) {
                        ImageCard("抠图结果", state.cutoutBitmap!!, Modifier.weight(1f), transparent = true)
                    }
                }
            }

            // Alpha Mask
            if (state.alphaMaskBitmap != null) {
                Text(
                    text = "Alpha Mask (${state.alphaMaskBitmap!!.width}×${state.alphaMaskBitmap!!.height})",
                    style = MaterialTheme.typography.titleSmall
                )
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = state.alphaMaskBitmap!!.asImageBitmap(),
                        contentDescription = "Alpha Mask",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // 前景边界坐标
            state.bbox?.let { bbox ->
                Text("前景边界坐标", style = MaterialTheme.typography.titleSmall)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "left: ${bbox.left}   top: ${bbox.top}\n" +
                                "right: ${bbox.right}   bottom: ${bbox.bottom}\n" +
                                "size: ${bbox.right - bbox.left} × ${bbox.bottom - bbox.top}",
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            }

            // 状态栏
            Text(
                text = state.status,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 图片展示卡片 Composable。
 *
 * 显示一张图片及其标题，支持透明棋盘格背景模式
 * （用于展示抠图结果的透明区域）。
 *
 * @param title 图片标题文本
 * @param bitmap 要展示的 Bitmap 图片
 * @param modifier 外部传入的 Modifier 修饰符
 * @param transparent 是否使用透明棋盘格背景，true 时展示透明效果
 * @author chiangyang
 */
@Composable
fun ImageCard(title: String, bitmap: Bitmap, modifier: Modifier = Modifier, transparent: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            text = "$title (${bitmap.width}×${bitmap.height})",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .clip(RoundedCornerShape(8.dp))
                .then(if (transparent) Modifier.checkerboardBackground() else Modifier.background(Color.DarkGray)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * 透明棋盘格背景 Modifier 扩展函数。
 *
 * 绘制灰白相间的棋盘格图案，用于直观展示图片的透明区域。
 * 类似 Photoshop 中的透明背景表示方式。
 *
 * @param cellSize 每个棋盘格单元的边长（dp），默认 20f
 * @param lightColor 浅色格子颜色，默认 #CCCCCC
 * @param darkColor 深色格子颜色，默认 #999999
 * @return 绘制了棋盘格背景的 Modifier
 * @author chiangyang
 */
@Composable
fun Modifier.checkerboardBackground(
    cellSize: Float = 20f,
    lightColor: Color = Color(0xFFCCCCCC),
    darkColor: Color = Color(0xFF999999)
): Modifier = this.background(Color.Gray).then(
    Modifier.drawBehind {
        val cols = (size.width / cellSize).toInt() + 1
        val rows = (size.height / cellSize).toInt() + 1
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val color = if ((row + col) % 2 == 0) lightColor else darkColor
                drawRect(
                    color = color,
                    topLeft = Offset(col * cellSize, row * cellSize),
                    size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                )
            }
        }
    }
)
