# settings-ui · WP1 设置页对齐收口 + M1 行按压反馈 + F09 禁用态

范围：ui/settings/SettingsScreen.kt、SettingsGroup.kt、SettingsItem.kt、AdaptiveSettingsPane.kt、pages/ThemePackSection.kt，新建 ui/settings/SettingsLayout.kt、ui/components/NirikoRowIndication.kt、test/.../SettingsLayoutContractTest.kt。未跑 gradle（Lead 统一编译），**未验证编译**。

## 新建 ui/settings/SettingsLayout.kt
- 常量：`SettingsPageHorizontalInset = 16.dp`、`SettingsRowMinHeight = 56.dp`、`SettingsRowTwoLineMinHeight = 72.dp`、`SettingsChevronSize = 20.dp`、`SettingsCategoryIconSize = 36.dp`。
- `SettingsChevron(onDark=false)`：唯一 chevron 实现（ChevronRight 20dp，onSurfaceVariant；onDark 时不透明，避免 0.9 alpha 落在深底上像禁用）。
- `SettingsRowIconChip(icon)`：36dp + `NirikoShapes.ControlShape` + `GlassChipTokens.surfaceChipBackground(isDark, emphatic=false, glassEnabled)` + primary 图标。
- 为什么：V-05/V-07/V-09 的「三种缩进 / 三种行 / 两种箭头 / 两种当前值」都源于各处各写一份数字；先把数字和三种原子组件收到一个文件，页面才可能真正对齐。

## V-05 水平缩进统一 16dp（组卡自带内距）
- **做法**：SettingsSplitGroup 外层 Column 加 `padding(horizontal = SettingsPageHorizontalInset)`；组标题、主页标题、宽屏左列标题同用该常量；宽屏 Row 的 12dp 横向 padding 删除（左右内缩交给列内）。
- **代价（需 Lead 知情）**：SettingsSplitGroup 是公共组件，凡用它而**未**自带内距的页面都会多出 16dp：SharePreviewSheet、WallpaperLibraryScreen、RatingSourceSettingsSection、GrayChannelSettingsSection、DetailSectionsSettingsScreen、LibrarySettingsScreen、About、Donate、SearchSettingsScreen。宽屏两栏 gutter 变 48dp（16+16+16）。
- 未改调用点即生效是有意为之：逐页改 16 处比改 1 处更容易漏，且漏掉就是"这页还是旧的"。

## V-07 行高 / 箭头 / V-09 当前值
- 六种行统一 `heightIn(min = 56.dp 或 72.dp)`（带 description 走 72），删掉各自 48/70dp 的散装高度。
- 行尾箭头全部换 SettingsChevron：分类行 24dp→20dp、搜索结果行各写的一份删除、宽屏导航行**新增**（原来没有箭头，看不出可点）。
- V-09 只留裸文字：`SettingsValueText = labelLarge + onSurfaceVariant + TextAlign.End + maxLines 2`，删 `ValueBadgeShape`/8dp 徽标变体；滑块行拖动时只换文字（原来整块底色随值闪）。
- 值文本不抢 weight（标题 weight(1f)），长值不会把标题挤成两行。

## V-08 分类行图标容器 + 宽屏选中态
- 窄屏分类行 / 宽屏导航行图标：36dp 圆形 secondaryContainer 实底 → 36dp ControlShape + 玻璃标签底 + primary 图标（与组卡同族，不再是一排实心绿球）。
- 宽屏选中态：edge-to-edge 55% 绿底 → CardShape 圆角 35% secondaryContainer 实底 + 行尾 SettingsChevron(onDark=selected)。边界清楚，且与右侧正文有了指向关系。

## M1 行按压反馈（ui/components/NirikoRowIndication.kt）
- `Modifier.nirikoRowIndication(interactionSource, cornerRadius, enabled)`：drawWithContent 先 drawContent，再按按压进度画整行圆角 onSurface 覆盖（alpha = 进度 × `ROW_PRESS_ALPHA`）。
- 进度用 Animatable，LaunchedEffect(interactionSource, enabled, motion) + `interactions.collectLatest`；Press→pressIn()、Release/Cancel→pressOut()；reduceMotion 时 snap()；中途禁用 snapTo(0)。
- **关键纪律**：`progress.value` 只在 draw 闭包里读 —— 放组合期会每帧重组整行；8% alpha 看不出差别，但 34 个开关行滚动开销差一个数量级。
- 替换 7 处 indication（SettingsItem 五处 + SettingsScreen 分类行/搜索结果行 + AdaptiveSettingsPane 导航行）；分类行的圆角 morph 动画**保留**（正向样本，它消费同一个 interactionSource，只把 ripple 换掉）。

## F09 禁用态
- SettingsActionRow：`.alpha(if (enabled) 1f else 0.38f)` + `clickable/enabled = enabled && !isLoading`。此前禁用行和可用态只差一个水波，用户看不出点不了。

## V-10 搜索框 / V-16 标题 / V-06 主题包分组
- 搜索框：M3 Outlined 文本框 → Row(Icon + BasicTextField) 的胶囊玻璃（PillShape + surfaceChipBackground，无描边，minHeight 56dp，清除按钮 IconButton 48dp + contentDescription「清除搜索」）。
- V-16：二级页标题本来就是 headlineSmall（24sp/w600，Type.kt），本次只补注释把它钉住，未改字体。
- V-06：ThemePackSection 两处 20dp 组卡 → SettingsPageSplitGroup（24dp），外观页 11 组终于同半径。

## 契约测试 test/.../SettingsLayoutContractTest.kt
- 照 SettingsSurfaceTokenTest 的源码文本断言风格：16/56/72/20dp 常量、页面不再手写 `start = 20.dp`、组卡自带内距、headlineSmall 24sp/w600、SettingsScreen 不含 OutlinedTextField、三处不含 ChevronRight、不含 8dp 徽标/ripple、含 0.38 禁用 alpha、ThemePackSection 的 24dp 组卡数为 2。
- 只做文本契约，不保证编译、不衡量观感。

## 风险 / 未确认
- **未验证编译**：以下为静态自查，非编译证据。重点核对项：`Modifier.nirikoRowIndication` 是 @Composable 扩展、在 modifier 链里调用；`Animatable(0f)` + `pressIn()/pressOut()` 返回 FiniteAnimationSpec 赋给 AnimationSpec<Float>；`BasicTextField(value: String)` 用的是仓库既有同款重载（IosStyleSearchComponent.kt:469）。
- 宽屏左列导航行选中底色 CardShape(20dp) 会被组内行 Surface 的 morph 半径（4→20dp）裁切，中间行角部呈 4dp 圆角；8% 覆盖层与 35% 底都在裁剪内，未做真机对比。
- 行高统一后带 description 的行比原来矮 2dp、单行行高比原来高 8dp，长列表总高会变（设置主页 8 行分类）。未做截图对比，需按 ui-review 清单目测。
- SettingsSplitGroup 的公共内距会连带影响 9 个未在本次范围内的页面（见 V-05 段），如需逐页控制需再加开关，当前未加。
