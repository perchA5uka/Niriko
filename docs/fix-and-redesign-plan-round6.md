# 修复方案（第 6 轮）· 发布前收尾

> 承接 `docs/fix-and-redesign-plan-round5.md`。本轮**不新增功能、不做破坏性重构**，
> 只解决用户复核后列出的 7 项问题：当季热门语义、发现页「全部」排序、历史排名空白、
> 删除评分月刊、「找条目」并入历史排名筛选器、搜索与搜索建议、设置页 UI 对齐 Kazumi。
>
> 本文件**只做方案**，经用户同意后再改代码。

---

## 0. 结论摘要（先看这个）

| # | 用户反馈 | 我的定位 | 性质 |
|---|---|---|---|
| 1 | 当季热门「展示有什么作品上线」而不是「当下有人气」 | 第 5 轮把它改成「/calendar ∪ 放宽窗口 → **全量 300 条** → 排序 chips」，于是它变成了一张**放送清单**；人气只体现在排序，不体现在**选取** | 语义错，重做「选取」层 |
| 2 | 「全部」六类型轮流排位 + 不足则历史排名填充，不好用 | 现状确认：`全部` = 5 类各取 top20 → 轮流交错 → `take(20)`。ChatGPT 的「统一 DiscoveryScore」方向正确，但有 **4 处在本项目数据源上不成立**（见 §3.2） | 换选取逻辑（规则版优先） |
| 3 | 历史排名**刷新后完全不显示内容** | 找到 3 个确定缺陷：榜单失败被**静默吞掉**（`getRanking` catch → 空表）、榜单空表落到「输入关键词搜索作品」这个对榜单毫无意义的空态、`全部` 不可翻页 | 确定缺陷 + 需真机诊断确认网络层 |
| 4 | 评分月刊完全不必要 | 与「找条目」同属第 4 轮 G 的多余模块，且它是唯一需要新增 DB 表的模块 | 删除（含 DB v28→v29） |
| 5 | 「找条目」模仿 Bangumi-master 完全是错的，应并进历史排名的筛选器 | 已读 Bangumi-master 源码：它的「找条目」= `screens/discovery/anime`（`MENU_MAP.Anime`），筛选器 `FILTER_DS` = **地区/版本/年份/季度/状态/类型/制作/排序/收藏**，与我们现在那套（年份区间/季度/标签/评分/排名/系列/NSFW）**不是一回事**；且我们发的 `filter.series` 在官方 schema 里**不存在** | 确定缺陷，按官方能力重映射 |
| 6 | 搜索完全没内容；搜索建议一直不展示 | 找到 9 个确定缺陷（含「网络失败」与「确实无结果」被混为一谈、筛选变更不重查、建议与结果在 UI 上互斥、建议链路 2s 超时过紧、token 从未挂到主 client）。**数据层根因需一次真机诊断坐实**（第 4 轮用同一手法解决了豆瓣自检） | 确定缺陷 + 诊断 |
| 7 | 设置页不满意，希望对齐 Kazumi（2026-09-24 版） | 已读 Kazumi `settings_page.dart` / `settings_list.dart` / `settings_detail_scaffold.dart` / `content_section.dart` / `split_list_row.dart`：分组入口页 + 二级页 `SettingsDetailScaffold` + `SettingsList`/`SettingsSection`/`SettingsTile`（leading 图标 + 标题 + 描述 + value + 尾部控件）+ `SettingsSliderTile` + 按压圆角 morph | UI 一致性改造 |

**唯一不可逆点**：删除评分月刊需要 DB v28 → v29（`DROP TABLE rating_snapshots`）。其余全部可回滚。

---

## 1. 方法与已确认的**外部事实**

本轮定位基于：通读发现页/搜索/设置相关 20 余个源文件；用仓库内的 git 对象读取到**第 4 轮前的最后提交**
`eabd962（"接入 VNDB 信息源 + 修复 Steam 主体卡片缺失与错绑"）`，作为用户所说的「以前版本」对照；
并核对了 Bangumi 官方 OpenAPI（`https://bangumi.github.io/api/dist.json`）。

从 OpenAPI 得到的三条**硬约束**（后面每个方案都受它约束）：

1. `POST /v0/search/subjects` 的 `filter` **只有**：`type`（或）、`meta_tags`（且）、`tag`（且）、
   `air_date`（且）、`rating`（且）、`rating_count`（且）、`rank`（且）、`nsfw`。
   —— **没有 `series`**（当前 `FindSubjectsRepository.buildRequest` 会发这个字段，属于未定义字段）。
2. `sort` 枚举 = `match | heat | rank | score`，其中 **`heat` 官方定义就是「收藏人数」**。
3. `GET /v0/subjects`（浏览）参数 = `type`(必填) / `cat` / `series`(仅书籍) / `platform`(仅游戏) /
   `sort` ∈ {`date`,`rank`} / `year` / `month` / `limit` / `offset`，第一页 cache 24h。
   —— 所以现在的「历史排名 = GET 榜单」这条通路本身是**合法**的。

搜索响应体只保证 `Subject`（`rating{score,total,rank,count}`、`images`、`date`、`total_episodes`…）；
**收藏数（`collection{wish,collect,doing,on_hold,dropped}`）是否在列表/搜索响应里返回，官方 schema 未承诺**
（详情端点有）。这一点直接决定 §3.2 的评估结论，因此 §7.6 安排了一次「响应字段探测」。

---

## 2. 问题 1 · 当季热门重做（「当下人气」而不是「上线清单」）

### 2.1 现状（第 5 轮 D26）

```
/calendar（权威：正在放送）
  ∪ air_date ∈ [今天-400天, 明天) 的检索 + isProbablyAiring 推算
  → 合并去重 → 类型过滤 → 排序（热度/评分/排名/开播日/放送日）
  → take(300)   ← 「不截断」是第 5 轮刻意做的
```

问题不在数据源，在**选取**：它把「本季在播」这件事**全量列出来**，
所以用户看到的是「这个季度有什么在播」，而不是「当下哪些最有人气」。

### 2.2 「以前版本」（第 4 轮前，`eabd962`）是怎么做的

```
mode == SEASONAL:
  全部：每类型 search(airDate=本季度, sort=heat, limit=12)
        → sortedByDescending(ratingScore).take(5) → 5 类轮流交错
  单选类型：search(airDate=本季度, sort=heat, limit=50) 按评分降序 take(15)
           不足 → 用 rank 榜单补齐（← 这条用户已明确否掉，本轮不恢复）
```

即：**本季度窗口 + 类内热度候选 + 每类只留前若干 + 交错混排**。
这正是用户说的「可以参考以前版本的实现」——**保留它的「取前若干」，去掉它的「历史排名补位」**。

### 2.3 方案（推荐：A + 多样性；可选：B 真趋势源）

**A（默认，零新依赖）· 「本季候选池 → 人气榜」**

| 步骤 | 内容 |
|---|---|
| ① 候选池 | **保留**第 5 轮的数据源（`/calendar` ∪ 放宽窗口检索），长连载不会丢 |
| ② 人气指标 | 类内用 `sort=heat`（**收藏人数**，官方语义）；本地排序默认用 `rating.total`（评分人数）作代理，二者都保留 |
| ③ 质量门槛 | 候选必须 `rating.total ≥` 阈值（默认 **100**，可调），挡掉「10 分 3 人」 |
| ④ 多样性重排 | 滑动窗口内**同类 ≤ 3**、**连续同类 ≤ 2**；并给「小类保底」（三次元/音乐/书籍各至少保留 3 条，若候选够） |
| ⑤ 截断 | 取前 **30**（原 300）。不足就是不足，**不填充**，尾部保留「查看历史排名 ›」出口 |
| ⑥ 副标题 | 保留「第 N 话 / 共 M 话（约）」 |
| ⑦ 文案 | 头部从「本季 · 共 N 部」改为「本季人气 · 展示前 30 / 候选 N 部」 |

**为什么要「多样性重排」而不是简单 take(30)**：第 5 轮把 take 去掉的原因是
「取前 15 会让中游的长连载消失」。多样性约束（每类保底 3、连续 ≤ 2）
**同时**解决「人气榜被番剧刷屏」和「长连载/小类被挤没」两个诉求 —— 这是本轮的核心里取舍。

**B（可选，真·趋势源）**：接入 Bangumi Next 的 `GET next.bgm.tv/p1/trending/subjects?type=&limit=&offset=`
（Kazumi 的「番剧趋势」就是这个接口），作为「当下人气」的**权威主源**，失败回退 A。
- 优点：服务端算好的趋势，最贴「当下有人气」的字面语义；支持 `type` 参数，天然可做「全部」。
- 代价：p1 是非公开接口，可用性/字段需真机探测（本机 `web_fetch` 到不了 `*.bgm.tv`）。
- 决策：**先做 A（发布前稳），B 作为一次性探测**；探测可用再在下一轮切换（或本轮加开关）。

### 2.4 类型筛选去重（顺手的必要清理）

现在有两套类型筛选：
- 搜索菜单的类型行（6 个：全部/动画/书籍/游戏/音乐/三次元）→ 写 `selectedType`，**同时**驱动发现页列表；
- 当季热门区自己的 chips（全部/动画/三次元）→ `seasonalTypeFilter`。

两套并存导致「选了书籍，但当季热门 chips 还写着全部」。方案：**删掉 `SeasonalHeader` 的类型 chips**，
统一由类型行驱动（`SeasonalTypeFilter` 退化为类型行到 `bangumiTypes` 的映射；「全部」= 5 类全查）。
排序 chips 保留。

### 2.5 验收

- 当季热门**不再**是一张放送清单：条数 ≤ 30，且**先人气后多样性**；
- 《假面骑士zzz》这类中游长连载仍能进入（多样性保底 + 阈值不过高）；
- 切换类型/排序**零请求**（保留第 5 轮的本地重排）；
- 新增/保留单测：候选池合并去重、热度排序、质量门槛、多样性重排（窗口上限、连续上限、小类保底）、
  「不填充」回归、空候选时的空态。

---

## 3. 问题 2 · 发现页「全部」排序逻辑重做 + ChatGPT 方案评估

### 3.1 现状

```kotlin
// SubjectSearchViewModel.doRefreshTrending(), currentType == null 分支
val typeList = listOf(2, 1, 4, 3, 6)
// 每类 getRanking(limit = 20) → 按 rank 升序 → for(i) 逐个类型交错 → merged.take(20)
```

即用户描述的「番剧 书籍 音乐 番剧 书籍 音乐…」。它的问题：
① 固定配额（每类必然同样多）；② 只有 20 条且**不可翻页**（`canPage = currentType != null`）；
③ 单类型路径里还有「不足则用历史排名填充」；④ 「全部」没有**跨类的质量可比性**。

### 3.2 对 ChatGPT 方案的合理性评估

| 它的主张 | 评估 | 理由 |
|---|---|---|
| 各类型先取 20–30 个形成候选池 | ✅ **采纳** | 与「以前版本」的类内取前若干一致，且是唯一能避免番剧霸榜的可行起点 |
| 统一计算 DiscoveryScore（热度35% + Rank30% + 评分20% + 新鲜度15%） | ⚠️ **部分不可行** | ① **收藏数拿不到**：`/v0/search/subjects` 响应只有 `rating.total`（评分人数），没有 `collection`；要拿收藏数得逐条打详情（125 条候选 = 125 次请求，不可接受）。② **rank 与 score 双计**：Bangumi 的 rank 本质就是分数排序，两者同时加权等于给评分记两次。③ **跨类不可比**：`rank` 与 `heat` 都是**类内**量纲，动画的 `rating.total` 普遍是音乐的 10 倍以上，直接归一化会把类别偏置带进来 |
| 收藏数做对数处理避免霸榜 | ❌ **本数据源做不到** | 同上 ①。替代：**用「类内取前 N」天然完成量纲归一的等价效果** |
| 评分同时考虑评分人数，避免「10 分但几个人评分」 | ✅ **但实现方式更好** | 官方 filter 直接支持 `rating_count`：`[">=100"]`，一条服务端过滤即可，不必做权重 |
| 类型多样性约束（最近 10 个同类 ≤ 4、连续 ≤ 2） | ✅ **采纳**（参数按 §2.3 定为 3 / 2） | 比固定配额灵活，是解决「刷屏」的正确工具 |
| 取消历史排名补位 | ✅ **采纳** | 与用户诉求一致 |
| 分类页面保持独立逻辑 | ✅ **采纳** | 单类型仍走自己的 rank/heat |
| 「长期架构：Discovery Ranking 独立成 Niriko 自己的一层，不绑 Bangumi」 | ✅ 方向采纳，**但只做接口形状** | 发布前夕不引入新数据源；只抽出 `DiscoveryFeed`（候选池 → 归一 → 重排）这一层纯函数 |
| 最终取 20–30 个 | ✅ 采纳 | 30 |
| 新鲜度 15% 加分 | ⚠️ **建议改成门槛** | 「人气」不应该被新鲜度奖励式地扭曲（老经典会系统性下移）；新鲜度只用来定义**候选范围**（本季/近一年/不限），不做加权 |

**没提到但必须处理的一条**：统一评分后再重排，**全局顺序不可分页**
（每页各自重排会重复/漏）。所以统一 Feed 必须**一次性取候选池（5 类 × 30）再本地重排**，
分页只对「单类型」生效。这条决定了实现形态。

### 3.3 方案（v1 规则版 / v1.5 加权版）

**v1（默认，可解释、零新依赖）**

```
每类型：POST /v0/search/subjects
        keyword="" , sort=heat（收藏人数）
        filter = { type:[t], rank:[">0"], rating_count:[">=100"] }（+ 用户筛选条件）
        limit=30
  ↓ 合并去重（按 subjectId，同 id 保留首次出现）
  ↓ 类内按 rank/score 归一化位置（0..1）
  ↓ 多样性重排（窗口内同类 ≤3、连续 ≤2、小类保底 3）
  ↓ take(30)
```

**v1.5（可选，等 §7.6 探测确认 `collection` 字段存在后再开）**
`DiscoveryScore = w1·log(1+heat) + w2·rankScore + w3·score·confidence + 类型多样性罚项`，
纯函数 + 常量权重 + 单测；默认**关闭**（保持 v1 的行为可解释）。

### 3.4 交付物

新增 `data/discover/DiscoveryFeed.kt`（纯函数：候选池 → 归一 → 多样性重排 → 截断），
由 `SubjectSearchViewModel` 的 `全部` 分支与「当季热门」共用（见 §2.3 步骤 ③④⑤）。

### 3.5 验收 + 单测

- 「全部」不再固定配额：同类型连排 ≤ 2、窗口内同类 ≤ 3；
- 「全部」有 30 条且能继续加载更多（按需：见 §4.3）；
- 候选不足时长度就是实际条数（**无填充**）；
- 单测：多样性重排（构造 5 类不均候选，断言分布）、类内归一、阈值过滤、去重、
  「不填充」回归、空候选。

---

## 4. 问题 3 · 历史排名刷新后完全不显示内容

### 4.1 确定缺陷（读代码即可确认）

| 编号 | 位置 | 问题 |
|---|---|---|
| R1 | `SubjectRepository.getRanking()` | `catch (e) { Log.e(...); emptyList() }` —— **失败被静默吞掉**，上层拿到的是「空列表」而不是「失败」 |
| R2 | `TrendingSection` 空态分支 | 榜单为空且 `error == null` 时，落到最后一个分支显示 **「输入关键词搜索作品」** —— 对榜单模式毫无意义，用户看到的就是「刷新后一片空白」 |
| R3 | `SubjectSearchViewModel.doRefreshTrending()` | `全部` = `merged.take(20)` 且 `canPage = currentType != null` → **只有 20 条且不能翻页** |
| R4 | `SubjectRepository.rankingCache` | 缓存 key 只有 `"$type-$offset"` —— 不含 `nsfw`/筛选条件，切筛选会命中旧榜单 |
| R5 | `refreshTrending()` | 已有刷新在跑时**直接丢弃**本次下拉刷新（`return`），用户以为「刷新没反应」 |
| R6 | `TrendingSection` 首行 | `if (!state.trendingMode.isTrend) return` —— 一旦 `DiscoverViewModel` 为空（`subject_search` 路由就没有传它），FIND/MONTHLY 会**渲染成完全空白** |

### 4.2 修法

1. **错误必须可见**：`getRanking` 改成返回 `Result`/抛异常（或返回 `null` 表示失败），
   `doRefreshTrending` 把失败写入 `state.error`；UI 显示错误 + 重试。
2. **空态分模式**：榜单模式空 → 「榜单暂无数据 / 加载失败，重试」；搜索模式空 → 「未找到相关作品」；
   绝不出现「输入关键词搜索作品」这类错位文案。
3. **全部可翻页**：改用 §3.3 的候选池 + 本地重排；「加载更多」在 `全部` 下按「候选池来源类型」继续取 offset。
4. **缓存 key 升级**为查询指纹（type + nsfw + 筛选 + sort），并在筛选变更时失效。
5. **刷新合并**：正在刷新时把「用户主动刷新」标记为 pending，当前请求结束后再补一轮（或直接复用其结果并提示）。
6. **删除 `isTrend` 早退**（本轮 FIND/MONTHLY 都被删除后不再需要）。

### 4.3 必须做的一次真机诊断（`§7.6` 同一入口）

「刷新后空白」有两种可能：**(a) 请求失败被吞**（R1/R2 已解释）、**(b) 请求本身在用户网络下失败**
（例如反代吞 POST body —— 代码里第 4 轮的注释就写着「绕开 POST body 被吞」）。
诊断入口一键跑：
`GET /v0/subjects?type=2&sort=rank`、`POST /v0/search/subjects{keyword:"巨人"}`、
`GET /search/subject/巨人`，各显示 HTTP 状态、耗时、返回条数、异常。
**一次截图即可定死方向**，避免继续猜（与第 4 轮豆瓣自检同一手法）。

---

## 5. 问题 4 · 删除评分月刊

### 5.1 删除清单

| 类别 | 对象 |
|---|---|
| UI | `DiscoverEntriesPane.MonthlyPane`、`TrendingMode.MONTHLY`、`DiscoverGridPane` 的「评分月刊」格子、顶栏「浏览」组的「评分月刊」 |
| ViewModel | `DiscoverViewModel` 的 monthly 状态与 `refreshMonthly`/`selectMonth`/`clearMonthlyMessage` |
| 数据层 | `data/repository/RatingMonthlyRepository.kt`、`data/local/dao/RatingSnapshotDao.kt`、`data/local/entity/RatingSnapshotEntity.kt` |
| 注入 | `NirikoApplication.ratingMonthlyRepository`、`MainPager` 的 `DiscoverViewModelFactory(monthlyRepository=…)` |
| DB | 实体移出 `NirikoDatabase.entities` → **version 28 → 29**，新增 `MIGRATION_28_29 = DROP TABLE IF EXISTS rating_snapshots` |

### 5.2 注意（真机才会暴露的坑）

- Room 的 schema 校验只在**真机首次开库**时执行：`asset`/导出目录里的 `29.json` 会在构建时生成，
  必须**逐字**核对 `29.json` 里不再包含 `rating_snapshots`，否则老用户升级即崩（第 4 轮踩过一次：`recordedAt INTEGER NOT NULL` 的 DDL 必须逐字一致）。
- 备份/同步**未包含**该表（第 4 轮的设计），所以删除**不影响**用户数据。
- 删除 `DiscoverViewModel` 后，`MainPager` 的 `discoverViewModel` 参数与 `SubjectSearchScreen` 的对应形参一并清理。

### 5.3 验收

- 发现页只剩 3 个入口（当季热门 / 历史排名 / Steam），宫格只剩 3 格；
- DB 从 v28 升级到 v29 后应用正常启动、旧数据完好（真机验证）。

---

## 6. 问题 5 · 「找条目」并入历史排名的筛选器（对齐 Bangumi-master）

### 6.1 Bangumi-master 的「找条目」到底是什么（已读源码）

- `MENU_MAP`（`src/constants/constants/data.ts:415`）里 `Anime → name: '找条目'`，对应
  `src/screens/discovery/anime/`（`index.tsx` 注释即「找番剧」）。
- 它的筛选器 = `src/screens/discovery/anime/ds.ts` 的 `FILTER_DS`：

| 维度 | key | 选项（来自 `utils/subject/anime/ds.ts`） | 备注 |
|---|---|---|---|
| 地区 | area | 日本 / 中国 | 单选 |
| 版本 | type | TV / 剧场版 / OVA / WEB | 单选 |
| 年份 | year | 2026…2001 / 2000以前 | 单选，`always` |
| 季度 | begin | 1月 / 4月 / 7月 / 10月 | 单选，`always` |
| 状态 | status | 连载 / 完结 / 未播放 | 单选 |
| 类型 | tags | 46 个内容标签 | **多选，且** |
| 制作 | official | ~120 家动画公司 | 单选 |
| 排序 | sort | 排名 / 上映时间 / 评分人数 / 随机 / 名称 | `always`，默认「评分人数」 |
| 收藏 | collected | 隐藏 | 显示/隐藏已收藏 |

- 交互：工具栏（类型/年份/月份/排序/筛选）+ **改动即查询** + 触底分页 + 布局切换。
- 数据源：它的这些维度来自**季度更新的离线数据集**（`utils/subject/anime` 直接本地过滤），
  我们无法照搬数据源，只能把**筛选能力**映射到官方 v0 API。

### 6.2 映射表（每个维度 → 我们能做到什么）

| 维度 | API 实现 | 说明 / 降级 |
|---|---|---|
| 地区 | `filter.tag = ["日本"]` / `["中国"]` | Bangumi 条目普遍带地区标签；真机抽样验证，空结果则隐藏该维度 |
| 版本 | `filter.tag = ["TV"]` / `["剧场版"]` / `["OVA"]` / `["WEB"]` | 同上 |
| 年份 | `filter.air_date = [">=YYYY-01-01","<YYYY+1-01-01"]`；「2000以前」= `["<2000-01-01"]` | 单边区间合法（官方支持单条表达式） |
| 季度 | 年份 + 季度 → 精确三个月窗口 | 已有实现（`FindSubjectsFilter.airDateRange`），直接搬 |
| 状态 | **无 API 支持** → 候选池内本地判定 | 未播放 = `air_date > today`（也可服务端做：`[">=today"]`，**推荐**）；连载 = `air_date <= today` 且 `isProbablyAiring`（复用第 5 轮纯函数）；完结 = 其余。UI 必须标注「按开播日推算」 |
| 类型 | `filter.tag`（AND，多选） | 已经是现在的做法，保留；选项改用 Bangumi-master 那 46 个高频词 |
| 制作 | `filter.tag = [公司名]`，用既有 `TagAliasMap` 展开多语言变体后**多请求合并** | 项目已有该机制（关键词搜索里的别名展开），复用；UI 标注「按标签匹配」 |
| 排序 | `sort`：排名→`rank` / 上映时间→`date` / 评分人数→`heat` / 评分→`score`；**随机** 与 **名称** 本地实现（随机=洗牌；名称=项目已有 `PinyinSearch` 排序键） | 与「改动即查询」兼容 |
| 收藏 | 本地过滤 `collectedSubjectIds`（已有） | 零请求 |
| NSFW | `filter.nsfw` | **我们的增强**，保留（Bangumi-master 有独立的 NSFW 页） |
| 评分区间 / 排名区间 | `filter.rating` / `filter.rank` + 新增 `filter.rating_count` | **我们的增强**，建议保留但收进「更多」；见 §12 待确认 |

**必须删掉的东西**：`filter.series`（官方 schema 无此字段，属未定义字段；
若服务端严格校验会被 400 整条拒掉 —— 这正是「找条目完全是错的」的一条候选根因）。
「系列」维度在本轮**直接删除**（Bangumi-master 的找条目里也没有它）。

### 6.3 界面形态（对齐它的工具栏）

- 历史排名页顶部固定工具条：**类型（沿用顶部类型行）· 年份 · 季度 · 状态 · 排序 · 筛选 · 更多**；
- 点开是 sheet/dropdown（不是现在那种一屏铺开的 chip 墙）；**改动即查询**（已有 D24 行为）；
- 保留触底分页 + 「共 N 条 / 候选池 N 条」计数；
- 保留筛选摘要行（现在已有 `conditionSummary`）。

### 6.4 删除清单

`FindSubjectsRepository`、`FindSubjectsFilter`（改名为 `BrowseFilter` 并迁到 `data/discover/`）、
`ui/search/DiscoverEntriesPane.kt`、`TrendingMode.FIND`、`DiscoverViewModel` 的 find 状态、
`FindSubjectsFilterTest` 重写为 `BrowseFilterTest`。

### 6.5 验收 + 单测

- 历史排名 = 「找条目筛选器 + 排名榜」：改任一维度立刻重查；
- 「全部类型 + 标签 + 年份」能跨类型检索（现在也能，但筛选器语义不同）；
- `filter` 里**不再出现 `series`**；
- 单测：每个维度 → 请求参数的映射（含「2000以前」退化为单边、季度跨年、随机/名称的本地排序、
  制作别名展开去重、状态过滤的三种判定）。

---

## 7. 问题 6 · 搜索与搜索建议

### 7.1 确定缺陷清单

| 编号 | 位置 | 问题 |
|---|---|---|
| S1 | `SubjectSearchViewModel.onQueryChanged` / `setSortMode`·`setNsfw`·`setFilter`·`clearFilters` | 输入只写 `queryInput`，**`_uiState.query` 在输入过程中恒为空**（只有点历史记录/清空时会写）；而上面四个 setter 读的正是 `_uiState.value.query` → 一旦输入过内容，改排序/筛选/R18 会走「刷新趋势」而不是「重查搜索」，表现为「筛选没用」。（`setType` 用的是 `queryInput`，注释自己承认了这个不一致） |
| S2 | `doSearch` 的 catch 顺序 | `catch (Exception)` 在 `catch (CancellationException)` **之前** → 取消异常被当成失败吞掉；`debounce+collectLatest` 的取消也会走进这个分支 |
| S3 | `SearchResultsPane` 分支顺序 | `suggestions.isNotEmpty()` 与 `isSuggestionsLoading` 都排在**结果之前** → 只要建议有内容或还在加载，搜索结果**永远不会显示**；且结果分支要求 `results` 与 `allResults` 同时非空（冗余耦合） |
| S4 | `doLoadSuggestions` | `catch (_: Exception)` 吞掉取消；取消路径直接 return 且**不清 `isSuggestionsLoading`**；远程建议整体限时 **2.5s**、单源 **2s** —— 弱网/代理下必然为空，而 UI 只在「建议非空」时才显示浮层 → 「搜索建议一直不展示」，且**不给任何原因** |
| S5 | `doSearch` 内的 per-async `catch → emptyList()` + `SubjectRepository.searchWithTotal` 的失败兜底 | **「网络失败」与「确实没有结果」被混为一谈**：远程全挂时 `error` 恒为 null，UI 显示「未找到相关作品」。这就是「搜索完全没内容」的观感来源 —— **根因级设计缺陷** |
| S6 | `BangumiClient.okHttpClient` | `authInterceptor` **只挂在 `authHttpClient`**，主 client 从不带 token → 已登录用户走 `nsfw=true` 的 v0 检索永远 401/被拒，只能退到旧版接口 |
| S7 | `FindSubjectsRepository` | 发未定义的 `filter.series`（见 §6.2） |
| S8 | `setType` | 搜索态下用 `filterByType(allResults, type)` 客户端过滤；而 `doSearch` 又用 `selectedType` 只查该类型 → 结果随「先选类型还是先输入」漂移 |
| S9 | `NirikoNavHost` 的 `subject_search` 路由 | 工厂**没传 `subjectDao`** → 这条入口的本地建议/Steam 补充能力比发现页弱（同一功能两套能力） |

### 7.2 修法

1. **单一「活跃查询」来源（F1）**：`uiState.query` 与 `queryInput` 二选一。
   建议：把输入同时写回 `uiState.query`（保留 `queryInput` 供防抖流），
   所有 setter 统一走 `activeQuery()`；补一条「改筛选必须重查」的单测。
2. **取消与失败分开（F2）**：先 `catch (CancellationException) { throw }`，再 `catch (Exception)`；
   per-async 只记录失败，**全部失败**才置 `error`（部分成功照常出结果）。
3. **结果优先于建议（F3）**：建议改成搜索框下方的 **overlay**（或结果列表首行），
   与结果不再互斥；`isSuggestionsLoading` 不再阻断结果渲染；结果分支只依赖 `results`。
4. **建议链路加固（F4）**：
   - `finally` 清除 loading；把「未开启 / 无输入 / 无命中 / 网络失败」四态区分开并在 UI 显示可诊断文案；
   - **与正式搜索共用一次请求**：建议（120ms）与搜索（300ms）打的是同一端点同一关键词，
     用既有的 `AsyncSingleFlight` 合并为一次请求，建议直接复用搜索结果的前 5 条 ——
     从根本上消除「建议因为 2s 超时而永远为空」；
   - 本地前缀/拼音命中继续即时展示（已有）。
5. **失败要说话（F5）**：
   - `DataSourceChain.searchWithTotal` 记录每个插件的失败；全部插件失败时**抛出**（或返回 `Result`），
     `SubjectRepository` 向上传达；`doSearch` 显示「网络失败 + 重试」，而不是「未找到」；
   - 本地兜底结果**标注**为「离线结果」。
6. **旧版 GET 搜索变成通用兜底（F5b）**：现在只在 `nsfw=true` 时用 `GET /search/subject/{keywords}`。
   若 §4.3 诊断确认「POST body 被吞/被拒」，则把「keyword 非空 + POST 失败或返回空」时的
   legacy GET 兜底**对所有搜索生效**（代码已存在，只是放宽触发条件）。
7. **token 挂对 client（F6）**：把 `authInterceptor` 加到主 client（或按域白名单注入），
   让 `nsfw=true` 的 v0 检索真正可用。
8. **删未定义字段（F7）**：见 §6.2。
9. **统一依赖注入（F8）**：`subject_search` 路由补 `subjectDao`。
10. **加诊断（F9）**：`§4.3` 的「搜索链路自检」入口（设置 → 数据源页，或搜索空态的「诊断」按钮）。

### 7.3 验收 + 单测

- 真机：输入→结果/建议都能出；断网时**明确显示网络失败**而不是「未找到」；
  改排序/筛选/R18 会重查；建议与结果同时可见；
- 单测（新增）：`activeQuery` 语义、取消不写状态、部分失败仍出结果、全失败置 error、
  建议合并去重的纯函数、`conditionSummary`/`BrowseFilter` 映射。

---

## 8. 问题 7 · 设置页对齐 Kazumi（2026-09-24 版）

### 8.1 Kazumi 现在的做法（已读源码）

| 层 | 组件 | 规范 |
|---|---|---|
| 入口页 | `settings_page.dart` + `ContentSection.group` + `SettingsCategoryTile` | 分组标题（`SectionHeader`：`titleSmall` + `primary` + w600）+ 分类行（**圆形图标底** `secondaryContainer` 36dp + `bodyLarge` 标题 + `bodySmall` 描述 + **chevron 图标**） |
| 二级页 | `SettingsDetailScaffold` | 内嵌时用 `AppBar(toolbarHeight: 64, titleTextStyle: headlineSmall, 透明背景)`；非内嵌用 `SysAppBar` + 返回箭头 |
| 二级页内容 | `SettingsList` → `SettingsSection(title, tiles)` | 分组标题 + `SplitListGroup` |
| 行 | `SettingsTile`（plain/switch/radio）/ `SettingsSliderTile` | 行内：leading 图标 24（`onSurfaceVariant`）+ `bodyLarge` 标题 + `bodySmall` 描述 + value（`bodyMedium` 次要色）+ trailing 图标 / Switch / Radio；`SettingsSliderTile` 带**数值徽标**（`secondaryContainer` 圆角 8） |
| 分组容器 | `SplitListGroup` + `SplitListRow` | 行间距 4dp，组两端圆角 24、组内圆角 4；**按下时该行圆角 morph 到 24**（替代分割线）；行底 `surfaceContainerLow`，按压感由 `AnimatedContainer` + `easeInOutCubic` 250ms |
| 宽屏 | `LayoutBreakpoint.compact` 判断 → 左侧 280dp 导航 rail + 右侧详情 | 手机竖屏用入口页 + push |

### 8.2 我们现在的差距

| 项 | 现状 | 差距 |
|---|---|---|
| 入口页分类行 | 圆角方底 12dp + `surfaceContainerHighest` + 文本「›」 | 应为**圆底 `secondaryContainer` + chevron 图标** |
| 二级页头 | 自绘 `Row(IconButton + titleLarge)` | 应为 `toolbarHeight 64` + `headlineSmall` 的 AppBar 形态 |
| 分组标题 | `labelLarge` + `primary` | 应为 `titleSmall` + `primary` + **w600**（并支持 description） |
| 分组容器 | `appleGlassCard` + hairline divider | Kazumi 是**扁平 tonal 分组 + 按压圆角 morph + 4dp 行间距**（无分割线） |
| 行解剖 | SwitchRow（可选 icon，调用方都没传）/ PickerRow（无 icon、无描述）/ InfoRow / ActionRow | 统一为「leading icon + 标题 + 描述 + value + 尾部控件」，且**调用方补齐图标与描述** |
| 数值型设置 | 无 | 缺 `SettingsSliderTile`（壁纸柔化/玻璃强度/卡片玻璃等） |
| 就地单选 | 一律弹 `SingleChoiceDialog` | Kazumi 用**就地 radio 行**（如主题模式、玻璃等级） |
| 覆盖度 | 7 个页面 + `ThemePackSection` + `GrayChannelSettingsSection`，风格不完全一致 | 统一到同一套组件 |

### 8.3 改造清单（组件级，一次改完，页面只改调用）

1. `SettingsDetailScaffold`：换成 `TopAppBar` 形态（`headlineSmall` 标题、64dp、透明、返回箭头）。
2. `SettingsGroupTitle` → 对齐 `SectionHeader`（`titleSmall`/w600/primary，可选 description，16–20dp 内边距）。
3. `SettingsSplitGroup` → 对齐 `SplitListGroup`：行距 4dp、组外圆角（`shapes.large`，约 20–24dp）、
   组内 4dp、**按压圆角 morph**（`animateDpAsState` + `Surface`/`graphicsLayer`）、行底选择（见 §12）。
4. 行组件统一：
   - `SettingsSwitchRow`：必填 leading icon、标题 `bodyLarge`、描述 `bodySmall`；
   - `SettingsPickerRow`：加 icon/描述，右侧 value + chevron 图标；
   - `SettingsInfoRow`：加 icon；
   - `SettingsActionRow`/`SettingsIconActionRow`：合并为一种；
   - **新增** `SettingsSliderRow`（value 徽标）、`SettingsRadioRow`（就地单选）。
5. 二级页统一 scaffold + 组件；入口页分类行改圆底 + chevron 图标。
6. （可选，暂缓）平板/折叠屏 rail 布局：Kazumi 有，手机端收益低，本轮**不做**，只记录。

### 8.4 验收

- 7 个二级页 + 2 个 section 全部使用同一套组件，视觉一致；
- 入口页/二级页与 Kazumi 的层级、字号、间距、按压反馈一致（截图对照）；
- 不改变任何设置项的行为与持久化（纯 UI 改造）。

---

## 9. 实施批次（建议顺序与依赖）

| 批次 | 内容 | 依赖 | 门槛 |
|---|---|---|---|
| **B0 基线** | 先跑一次 `gradlew assembleDebug` + `testDebugUnitTest`，确认起点是绿的（第 5 轮记录 322 个用例） | — | 全绿 |
| **B1 搜索链路（P0）** | §7 的 F1–F9、§4 的 R1–R6（搜索与榜单是同一批状态机，必须一起动）；新增 `搜索链路自检` 诊断 | B0 | 全绿 + 真机诊断报告 |
| **B2 发现页重做** | §3 `DiscoveryFeed`（全部排序 + 当季热门选取共用）→ §2 当季热门重做 → §6 找条目并入历史排名 → §5 删除评分月刊（含 DB v29）→ §2.4 类型筛选去重 | B1 | 全绿 + DB 升级真机验证 |
| **B3 设置页对齐** | §8 组件层 + 页面层 | 可与 B2 并行（无文件冲突，除 `NirikoNavHost` 的注入） | 全绿 + 截图对照 |
| **B4 收尾** | README / `docs/` 更新、真机清单、单项回滚说明 | B1–B3 | 全绿 |

> 说明：B1 与 B2 都动 `SubjectSearchViewModel`，因此**必须串行**；B3 只动 `ui/settings/**`，可并行。

---

## 10. 新增/修改单测清单

| 文件 | 覆盖 |
|---|---|
| `data/discover/DiscoveryFeedTest`（新） | 候选池去重、类内归一、质量门槛、**多样性重排**（窗口上限/连续上限/小类保底）、不填充、空候选 |
| `data/seasonal/SeasonalTrendingCalculatorTest`（改） | 保留在播判定/进度/合并；新增「取前 N + 人气序 + 多样性」与「不再全量」的回归 |
| `data/discover/BrowseFilterTest`（新，替代 `FindSubjectsFilterTest`） | 9 个维度 → 请求参数映射；**断言不产生 `series` 字段**；年份单边/跨年季度/状态三态/随机与名称本地排序 |
| `viewmodel/SearchStateTest`（新） | `activeQuery` 语义、取消不写状态、部分失败仍出结果、全失败置 error |
| `data/search/SuggestionMergeTest`（新） | 建议合并去重、四态区分（未开启/无输入/无命中/失败） |
| `data/remote/bangumi/SearchFallbackTest`（新） | POST 失败/空 → legacy GET 兜底触发条件（含 nsfw 与普通两种） |
| `data/local/Migration28To29Test`（可选，Robolectric） | v28→v29 后表被删除、旧数据完好 |

---

## 11. 风险与回滚

| 风险 | 影响 | 缓解 |
|---|---|---|
| DB v28→v29 | **不可逆**（老库升级即迁移） | 只 `DROP TABLE`，逐字核对生成的 `29.json`；真机验证升级路径 |
| 删除 `DiscoverViewModel` 等模块 | 编译面广 | 与 B2 同批完成，编译通过即验证；git 可回滚 |
| 网络事实未知（POST 是否被吞、`collection` 字段、`/p1/trending`、标签维度可用性、建议耗时） | 可能改错方向 | 全部收敛到「一次真机诊断」（§4.3/§7.6）；诊断先落地，再定 B1/B2 的最终形态 |
| 多样性参数（3/2/保底 3）主观 | 榜单口味 | 常量集中 + 单测固定行为，后续一改一处 |
| 设置页视觉改动大 | 用户观感 | 先出 1 个页面的对照截图确认风格，再铺开其余 6 页 |

---

## 12. 取舍决策（**已确认 2026 年**）

> 用户批复：**1 → A**，**2 → v1**，**3 → 保留增强**，**4 → B**。以下为最终口径，实施时按此执行。

- **①当季热门数据源 = A**：本季窗口（`/calendar` ∪ 放宽窗口检索）+ 类内热度候选 + 取前 30；**不接** `next.bgm.tv/p1/trending/subjects`（B 方案本轮不做，仅登记）。
- **②「全部」排序 = v1 规则版**：类内热度候选（`sort=heat`）→ `rating_count >= 100` 门槛 → 多样性重排（窗口内同类 ≤3、连续 ≤2、小类保底 3）→ 取 30。**不做**加权 DiscoveryScore（v1.5 登记不做）。
- **③找条目筛选器保留我们的增强**：Bangumi-master 的 9 个维度 + **评分区间 / 排名区间 / NSFW** 三项（收进「更多」）。
- **④设置页 = B**：采用 Kazumi 的版式与行解剖（分组入口页、`SettingsDetailScaffold` 的 AppBar 形态、分组标题、行内 leading 图标 + 标题 + 描述 + value + 尾部控件、按压圆角 morph、Slider/Radio 行），**保留 Niriko 的玻璃质感**（分组容器继续用 `appleGlassCard`，只在版式与解剖上对齐）。

### 原问题（保留备查）


1. **当季热门的数据源**：A 本季窗口 + 人气排序（稳，本轮默认）／B 接入 `next.bgm.tv/p1/trending/subjects`（真·趋势，需真机探测）／A+B（B 可用时优先）。
2. **「全部」排序**：v1 规则版（类内热度候选 + 质量门槛 + 多样性重排，可解释）／v1.5 加权 DiscoveryScore（需先确认能拿到收藏数，权重可调）。
3. **找条目筛选器里我们自己的增强**：评分区间 / 排名区间 / NSFW 三项——保留（放「更多」里）还是严格只留 Bangumi-master 的 9 个维度？
4. **设置页的表面处理**：A 严格照 Kazumi（扁平 tonal 分组、去掉设置页玻璃）／B 采用 Kazumi 的版式与行解剖，但保留 Niriko 的玻璃质感。

---

## 13. 本轮**不做**的事（明确范围）

- 不新增功能模块、不接入新数据源（B 方案若启用也只作为可选源）；
- 不动详情页 / 统计页 / 同步 / 备份 / 数据源插件链的既有行为；
- 不补 WebDAV 新表、不做限流闸门、不做平板 rail 布局（均为既有遗留，登记在案）。


---

## 14. 实施记录（第 6 轮收尾）

> 本节由集成收尾（t10）在 B0–B3 全部批次与验证/评审通过后补写：批次落地情况、一致性核对结论、
> 残留与死代码核对结论、构建与单测结果、未做项，以及真机验证清单。

### 14.1 批次落地

| 批次 | 状态 | 落地内容 |
|---|---|---|
| **B0 基线** | 完成 | 起点全绿（第 5 轮结束时 322 个用例；任务书 B0 记为 345） |
| **B1 搜索链路** | 完成 | §7 的 F1–F9 + §4 的 R1–R6；新增「搜索链路自检」（搜索空态入口，刻意不放设置页） |
| **B2 发现页重做** | 完成 | §3 `DiscoveryFeed` → §2 当季热门重做 → §6 找条目并入历史排名 → §5 删除评分月刊（DB v29）→ §2.4 类型筛选去重 |
| **B3 设置页对齐** | 完成 | §8 的组件层 + 页面层（取舍 ④B：Kazumi 版式 + 保留 Niriko 玻璃） |
| **B4 收尾** | 完成 | 本节的集成核对 + README / 本轮文档更新 + 真机清单 + 最终构建复跑 |

### 14.2 一致性核对（编译期与运行期入口）

**注入（NirikoApplication）** —— 自洽。
`ratingMonthlyRepository` 已随模块移除，全仓 0 引用；应用层仍提供
`subjectRepository` / `collectionRepository` / `searchHistoryDao` / `steamRepository` /
`subjectDao` / `settingsDataStore` / `refreshCoordinator` / `seasonalTrendingRepository` 这 8 个依赖
（另加 `database`），恰好覆盖两个搜索入口所需的全部构造参数。

**路由与 ViewModel 工厂** —— 两处入口参数完全一致，无「同一功能两套能力」。
- `MainPager` 的 Discover 页：`SubjectSearchViewModelFactory(subjectRepository, collectionRepository,
  searchHistoryDao, steamRepository, subjectDao, settingsDataStore, refreshCoordinator, seasonalTrendingRepository)`；
- `NirikoNavHost` 的 `subject_search` 路由：同一组 8 个具名参数（F8 在此补上 `subjectDao`）；
- 工厂签名（`SubjectSearchViewModel.kt:2220`）8 个参数，后 4 个可空，一一对应；
- `SubjectSearchScreen` 的形参表里已无 `discoverViewModel`（该处只剩一行说明性注释），
  `MainPager` 的调用也不再多传；
- 路由集合自洽：`main` / `subject_search` / `subject_detail/{id}` / `staff_list/{id}` /
  `episode_detail/{subjectId}/{epId}` / `character_detail/{id}` / `person_detail/{id}` /
  `bilibili_sync` / `steam_sync` + 7 个设置二级路由，全部有对应 `composable`；
  被删模块没有留下悬空路由或悬空形参。

**DB version 与实体清单** —— 等价。
- `@Database(entities = 18, version = 29, exportSchema = true)`；
- `addMigrations` 注册 `MIGRATION_2_3 … MIGRATION_28_29` 共 27 个，**无缺环**（含本轮新增的 `MIGRATION_28_29`）；
- 构建产物 `app/schemas/.../29.json`：**18 张表**，不含 `rating_snapshots`；
  `28.json` 为 19 张（含该表）；除该表外其余 18 张表的 `createSql` **逐字相同**；
- `TrendingMode` 只剩 `SEASONAL / ALL_TIME / STEAM`，`TREND_MODES` 三个；
  消费点两处自洽：顶栏 `SearchToolbar.kt:93` 与宫格 `DiscoverGridPane.kt:173-175`（恰 3 格）。

**备份 / 同步** —— 不引用被删表。
`BackupManager` 与 `SyncManager` 对 `rating_snapshot` / `monthly` / `find` 的引用 = **0**；
备份只覆盖 subjects / collections（+ 用户侧数据表）与 DataStore 设置项，同步只覆盖
collections / workItems / history / subjects —— 该表从未进入这两条通路，删除不影响用户数据。

### 14.3 残留与死代码核对（app/src 全量搜索）

| 搜索项 | 命中 | 结论 |
|---|---|---|
| `TrendingMode.MONTHLY` / `TrendingMode.FIND` / `RatingSnapshot` / `RatingMonthly` / `ratingMonthly` / `MonthlyPane` / `DiscoverEntriesPane` / `BROWSE_MODES` / `FindSubjectsFilter` | **0** | 已彻底删除 |
| `rating_snapshots` | 5（全在 `NirikoDatabase.kt`） | 均为历史迁移 DDL 与注释：`MIGRATION_27_28` 的 CREATE、`MIGRATION_28_29` 的 DROP、迁移 KDoc —— 按规格 §13 可保留 |
| `DiscoverViewModel` | 1（`MainPager.kt:93`） | 仅注释「已整体删除」 |
| `FindSubjects` | 2（`BrowseFilter.kt:16`、`SearchState.kt:80`） | 仅注释说明「已被删除 / 能力并入 BrowseFilter」 |
| `MONTHLY` / `FIND` | 各 2（`TrendingMode.kt:6`、`TrendingSection.kt:181`） | 仅 KDoc / 注释（记录第 4–6 轮沿革） |
| `isTrend` | 1（`TrendingSection.kt:180`） | 仅注释（记录 R6 删掉了该早退） |
| `filter.series` / `series` | `BrowseFilter.kt`（KDoc 说明官方无此字段且本轮删除）、`BrowseFilterTest.kt`（6 处**断言不存在**）、`SearchChainProbe.kt`（注释） | 无任何代码仍在发送 `series`；`NirikoDatabase.kt:160` 的 `ALTER TABLE subjects ADD COLUMN series` 是 subjects 表的「系列」列（本地字段，与 v0 filter 无关），保留 |
| 被删文件是否仍在磁盘 | `RatingMonthly*` / `RatingSnapshot*` / `DiscoverViewModel*` / `FindSubjects*` / `MonthlyPane*` / `DiscoverEntriesPane*` / `SettingsSection.kt` 全部 **0 命中** | `data/discover/` 只剩 `DiscoveryFeed.kt` + `BrowseFilter.kt` |

结论：**无残留引用、无孤儿代码、无未使用的死文件**。

### 14.4 最终构建与单测

在收尾时以**全量重跑**（`--rerun-tasks`）替代增量复跑，确保当前工作树被真正编译与执行：

```
.gradlew.bat assembleDebug testDebugUnitTest --rerun-tasks --console=plain
→ BUILD SUCCESSFUL in 4m 58s
→ 47 actionable tasks: 47 executed   （含 :app:compileDebugKotlin、:app:packageDebug、:app:testDebugUnitTest）
```

| 项 | 结果 |
|---|---|
| `assembleDebug` | 成功；产物 `app/build/outputs/apk/debug/app-debug.apk`（28,782,504 B，08:13:27） |
| `testDebugUnitTest` | 成功；`app/build/test-results/testDebugUnitTest/*.xml` = **50 套件 / 440 用例 / 0 失败 / 0 错误 / 0 跳过**（08:13:19） |
| 产物新鲜度 | `app/src` 下**没有任何**文件晚于 APK 与测试结果 → 当前树被这次构建完整覆盖 |
| 日志 | 仓库根目录 `verify-round6.log`（本次全量重跑的输出，含 `BUILD SUCCESSFUL`） |

**用例数变化**：第 5 轮 322（本轮任务书 B0 记为 345）→ **440**（+95）。新增 / 重写套件见 §10 与 README 的
第 6 轮小节：`DiscoveryFeedTest` 19、`BrowseFilterTest` 32（替代 `FindSubjectsFilterTest`）、
`SearchStateTest` 17、`SuggestionMergeTest` 10、`SearchFallbackTest` 19、
`SearchChainProbeTest` 11、`SeasonalTrendingCalculatorTest` 25。

**非阻塞观察**：本次全量重跑产物 28,782,504 B，与之前一次增量构建的 30,309,481 B 不同
（debug 未开混淆；差异只可能来自资源合并 / dex 打包在增量与全量两条路径下的产物差异），未进一步定位。

### 14.5 未做项与已知残余

- **§13 范围全部遵守**：未新增功能模块、未接新数据源（B 方案 `next.bgm.tv/p1/trending/subjects` 仅登记）、
  未动详情页 / 统计页 / 同步 / 备份 / 数据源插件链的既有行为、未补 WebDAV 新表、未做限流闸门、
  未做平板 rail 布局。
- **v1.5 加权 `DiscoveryScore`**：不做（需先确认真能拿到收藏数），本轮只留了 `normalizedPosition` 与
  `Params` 作为接口形状。
- **§10 中标记「可选」的 `data/local/Migration28To29Test`（Robolectric）未写**：本仓约定不新增
  Robolectric / mockito / coroutines-test 依赖，与「迁移测试未补」这条既有待办一致
  → DB v28→v29 的等价性目前是**静态核对**（29.json vs MIGRATION_28_29）而非自动化测试，
  必须在真机首次开库时确认（见 §14.6 第 1 项）。
- **已知残余（low，沿用 B1 收尾结论，本轮不动）**：`SubjectSearchViewModel` 分页路径两处
  `catch (_: Exception) { isLoadingMore = false }`（`loadNextTrendingPage` / `loadMoreAllTypes`）——
  最坏后果只是「加载更多」指示器被提前复位。
- **测试形式限制（勿误读为已覆盖）**：本项目 JVM 单测无法实例化 `SubjectSearchViewModel`
  （`viewModelScope` 需要 Android 主线程），因此 `SearchStateTest` 驱动的是 `setType` 自己的取词入口
  `typeSwitchPlan` + 反向锁，而不是 `setType` 方法本体；那一行 `_uiState.update` 没有方法级测试。
- **集成期新发现（非阻塞，未改代码）**：`ui/settings/SettingsScreen.kt:74` 的外观分类副标题仍写着
  「主题配色、壁纸、开屏与图标」，但「开屏」UI 已在第 5 轮整体删除
  （`AppearanceSettingsScreen.kt:173` 有对应注释）—— 纯文案与现状不符，属设置页范围，
  本轮按「不越界改业务逻辑」未动，留给下一轮清理。

### 14.6 真机验证清单（发布前必跑）

> 本清单里的 5 项是**只有真机才能确认**的（沙箱/CI 无法覆盖）。前 4 项与「网络事实未知」这条风险直接相关，
> 第 3 项（DB 升级）是本轮**唯一不可逆**的改动。

| # | 项 | 步骤 | 通过标准 |
|---|---|---|---|
| 1 | **DB v28→v29 升级（不可逆）** | 装第 5 轮版本（DB v28）→ 造若干收藏 / 评分 / 卷进度 → 覆盖安装本轮 APK → 冷启动 | 正常进入首页；**不出现** Room 的 `Migration didn't properly handle` / `Room cannot verify the data integrity` 崩溃；收藏 / 评分 / 进度完好；库中 `rating_snapshots` 表已消失 |
| 2 | **POST 是否被吞** | 搜索页空态点「诊断搜索链路」 | 看 `POST /v0/search/subjects` 那一行的 HTTP 状态、耗时与返回条数；与 `GET /search/subject/巨人` 对比，决定是否长期依赖 legacy 兜底 |
| 3 | **token 生效** | 设置 → 数据源与账号 → 完成 Bangumi OAuth 登录 → 打开 NSFW/R18 检索 | 诊断页的 POST 行不再是 401/403；R18 检索能返回结果（未登录时走旧版兜底仍可用） |
| 4 | **legacy 兜底实际触发** | 反代 / 弱网环境下搜一个有结果的词（如「巨人」） | 结果能出；诊断页可见 POST 失败或返回 0 条而 legacy GET 有返回 |
| 5 | **设置页视觉对照** | 逐个点开 7 个二级页，对照 Kazumi 2026-09-24 版 | 入口页圆形 `secondaryContainer` 图标底 + chevron 图标；二级页 64dp 透明 TopAppBar + `headlineSmall`；分组标题 `titleSmall`/primary/w600；行距 4dp；按压时该行圆角 morph；Slider 有数值徽标；主题模式 / 玻璃等级是就地 radio；**玻璃容器仍在**（不是扁平 tonal） |
| 6 | 当季热门语义 | 进入发现页「当季热门」 | 头部显示「本季人气 · 展示前 30 / 候选 N 部」；条数 ≤ 30；不出现同类型连续刷屏；候选够时三次元 / 音乐 / 书籍各有保底；切排序 / 类型**零请求** |
| 7 | 历史排名 | 刷新历史排名页；断网再刷；改筛选 | 有内容；断网显示「加载失败 + 重试」而**不是**空白或「输入关键词搜索作品」；改筛选后榜单变化（不吃旧缓存） |
| 8 | 搜索与建议 | 输入过程中观察；弱网下观察 | 建议与结果**同时可见**；建议加载中不阻断结果；弱网下不再出现「建议永远为空」；全失败时显示「网络失败 + 重试」而不是「未找到」 |
| 9 | 删除彻底性 | 观察发现页宫格 / 顶栏；看「找条目」「评分月刊」入口 | 宫格 3 格、顶栏 3 个入口；无残留入口；请求体不含 `series`（可经诊断页抓包确认） |

### 14.7 单项回滚说明

- **DB v29**：不可逆。回滚 APK 不会把表建回来（`rating_snapshots` 已 DROP）；
  若要回到 v28 的代码，需要用户在旧版上重新建库，或补一条 `29→28` 的 CREATE 迁移。
- **发现页 / 搜索 / 设置页**：全是纯代码改动，`git revert` 即回滚，无数据迁移。
- **设置页改造**：键名 / 默认值 / 回调语义零变更，回滚不影响已保存的设置项与主题包。
