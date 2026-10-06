package com.otakup.niriko.ui.common

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import java.net.URL
import kotlin.math.abs

/**
 * 全屏图片查看器（项目此前完全没有）。
 *
 * 能力：
 * - `HorizontalPager` 翻页；
 * - 捏合缩放 + 平移 + 双击（telephoto `ZoomableAsyncImage`，缩放态自动禁翻页，避免手势打架）；
 * - 大图子采样解码（`BitmapRegionDecoder`），超长海报不会整张进内存；
 * - 保存到相册（MediaStore，Android 10+ 免权限写入 Pictures/Niriko；API ≤ 28 在**初次点击保存**时申请写存储权限，B2-1）；
 * - 用浏览器打开原图。
 *
 * 复用场景：剧照区、Anitabi 取景地、Steam 截图、封面候选预览。
 */
@Composable
fun ImageViewer(
    urls: List<String>,
    initialIndex: Int = 0,
    title: String? = null,
    /** 豆瓣等图床的防盗链 Referer（显示与保存都要用同一份）。 */
    referer: String? = null,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, urls.lastIndex),
        pageCount = { urls.size },
    )
    // 缩放状态按页各存一份：翻页回来时仍停在原来的缩放位置。
    // 当前页是否处于放大态，决定 HorizontalPager 能不能翻页（缩放态禁翻页，避免与平移手势打架）。
    val zoomStates = remember(urls.size) { mutableStateMapOf<Int, ZoomableImageState>() }
    val currentPageZoomFraction = zoomStates[pagerState.currentPage]?.zoomableState?.zoomFraction
    val isCurrentPageZoomed = currentPageZoomFraction != null && currentPageZoomFraction > 0f

    // 计划 B2-1（用户确认：初次在使用保存功能时申请）：
    // 只有 API ≤ 28 的 MediaStore 写入需要 WRITE_EXTERNAL_STORAGE；29+ 分区存储免权限。
    // 拒绝后不做二次引导，用户再次点击保存仍会走系统询问。
    var pendingSaveUrl by remember { mutableStateOf<String?>(null) }
    val legacyStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val url = pendingSaveUrl
        pendingSaveUrl = null
        if (granted && url != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { saveImageToGallery(context, url, referer) }
                snackbarHostState.showSnackbar(if (ok) "已保存到相册（Pictures/Niriko）" else "保存失败")
            }
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("保存失败：未获得存储权限") }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentPageZoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val url = urls[page]
                // telephoto（Apache-2.0，me.saket.telephoto）：
                // - 缩放/平移/双击/超缩回弹由 ZoomableAsyncImage 统一实现，缩放态不会把图拖出边界
                //   （原实现按容器尺寸算平移上限，长图放大后仍能拖出画面）；
                // - 大图走 BitmapRegionDecoder 子采样，只解码可见区域，超长海报不再整张进内存。
                val zoomableState = rememberZoomableState(
                    zoomSpec = remember { ZoomSpec(maxZoomFactor = IMAGE_MAX_ZOOM_FACTOR) },
                )
                val imageState = rememberZoomableImageState(zoomableState)
                SideEffect { zoomStates[page] = imageState }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageRequest = remember(url, referer) {
                        coil.request.ImageRequest.Builder(context)
                            .data(url)
                            .apply {
                                referer?.let { value ->
                                    headers(okhttp3.Headers.Builder().add("Referer", value).build())
                                }
                            }
                            .build()
                    }
                    ZoomableAsyncImage(
                        model = imageRequest,
                        contentDescription = title,
                        state = imageState,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // 顶部：标题 + 计数 + 关闭
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
                Text(
                    text = listOfNotNull(
                        title,
                        "${pagerState.currentPage + 1} / ${urls.size}",
                    ).joinToString("  ·  "),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
            }

            // 底部：保存 / 浏览器打开
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val currentUrl = urls.getOrNull(pagerState.currentPage)
                IconButton(onClick = {
                    val url = currentUrl ?: return@IconButton
                    if (needsLegacyStoragePermission(context)) {
                        // 只有旧系统才申请：初次点击保存时询问
                        pendingSaveUrl = url
                        legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                saveImageToGallery(context, url, referer)
                            }
                            snackbarHostState.showSnackbar(if (ok) "已保存到相册（Pictures/Niriko）" else "保存失败")
                        }
                    }
                }) {
                    Icon(Icons.Filled.Download, contentDescription = "保存到相册", tint = Color.White)
                }
                IconButton(onClick = {
                    val url = currentUrl ?: return@IconButton
                    runCatching { uriHandler.openUri(url) }
                }) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = "用浏览器打开", tint = Color.White)
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            )
        }
    }
}

/**
 * 把远程图片写入系统相册（Android 10+ 走 MediaStore，无需存储权限）。
 *
 * 修复 BUG-10：原来用 `URL(url).openStream()`——**既不能带 Referer（豆瓣/部分图床直接 403），
 * 也没有超时与大小上限（大图可能 OOM）**。现在改走 OkHttp：带 Referer/UA、30s 超时、
 * 超过 [MAX_DOWNLOAD_BYTES] 直接放弃，并流式写盘而不是先全读进内存。
 */
internal fun saveImageToGallery(
    context: Context,
    url: String,
    referer: String? = null,
): Boolean = runCatching {
    val request = okhttp3.Request.Builder()
        .url(url)
        .header("User-Agent", IMAGE_DOWNLOAD_UA)
        .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
        .build()
    val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val fileName = "niriko_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Niriko")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver

    // 注意：body 不能带出 execute().use {} —— 块结束时 response 已关闭。
    // 因此下载与写盘必须在同一个 use 块里完成。
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return false
        val declaredLength = response.body?.contentLength() ?: -1L
        if (declaredLength > MAX_DOWNLOAD_BYTES) return false
        val bodyStream = response.body?.byteStream() ?: return false

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        var written = 0L
        val ok = resolver.openOutputStream(uri)?.use { out ->
            bodyStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    written += read
                    if (written > MAX_DOWNLOAD_BYTES) return@use false
                    out.write(buffer, 0, read)
                }
            }
            true
        } ?: false
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }
}.getOrDefault(false)

/**
 * 计划 B2-1：是否需要写外部存储权限。
 * 只有 API ≤ 28 才需要（29+ 用 MediaStore 分区存储写入 Pictures/Niriko，免权限）。
 */
private fun needsLegacyStoragePermission(context: Context): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED

/** 单张图片的下载上限（防止误点一张几十 MB 的原图把内存/流量打爆）。 */
/** 图片查看器最大放大倍数（telephoto ZoomSpec；原实现为 1×..5×）。 */
private const val IMAGE_MAX_ZOOM_FACTOR = 5f

private const val MAX_DOWNLOAD_BYTES = 24L * 1024 * 1024

/** 下载图片用的 UA（豆瓣等图床会按 UA 分流）。 */
private const val IMAGE_DOWNLOAD_UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
