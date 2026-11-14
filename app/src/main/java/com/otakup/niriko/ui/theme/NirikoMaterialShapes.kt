package com.otakup.niriko.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3 [Shapes] 与 Niriko 形状令牌的映射（根因 R1「令牌双轨」的修法）。
 *
 * 为什么需要它：`Theme.kt` 过去只传 colorScheme + typography、没传 shapes，
 * 于是 `MaterialTheme.shapes` 一直是 M3 默认的 4/8/12/16/28，
 * 而应用自己的圆角又是另一套 `NirikoShapes`（20/16/12/24/4/999）——
 * 同一份代码里 `MaterialTheme.shapes.small`（8dp）与 `NirikoShapes.Control`（12dp）
 * 指的是两套互不相干的口径，组件想用哪个纯看作者当时记得谁。
 *
 * 映射口径：**逐档取 Niriko 已有的真实取值**，不引入新数字：
 *  - extraSmall 4  = [NirikoShapes.RowInner]
 *  - small      8  = 列表内小件（M3 默认即 8dp，保持不变）
 *  - medium    12  = [NirikoShapes.Control]
 *  - large     16  = [NirikoShapes.Section]
 *  - extraLarge 24 = [NirikoShapes.Sheet]（原先走 M3 默认 28dp 的弹层是第 6 个圆角档）
 *
 * 两个**刻意**的例外（写在这里，避免以后有人"补全"它们）：
 *  1. [NirikoShapes.Pill]（999dp）不进 extraLarge：extraLarge 同时被
 *     ModalBottomSheet / DropdownMenu / Badge 等 M3 容器使用，映射成胶囊会让这些面板变成药丸。
 *  2. [NirikoShapes.Card]（20dp）不在这五档里。它是本应用最高频的卡片圆角（列表卡、设置组卡、
 *     统计区块），调用点直接写 `NirikoShapes.CardShape`；不要为了"能用 MaterialTheme.shapes.xxx"
 *     把 20 硬塞进 large（那会连带把 M3 大组件的 16dp 改成 20dp）。
 */
val NirikoMaterialShapes: Shapes = Shapes(
    // material3 1.4 的 Shapes 形参类型是 CornerBasedShape（不是 Shape），
    // 因此这里必须用 RoundedCornerShape 而不是 NirikoShapes.rounded()（后者返回 Shape）。
    extraSmall = RoundedCornerShape(NirikoShapes.RowInner),
    // 8dp = 列表内小件口径（M3 默认 small 就是 8dp，因此这一档观感不变）
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(NirikoShapes.Control),
    large = RoundedCornerShape(NirikoShapes.Section),
    extraLarge = RoundedCornerShape(NirikoShapes.Sheet),
)
