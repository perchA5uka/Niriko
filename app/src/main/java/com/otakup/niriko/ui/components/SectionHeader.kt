package com.otakup.niriko.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 详情页 / 图表卡 / 设置页共用的**区块标题**（V-15）。
 *
 * 此前同一个「小节标题」在三处有三套写法：设置页 15sp/SemiBold/主题色，详情页 30+ 处
 * 16sp/Bold/白，图表卡内 15sp/Medium —— 字号、字重、颜色全靠各调用点手抄，改一处漏三处。
 * 现在统一到 [MaterialTheme.typography.titleMedium]（16sp / w600）+
 * [MaterialTheme.colorScheme.onSurface]，并统一上下 12dp 的垂直呼吸，
 * 让区块标题在整页里形成一致的节奏（标题上下 12dp = 与上一区块、与本区块内容的间距）。
 *
 * 为什么不把 padding 交给调用点：交给调用点就等于把「统一」重新拆散；需要更紧的地方
 * 应该调整外层容器间距，而不是给标题传不同的 padding。
 *
 * @param title 标题文案（纯文本，不做富文本解析）
 * @param modifier 外部修饰符（对齐 / 额外外边距），padding 由本组件统一负责
 * @param trailing 右侧附加槽（计数、跳转箭头、"全部"按钮等）；不传则标题右侧留空
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}
