# 批次 C 存储决策（先定，再动手）

> 结论：**保留范围内的 27 条新功能，全部不需要 Room 迁移**。上一版计划里的「v33 一次加 6 张表」是 v1 目录（4.6/4.16/5.9/5.15/5.17/6.17）的结论，而那 6 条已被你从范围里删掉。

| 功能 | 需要持久化吗 | 存哪 |
| --- | --- | --- |
| 今晚看什么 / 接下来七天 / 漏看 | 否 | 纯计算（放送数据 + 本地进度） |
| 收藏健康度 | 否 | 纯计算 |
| 一句话对比 / 口径说明 | 否 | 纯计算 / 静态文案 |
| 日历 .ics 导出 | 否 | 现算现导出 |
| 停播 / 延期角标 | 否 | 由 onair + episodes.airDate 推导 |
| 标题 logo | 否 | TMDb images 响应（已有） |
| 区块折叠 | 是（小） | DataStore：`detail_collapsed_v1`（JSON map，按 subjectId） |
| 评分对照卡 / 分享卡 | 否 | 现算现渲染 |
| 榜单 ▲▼ 箭头 | 是（小） | DataStore：`ranking_snapshot_v1`（每周一次，JSON，≤200 条） |
| 同步预览与演练 | 否 | 复用 `BangumiSyncPlan` |
| 数据体检 | 否 | 只读检查 |
| 本地快照 | 是（文件） | app 私有目录 `snapshots/*.json.gz`，**不进 Room** |
| 图片占用清理 | 否 | 扫 disk cache |
| 设置搜索 | 否 | 纯本地过滤 |
| 深色对比度 | 否 | theme token |
| 玻璃自动档 | 是（小） | 已有 `AppSettings` 加一个枚举键 |
| 数据源自检 | 否 | 复用 probe |
| 应用内更新 | 否 | 查 Release |
| 英文界面 | 是（大） | `values-en/strings.xml`，**本轮不做**（L，单独立项） |

## 迁移结论

- **不新增表、不改列**；`NirikoDatabase` 保持 v32。
- 新增 DataStore 键最多 3 个：`detail_collapsed_v1`、`ranking_snapshot_v1`、`glass_auto_tier`；都在 `SettingsDataStore` 里，随 JSON 备份自动带走（需要各加一行序列化）。
- 备份 `EXPORT_VERSION` 保持 v5（新增 DataStore 键属于「附加字段」，缺键走默认）。

## 所以「v33 窗口」取消

批次 C 可以直接开工，不需要数据库窗口，也不需要停机迁移。若后续做「英文界面」或「离线元数据快照」再单独评估存储。