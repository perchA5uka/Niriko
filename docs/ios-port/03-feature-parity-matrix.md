# 功能双端对齐矩阵

> 目标：iOS 首版功能范围与 Android 对齐，并标注哪些功能只能平台各自实现。

## 图例

- ✅ Shared：在 `commonMain` 一份实现，双端共用
- 🔶 Platform：业务共享，但需要各自平台壳
- ⛔ Android-only：iOS 不支持或首版裁剪
- ❓ 待 Spike 确认

## 功能矩阵

| 功能 | Android | iOS | 说明 |
|---|---|---|---|
| 作品库（收藏/评分/进度/标签） | ✅ | ✅ | Shared |
| 发现页（当季/榜单/Steam 热销） | ✅ | ✅ | Shared |
| 统计页（日历/分布/年度总结） | ✅ | ✅ | Shared |
| 设置页（主题/默认排序/NSFW 等） | ✅ | ✅ | Shared |
| 作品详情/角色/人物/Staff | ✅ | ✅ | Shared |
| 全屏搜索/漫搜/搜索建议 | ✅ | ✅ | Shared |
| Bangumi 数据源 | ✅ | ✅ | Shared + Ktor |
| AniList 数据源 | ✅ | ✅ | Shared + Ktor |
| Steam 数据源 | ✅ | ✅ | Shared + Ktor |
| VNDB 数据源 | ✅ | ✅ | Shared + Ktor |
| Bilibili 评分/追番导入 | 🔶 | 🔶 | 流程 Shared，登录 WebView 平台壳 |
| Steam 账号/游戏库导入 | 🔶 | 🔶 | OpenID 登录 WebView 平台壳 |
| WebDAV 同步 | ✅ | ✅ | Shared |
| Bangumi 账号同步 | ✅ | ✅ | Shared |
| JSON 备份导出/导入 | 🔶 | 🔶 | 文件读写平台壳 |
| 主题色 / M3 主题 | ✅ | ✅ | Shared |
| 动态壁纸（图片） | ✅ | ✅ | Shared + 图片加载 |
| 动态壁纸（视频） | ✅ | ⛔/🔶 | Android Media3；iOS AVPlayer 或首版裁剪 |
| 开屏动画 | 🔶 | 🔶 | Android SplashScreen；iOS LaunchScreen + CMP 动画 |
| 液态玻璃/模糊 | ❓ | ❓ | miuix blur 是否可用需 Spike |
| 分享卡片 | 🔶 | 🔶 | 渲染可共享，分享动作平台壳 |
| .nirikotheme 主题包 | 🔶 | 🔶 | 文件选择/关联打开平台壳 |
| 动态桌面图标 | ✅ | ⛔/🔶 | iOS Alternate Icon 受限，首版建议裁剪 |
| Kazumi 收藏导入 | 🔶 | ⛔ | 依赖 Android 本地文件/目录，iOS 需文件导入改造或裁剪 |

## 首版建议裁剪项

1. 视频动态壁纸（保留图片壁纸）
2. 动态桌面图标自定义
3. Kazumi 导入（或改为用户手动选择 Kazumi 导出文件）
