# detail-ui · 详情页（WP2 + V-11/V-12 收口）

范围：ui/subject/SubjectDetailScreen.kt、ui/subject/EpisodeRatingChart.kt、ui/subject/RatingComparisonSection.kt、新建 ui/components/SectionHeader.kt。未跑 gradle（Lead 统一编译），**未验证编译**。

## 轮次 2（task-10）：V-11 分段 + V-12 胶囊五副面孔
只改 app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt。

1. **V-11 观看状态 5 段 → NirikoSegmentedControl**（替换原 ~:2667 手搓 Row + animateColorAsState + 实心状态色胶囊 + 12.sp）
   - `options = ManageableStatuses.map { it.verbFor(subject.type) }`：顺序与文案和改造前完全一致（想看/在看/看过/搁置/抛弃，按类型映射看/读/玩/听）。
   - `selectedIndex = ManageableStatuses.indexOf(state.currentStatus)`：越界 -1 时组件不高亮任何段，与改造前「一个都不选中」观感一致。
   - **enabled 已接线**：Lead 给组件补了 `enabled: Boolean = true`（NirikoSegmentedControl.kt:62，禁用压 0.38 不透明度 + selectable(enabled=)），这里改传 `enabled = !isUpdating`，并删掉 onSelect 里原先的 `if (!isUpdating)` 守卫（守卫与禁用态分居两处会漂）。
   - 同时删掉不再使用的 statusTone 调用及 statusTone / animateColorAsState / spring / sp 四条 import。
2. **V-12 选集胶囊**：`SelectionChip(...)` 函数体改为一行委派 `NirikoSelectionPill(text, selected, onClick, onLongClick)`；KDoc 重写为「委派」并保留 R3 根因（FilterChip 内部 Surface 吃长按；共享组件内部本就是 combinedClickable）。3 个调用点未动。
3. **V-12 豆瓣「搜索词条」**（~:1879 `AssistChip` → `NirikoActionPill`）：语义是「做一件事」，用主色文字的动作胶囊替代 M3 方角片；文案与 onClick（ratingCallbacks.onSearchDouban）不变。
4. **V-12 Steam 类型标签**（~:3423 `FilterChip`，selected=false、onClick={} → **`NirikoTagPill`**，与任务卡写的 FilterPill 不同）：这里 onClick 是空实现，是纯信息不是开关；保留可点外观等于让用户反复戳一个没反应的东西，故按语义选不可点标签胶囊。若 Lead 坚持 FilterPill，一行可换回。
5. 删除因替换而失效的 import：animateColorAsState、spring、combinedClickable、border、AssistChip、FilterChip、FilterChipDefaults、sp、LocalCardGlassLevel、GlassChipTokens、statusTone；新增 4 条 Niriko* 组件 import。

## 风险 / 未确认（轮次 2）
- **未验证编译**（未跑 gradle，无编译权）；以上为静态自查（import 增删已按 grep 逐个核对，无残留引用）。
- 未改任何共享组件签名、未跑 git、未新增依赖或颜色常量。
- 复核（Lead 提醒）：删掉 import 的 statusTone / GlassChipTokens / LocalCardGlassLevel / segBg / sp / border 在该文件已 0 引用（剩余 FilterChip、combinedClickable 字样只出现在注释与 KDoc 里），不会因此编译错。
- 预存未使用的 `androidx.compose.animation.core.Spring` import（行 19）不是本次引入，为减 diff 噪音未清。
- 新组件的文字是 labelMedium(13sp)（原 AssistChip 用 labelSmall）：豆瓣那行与「已绑定 #…（N 张）」同排，窄屏可能挤压，未截图确认。
- 分段控件是 fillMaxWidth，替代原先同样 fillMaxWidth 的 Row，宽度行为不变；深色/玻璃档位下未目视检查。

## 轮次 1（WP2）：V-15 / V-17 / V-18 / V-19 / V-20 / B-1
- 新建 ui/components/SectionHeader.kt：titleMedium 16sp/w600 + onSurface + 上下 12dp，可选 trailing 槽。
- SubjectDetailScreen.kt **V-17**：「详细信息」标题移出 heightIn(max=420.dp)+verticalScroll → 外层 Column{ SectionHeader; Column(heightIn(max=420.dp).verticalScroll()) }，标题与滚动区是**兄弟**，永远不被裁；代价卡内总高最坏 420 → 420+标题(~44)。「简介」标题同样换 SectionHeader。
- **B-1** 折叠：`if (!collapsed) content()` → AnimatedVisibility(expandVertically(spatialDefault, Top)+fadeIn(effectsFast) / shrinkVertically+fadeOut)；chevron 由 ▸/▾ 文字改 Icons.Filled.ExpandMore + rotationZ 0↔180。偏差：reduceMotion 用 EnterTransition.None/ExitTransition.None（orSnap 返回 AnimationSpec，expandVertically 要 FiniteAnimationSpec）。
- EpisodeRatingChart.kt **V-18** 结论条 Row(spacedBy14) → FlowRow(12/4) + maxLines=1/Ellipsis（不加 weight）；**V-19** 双折线改 themeChartPalette()[0]/[1] 并在图上方加图例行（Vico 与 Canvas 两条实现各一次），平均线保持 tertiary。
- RatingComparisonSection.kt **V-20** 柱图包 appleGlassCard()+padding(16dp)+SectionHeader，柱色 chartBarDefaultColor() alpha 0.85；compact 分支保持裸 Canvas。
- 未验证编译；折叠行命中区 44dp(<48) 属 03-feedback 范围，未动。
