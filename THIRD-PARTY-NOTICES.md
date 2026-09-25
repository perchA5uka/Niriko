# 第三方组件与许可

Niriko 本体以 **MIT License** 发布（见 [`LICENSE`](LICENSE)）。下面列出随应用一起分发的第三方组件、
内嵌源码与数据来源。分发本项目的衍生版本时，请一并保留这些声明。

## 运行时依赖

| 组件 | 许可 |
| --- | --- |
| Jetpack Compose / Material 3 / Navigation Compose / Compose BOM（AndroidX） | Apache-2.0 |
| Room（AndroidX） | Apache-2.0 |
| DataStore Preferences（AndroidX） | Apache-2.0 |
| WorkManager（AndroidX） | Apache-2.0 |
| core-splashscreen（AndroidX） | Apache-2.0 |
| Media3 ExoPlayer / Media3 UI（AndroidX） | Apache-2.0 |
| Coil（io.coil-kt） | Apache-2.0 |
| Retrofit / OkHttp（Square） | Apache-2.0 |
| kotlinx.serialization（JetBrains） | Apache-2.0 |
| miuix-blur（top.yukonga.miuix.kmp） | Apache-2.0 |
| backdrop（io.github.kyant0） | Apache-2.0 |
| material-color-utilities（com.materialkolor，Google material-color-utilities 的 Kotlin 移植） | Apache-2.0 |
| pinyin4j（com.belerweb） | BSD |

仅测试期依赖（不进入 APK）：

| 组件 | 许可 |
| --- | --- |
| JUnit 4 | Eclipse Public License 1.0 |

## 内嵌的第三方源码

- `app/src/main/res/raw/liquid_glass_effect.agsl` —— 移植自
  [AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView)（作者 QmDeve），**MIT License**；
  版权声明已保留在该文件头部，按 MIT 要求不得移除。
- `app/src/main/java/com/otakup/niriko/ui/components/liquidglass/` 内的着色器封装（模糊缓存与
  RenderEffect 策略）参考了同一上游实现。

## 数据来源

本应用只做个人收藏管理，不缓存、不转售任何第三方数据。

| 来源 | 用途 | 备注 |
| --- | --- | --- |
| Bangumi 番组计划（`api.bgm.tv`） | 主数据源：条目、剧集、人物、角色、每日放送、条件检索 | 版权归 Bangumi 及各权利方 |
| AniList | 补充信息与独立条目兜底 | 无需密钥 |
| VNDB（REST API v2） | 视觉小说信息 | 无需密钥 |
| Steam 商店 / Web API | 游戏商业数据与游戏库导入 | 版权归 Valve 及各发行商 |
| TMDb | 剧照与权威评分 | 本产品使用 TMDb API，但未获得 TMDb 的认可或认证 |
| OMDb | IMDb 逐集评分（需用户自备密钥） | 内容按 **CC BY-NC 4.0** 提供，**不可用于商业用途** |
| Anitabi | 动画取景地标 | |
| 豆瓣 | 剧照（**灰通道，默认关闭**） | 豆瓣未授权第三方抓取，仅供个人查看；本仓库不含任何豆瓣数据。代码内置了社区公开的 frodo apikey（非豆瓣官方发布、可能随时失效）；分发衍生版本时请自行评估并替换或移除该通道 |

内置静态数据：

- `app/src/main/assets/data/onair/*.json` —— 每季放送时刻表，整理自公开的放送日历。
- `app/src/main/assets/data/bilibili_site_map.json` —— Bangumi 条目到 B 站番剧 id 的映射精简快照，
  来源 [bangumi-data](https://github.com/bangumi-data/bangumi-data)。

## 用户自备凭据

TMDb / OMDb / IGDB / RAWG / OpenCritic / Steam 的 API Key 以及 Bangumi OAuth 应用凭据
**全部由用户在应用内填写**（默认空值），仓库中不含任何可用密钥；凭据仅保存在应用私有目录。
