# Niriko · DSH 手机端协作须知

作者 perchA5uka。Android 应用：Kotlin + Jetpack Compose + Room（23 表，v32）。
仓库：https://github.com/perchA5uka/Niriko
手机端构建与验证的完整流程见 **docs/mobile-dev-flow.md**（遇到流程问题先读它，不要猜）。

## 0. 位置与边界

- 仓库在本机：`/root/workspace/Niriko`。当前目录不是它时，先 `cd /root/workspace/Niriko` 再动手。
- 本机是 Android 手机（Android 17 / SDK 37 / arm64），不是桌面 Linux。
- **本地可以编译，但要先用 `bash scripts/mobile/local-build-setup.sh` 配好 qemu 转译的 aapt2**（见流程文档 §6；已实测 `assembleDebug` 约 6 分钟）。默认仍推荐「推送后等 CI」，本地构建用于需要快速反复验证时。
- 产出文件（APK、日志、截图）放 `/sdcard/Download/DSH-Go/`；**不要**把产物、密钥、临时文件提交进仓库。

## 1. 验证顺序（改完代码必须走一遍）

1. 补/更新 JVM 单测：`app/src/test/...`（README「开发约定」要求新增逻辑必须带单测）。
2. 静态自查：`git diff`、`git status`，确认没有夹带无关文件。
3. 推送后等 GitHub Actions 绿（`scripts/mobile/ci.sh watch`）；需要快速反复时也可先本地 `./gradlew :app:assembleDebug`（环境见流程文档 §6）。**CI 绿仍是合并前的最终门槛**。
4. 装包到本机肉眼验证（`scripts/mobile/ci.sh apk`，然后安装）；能用手机工具截图就截图判断。
5. 数据库迁移、签名、发布包这类只在真机/发布链路才暴露的问题，必须按流程文档 §5 单独确认。

## 2. 提交与分支

- 中文 conventional commit：`feat(ui): …`、`fix(sync): …`、`docs: …`、`test: …`、`chore: …`。一个改动一个提交，正文写「为什么改 + 怎么验证的」。
- 小改动直接推 `main`；跨多文件、动数据库、改公共组件这类走 `feat/xxx` 分支 + PR，CI 绿再合。
- 禁止 `git push --force` 到 `main`；`main` 上历史即发布史（tag v1.0.0…v1.2.1）。
- 只改了文档/说明时，commit message 里加 `[skip ci]` 省一次 6 分钟构建。

## 3. 代码约定（细节以 README「开发约定」为准）

- 无 DI 框架：依赖在 `NirikoApplication` 装配，ViewModel 用手写 Factory。
- 纯函数优先：统计 / 排序 / 匹配 / 在播判定等逻辑抽成不依赖 Android 的函数。
- KDoc 用「」而不是反引号。
- Room 迁移的 DDL 必须与 `app/schemas` 导出的 `createSql` 逐字一致；`app/schemas/` 随仓库提交。
- 引入新第三方依赖前先问用户。

## 3.5 新功能前置调研（用户要求，2026-11-08）

- 写任何新功能之前，**先查有没有现成库/现成实现**：Maven Central（search.maven.org 检索接口）、GitHub（按 star 排序搜 Kotlin/Compose）、AndroidX 官方组件。
- 调研结论写进对应的 docs 计划文件：候选清单、适用性、维护状态（最后发布年份/是否有 Compose 版本）、为什么用或不用。没有可用库时写明「自研」的具体理由与工作量。
- 只搜一两个关键词就下结论不算数；至少覆盖「功能名 + Compose / Android / Kotlin」三类关键词。

## 4. 读代码的姿势（手机上下文预算有限）

- `README.md`（约 50KB）和 `docs/` 下的大文档**不要整篇读**：先 grep/rg 定位，再只读命中的片段。
- 713 个 Kotlin 文件，先按文件名/符号搜索缩小范围；跨模块的大范围探索交给 subagent，别把整文件贴进对话。
- 改完用 `git diff` 自查，别复述整个文件。

## 5. 真机验证

- 手机就是目标设备，已装 `com.otakup.niriko`。装新包后用 `mcp__phone__*` 操作与截图（截图能直接读进来判断）。
- **装包统一用 `ninstall`**（= `bash scripts/mobile/install-apk.sh`，参数是 APK 路径，默认取 `/sdcard/Download/DSH-Go/apk` 下最新包）。签名一致直接覆盖；不一致时它自己走「备份 → 卸载 → 安装 → 恢复数据」，不会丢收藏。
- 签名已经收敛：仓库密钥由 GitHub Secrets（`NIRIKO_KEYSTORE_BASE64` 等四个）提供给 CI，本机 `keystore.properties` 指向同一把钥匙。所以 **CI 的 `--release` 包与手机已装包同签名**，日常迭代不需要再审一遍签名。
- 不要再用裸 `adb install -r` 装包：这台小米 HyperOS 会直接回 `INSTALL_FAILED_USER_RESTRICTED: Invalid apk`（不是包的问题）。`ninstall` 已经改成「拷到 /data/local/tmp → root `pm install`」，绕开这个限制。
- 签名变更（换钥匙）时 `ninstall` 会自动备份并恢复数据；恢复后按**新 uid** chown、按新装目录的 categories 递归 chcon —— 这两步缺一个，应用启动就会报 SQLite `Permission denied`。
