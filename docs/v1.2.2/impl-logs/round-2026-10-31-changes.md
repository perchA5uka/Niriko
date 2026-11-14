# v1.2.2 第八批（2026-10-31）改动交接单

> 给文档维护者的唯一事实清单。所有条目都已落进源码（Lead 已编译通过；测试数/APK 大小由 Lead 在门禁跑完后补）。
> 文档口径：不写营销话术；每条声称必须能在源码找到；未验证的（真机/截图/性能）保持「未验证」。

## 0 背景

- 前七批已把批次 A/B 主干落地；本轮按用户「按 v1.2.2 计划继续完成」推进，并采纳 prototype-pending-changes.html 的四项外观 A 方案（分段 / chip / 菜单 / 统计数字）。
- 本轮新增审计输入：`docs/v1.2.2/gap-report-2026-10-31.md`（只读差距审计，218 行，与源码快照 10:00–10:25 对照）。
- 本轮之后状态：**除真机项、B-11 结构重写、V-04（用户裁决不做）、60 处无档位圆角外，01/02/03 的 P0/P1/P2 条目已全部落地或明确记账**。

## 1 控件语言（外观四项，A 方案）

| 项 | 落地 | 源码 |
|---|---|---|
| V-11 分段 | `NirikoSegmentedControl` 唯一实现：Pill 容器 + 主色 20% 淡底 + 1dp 描边 + 等宽 + 13sp + 48dp 行高；接线 3 处：统计页日历模式、详情页观看状态、作品库状态计数行（新增 details 第二行计数）；`GlassSegmentedContainer` 空壳已删 | `ui/components/NirikoSegmentedControl.kt`、`ui/stats/CalendarCard.kt`、`ui/subject/SubjectDetailScreen.kt`、`ui/common/LibrarySearchControls.kt` |
| V-12 chip | 三档语义：`NirikoTagPill`（信息）/ `NirikoFilterPill`（可切换）/ `NirikoSelectionPill`（选集，可长按）/ 另有 `NirikoActionPill`（动作）；旧 `TagChip` 已删除；UI 层 M3 FilterChip/AssistChip = 0 | `ui/components/NirikoChip.kt`、`ui/subject/**`、`ui/library/CollectionDashboard.kt` 等 |
| V-14 菜单 | `NirikoMenu` + `GlassMenuOverlay` 唯一入口（24dp 玻璃 + 从锚点缩放 + 退场 + Back/Esc + 焦点进出/归还）；旧包装器 `GlassAnchoredMenu` 已删；全站 M3 DropdownMenu = 0；详情页 5 处 M3 `DropdownMenuItem`（双行副标题）刻意保留 | `ui/components/NirikoMenu.kt`、`GlassMenuOverlay.kt`、`GlassMenuOrigin.kt` |
| V-21 统计数字 | `StatNumber`（24sp/w600/tnum，主指标主色）+ `StatLabel`（13sp）+ `StatNote`（11.5sp）+ `StatTile`（图标+标签+数字，accent 14% 薄涂）；统计页 20/22/26/28/32 五档已清零；详情评分区/环心/年度统计同源 | `ui/components/StatNumber.kt`、`ui/stats/**`、`ui/subject/RatingComparisonSection.kt`、`DetailStatCard.kt` |
| V-13 控件 | 设置页滑块 → `GlassSlider`；搜索筛选面板 Switch/RangeSlider → `GlassToggle` / 新 `GlassRangeSlider`（双 thumb 纯函数）；B 站同步 Switch 迁移；剧集评分 → `GlassRatingSlider`；ui/ 下 M3 Switch/Slider/RangeSlider = 0 | `ui/components/GlassControls.kt`、`GlassRangeSliderMath.kt`、`ui/settings/SettingsItem.kt`、`ui/subject/SearchFilterPanel.kt`、`ui/bilibili/BilibiliSyncScreen.kt`、`ui/episode/EpisodeDetailScreen.kt` |
| P2-1 搜索框 | 新 `NirikoSearchField`（Pill 玻璃 + 放大镜 + placeholder + 48dp 清除 + focus 描边）；设置页替换自绘 BasicTextField | `ui/components/NirikoSearchField.kt`、`ui/settings/SettingsScreen.kt` |

## 2 语言与层级

- **V-15 分区标题**：`SectionHeader` 真实调用从 4 处扩到 **43 处**（详情 19 / 人物 5 / 角色 3 / 剧集 4 + 来源/关联/剧集/人物分区 12）；剩余 6 处 `titleMedium+Bold` 是「数值强调」，按文件锁死计数。
- **V-16 页标题**：6 个 drill-in 页 `titleLarge`(22) → `headlineSmall`(24/w600)（详情/人物/角色/制作人员/剧集/Steam 登录）；设置二级页此前已是 24。
- **V-01 圆角**：继续走令牌；本轮未新增字面量；`ShapeLiteralPolicyTest` 基线按实测重算（测试仍是精确相等表）。
- **V-02 色值**：`CardRarityMaterial` 23 色等值搬进 `ui/theme/RarityPalette.kt`；IosStyleSearch 2 个绿进 `SearchIndicatorAccents`；LibraryGalleryCard 2 色清零。ui/ 下 `Color(0x` 只剩 `theme/`、`share/`、`LiquidGlassTokens.kt`。
- **V-03 字号**：统计/日历/详情小字全部走 `labelSmall`(11.5)/`labelMedium`(13)；日历角标改实底 badge；`fontSize = N.sp` 只剩 Type.kt（定义）、Badges.kt（压图 10sp 刻意）、share/**（隔离域）、LibraryGalleryCard 已清零。

## 3 动效 / 帧阶段 / 减少动态效果

- **B-15 日历切月**：标题 `AnimatedContent(effectsFast)`（只包标题，不引入双 Lazy），方向纯函数 + reduceMotion → 两侧 None，`sizeTransform = null`。
- **B-14 筛选面板**：面板内 `animateContentSize(spatialDefault/orSnapFinite)`；两个面板的 8 处手写 spring 收进 `panelEnter/panelExit`。
- **B-16 搜索结果**：`animateItem()` 逐条入场已核实在位。
- **B-13 四态**：新 `StateHost`（单实例、0.55→1、180ms 包络，首帧不播、reduceMotion 瞬切），接线 7 屏（人物/角色/制作人员/趋势/发现/搜索结果/作品库首屏）；**刻意不用 AnimatedContent**（双 Lazy 红线）。
- **B-11 搜索卡**：12 处手写字面量 → 六档令牌；5 处 reduceMotion；尺寸/位移读取从组合期移到布局/绘制期（新 `AnimatedLayoutSize.kt`）。**未做结构重写**（不合并单一 progress、不做 graphicsLayer 尺寸近似）——无真机逐帧验证条件，文件头已写取舍。
- **A.9 帧阶段**：底栏/宽屏 dock 的隐藏进度改成 `offset{}/graphicsLayer{}` 内读 + `derivedStateOf`（不再每帧重组 kyant 底栏）；时间线页偏移、页面指示器（改 Canvas）、进度条/range thumb 均移到绘制/布局期。
- **A.8 字面量**：`spring(` 业务字面量 21→0（只剩令牌本体与 kyant bottombar 移植值）；手写 tween 数字只剩 CardRarity 3000×2、详情 350×1（刻意）。
- **B-9 预测返回**：navigation-compose 2.8.5 的 NavHost 内部已用 `PredictiveBackHandler + SeekableTransitionState` 对 popEnter/popExit 做跟手 seek（用 aar 字节码核实）；新增 `PredictiveBackSupportTest` 钉住版本/清单开关/四条过渡/reduceMotion 分支/popExit 缩放 ≤12%。**真机逐帧未验证**。
- **P2-2 封面重播**：进程级 `CoverRevealTracker`（上限 512 FIFO），列表滚回不再重播 150ms 淡入。

## 4 反馈 / 命中区 / 细节

- **S02/S03/S04**：剧集行徽标移到 meta 下一行（不再抢位）；行尾常驻 chevron（展开旋转，reduceMotion 瞬切）；「全部 N 张」48dp 命中盒。
- **S07**：TMDb / AniList / VNDB 解绑改二次确认（文案与 VM 行为一致：TMDb 会删来源分集评分）；TMDb 解绑补 snackbar 回执。
- **S08**：infobox「打开链接」→ `NirikoActionPill(OpenInNew)`（48dp 壳）。
- **F-05**：`StatsViewModel.isBroadcastRefreshing` 外露；日历刷新胶囊显示「刷新中…」并禁用。
- **F-07/F-08/F-10/F-12/F-14/F-15**：选集 pill 长按触感；搜索菜单 chip/type cell 按压高亮 + selected 语义；TrendTab 按压 + selected；搜索关闭钮 48dp 外壳（视觉 44 不动）；5 张主卡 pressTilt 触感开启；保存回执 LongPress → Confirm。
- **F-11**：详情 hero 单击即翻面（保留双击；单击有 ~300ms 双击等待，已记账）；未引入 DataStore 首次提示。
- **F-01/F-02/F-03/F-04/F-06/F-13/F-16**：前七批已完成，本轮未回退。
- **V-22/V-23/V-24/V-25/V-17/V-18/V-19/V-20**：前七批已完成。

## 5 仍然未做 / 刻意保留（文档必须如实列出）

1. **真机验证全部未做**：无设备连接。截图基线仍是 2026-09-25 旧图；06-roadmap §7 的 320/360/411/600/840dp × 7 主题 × 3 玻璃档 × reduceMotion × fontScale 矩阵无记录；同屏 RenderEffect 实测未做（`GlassRenderEffectBudgetTest` 只守源码构造点）。
2. **B-11 结构重写**（单一 progress / 尺寸 graphicsLayer 近似）未做：需真机逐帧调，留待下一版。
3. **V-04 材质分层**：用户 D1 裁决「不收」，`GlassRenderEffectBudgetTest` 只守「不新增构造点」。G-3 风险：`NirikoSegmentedControl`（非 nested）与 `NirikoMenu` 都走实时折射路径，真机 FULL 档同屏是否 ≤2 未知。
4. **60 处无对应档位的圆角字面量**（6/8/10/11/14/28/48/50dp 等）：保留，`ShapeLiteralPolicyTest` 锁值锁次数。
5. **视觉风险待真机**：SectionHeader 12dp 留白让详情页节奏变松；StatNumber/StatTile 主色与状态色薄涂；GlassRangeSlider 双 thumb 相撞观感；日期条 pitch 44→48dp 在 320dp 窄屏边界；hero 单击翻面延迟；剧集评分「停止拖动 600ms 落库」可能丢最后一次（原实现松手即存）。
6. **已知取舍**：详情页 5 处 M3 DropdownMenuItem（双行副标题无槽位）；`share/**` 与 `Badges.kt` 的色值/字号隔离；`RatingComparisonSection` Canvas 刻度仍 8.sp（需同步改图表底部留白）。
7. **未做架构级改动**：无新表/迁移/DataStore 键；无新依赖。

## 6 新增/更新的护栏测试（本轮）

见 `app/src/test/java`：`GlassMenuOverlayTest`（替代 GlassAnchoredMenuTest）、`PredictiveBackSupportTest`、`ThemeColorAuditTest`、`TypographyLiteralAuditTest`、`ControlSourceTest`、`SearchCardMotionContractTest`、`GlassRangeSliderMathTest`、`CoverRevealTrackerTest`、`GlassSliderThumbContrastTest`、`StatNumberTierTest`、`CalendarTitleTransitionTest`、`CalendarTouchTargetTest`、`StatsTypographyContractTest`、`ChartPaletteContrastTest`、`DetailTypeScaleContractTest`；更新 `ShapeLiteralPolicyTest`、`MotionLiteralPolicyTest` 基线。

## 7 Lead 待补

- 最终测试统计（类/项/失败）与 APK 大小 → 由 Lead 在门禁后补到 README/release/impl-summary。
- `docs/v1.2.2/impl-summary.md` 需要追加 §6.11（本轮）并重写 §7；`docs/v1.2.2/README.md` §6 状态表、`06-roadmap.md`、`review-visual.md`、`01/02/03` 审计勾选需要同步（以本交接单为事实源）。
