package com.otakup.niriko.ui.settings.pages

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.otakup.niriko.R
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsInfoRow
import com.otakup.niriko.ui.settings.SettingsSplitGroup

/**
 * 「向开发者捐赠」二级页外壳（窄屏：脚手架 + 返回箭头）。
 *
 * 与「关于」同构：正文抽成 [DonateSettingsContent]，窄屏套脚手架，
 * 宽屏两栏的右列直接调用同一份正文。
 */
@Composable
fun DonateSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDetailScaffold(title = "向开发者捐赠", onBack = onBack, modifier = modifier) {
        DonateSettingsContent()
    }
}

/**
 * 「向开发者捐赠」正文：两条收款码 + 一段说明。
 *
 * 收款码是用户自己的个人收款码，属于静态图片资源，不联网、不上报；
 * 页面本身不写任何设置项，捐赠与否不影响应用行为。
 */
@Composable
fun DonateSettingsContent() {
    Column {
        SettingsGroupTitle("向开发者捐赠", description = "帮助我继续更新")
        SettingsSplitGroup(content = listOf(
            {
                SettingsInfoRow(
                    icon = Icons.Outlined.VolunteerActivism,
                    title = "完全自愿",
                    description = "Niriko 是个人业余项目，所有功能对所有人开放；捐赠不解锁任何内容，也不影响任何功能。",
                )
            },
            {
                SettingsInfoRow(
                    icon = Icons.Outlined.Coffee,
                    title = "怎么支持",
                    description = "用另一台设备扫下面的收款码即可，金额随意。反馈、Bug 报告和 star 同样是支持。",
                )
            },
        ))

        SettingsGroupTitle("支付宝")
        DonateQrImage(
            resId = R.drawable.donate_alipay,
            aspectRatio = ALIPAY_ASPECT_RATIO,
            contentDescription = "支付宝收款码",
        )

        SettingsGroupTitle("微信支付")
        DonateQrImage(
            resId = R.drawable.donate_wechat,
            aspectRatio = WECHAT_ASPECT_RATIO,
            contentDescription = "微信收款码",
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "谢谢你的支持。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        )
    }
}

/**
 * 收款码图片：圆角 + 1dp 描边，按原始比例铺满可用宽度。
 *
 * 用 Coil 而不是 painterResource —— 两张原图分别是 1080×1620 与 1242×1692，
 * 按显示尺寸解码可以避免为了一个很少打开的设置页常驻十几 MB 位图。
 * 比例必须与资源文件一致，否则 ContentScale.FillWidth 会把二维码拉伸变形。
 *
 * 宽度上限 [DonateQrMaxWidth]：窄屏取满可用宽度（越大越好扫），
 * 宽屏两栏的右列可以有上千 dp，不封顶会把二维码拉成一面墙。
 */
@Composable
private fun DonateQrImage(
    @DrawableRes resId: Int,
    aspectRatio: Float,
    contentDescription: String,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(resId)
                .crossfade(false)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .widthIn(max = DonateQrMaxWidth)
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        )
    }
}

/** 收款码显示宽度上限（宽屏封顶；手机取满可用宽度）。 */
private val DonateQrMaxWidth = 420.dp

/** 支付宝收款码原始比例 1080 × 1620。 */
private const val ALIPAY_ASPECT_RATIO = 1080f / 1620f

/** 微信收款码原始比例 1242 × 1692。 */
private const val WECHAT_ASPECT_RATIO = 1242f / 1692f
