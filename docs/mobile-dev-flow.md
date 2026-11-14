# Niriko 手机端开发流程（DSH Go）

> 目标：把原来「Windows + Android Studio + DSH」的日常开发搬到手机上，日常改动不再开电脑。
> 本文命令都在手机的 Ubuntu 24.04（proot，`/root/workspace`）里执行。
> 建立于 2026-11-08。仓库：https://github.com/perchA5uka/Niriko

---

## 1. 分工结论（先看这一节）

| 环节 | 在哪做 | 说明 |
| --- | --- | --- |
| 读代码 / 改代码 / 写文档 / 查资料 | **手机 DSH** | 仓库 `~/workspace/Niriko` |
| 编译 + JVM 单测 | **GitHub Actions** | `testDebugUnitTest` + `assembleDebug`，实测约 6~7 分钟一轮 |
| 打包产物 | **GitHub Actions artifact** | 每次 push 自动上传 `niriko-debug-apk`；配了签名 secrets 再多一个 `niriko-release-apk` |
| 装机验证 / 截图回归 | **手机** | 手机就是目标设备（Android 17 / SDK 37），装完直接看、直接截图给 AI |
| 发布签名 / 正式 Release | **电脑**（偶尔） | keystore 留在电脑；也可以让 CI 用 secrets 打同签名包 |
| 手机本地编译 | 可行（已实测） | 见 §6：用 qemu 转译官方 aapt2，`assembleDebug` 约 6 分钟。默认仍走 CI，本地用于快速反复验证 |

**为什么默认编译仍放云端**：Google 的 `aapt2` 只发布 x86_64 ELF（本机实测：`aapt2-8.13.2-linux.jar` 内是 `ELF 64-bit x86-64`），aarch64 直接跑会在 `processDebugResources` 失败。手机端已用 qemu-user 转译官方 aapt2 绕开（§6 已实测通过），但本地跑 Gradle 会占 2.5GB 内存 + 1.8GB 依赖缓存，构建时手机明显变慢；**日常以 CI 为准，本地构建只在需要连续快改时开**。

---

## 2. 一次性准备

### 2.1 已在本机配好（2026-11-08）

| 项 | 状态 |
| --- | --- |
| 仓库 | `/root/workspace/Niriko`（origin 默认 SSH） |
| git 身份 | `perchA5uka <297563809+perchA5uka@users.noreply.github.com>` |
| SSH 密钥 | `~/.ssh/id_ed25519_niriko` + `~/.ssh/config`（Host github.com） |
| 工具 | `gh` 2.103.3、`curl`、`jq`、`unzip`、`openssh-client`、`file` |
| 取包目录 | `/sdcard/Download/DSH-Go/apk/` |
| CI 助手 | `scripts/mobile/ci.sh`、`scripts/mobile/gh-login.sh` |

### 2.2 还需要你本人做的 3 件事

**① 把 SSH 公钥加到 GitHub**（唯一卡住推送的事，1 分钟）

打开 https://github.com/settings/ssh/new ，Title 随便写（如 `dsh-go phone`），Key 粘贴这一整行：

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPZU8P3puoYYtWYjPQ8SNDpQXx3LhaZ9LPktIbcOASut 297563809+perchA5uka@users.noreply.github.com (dsh-go phone)
```

加完在手机终端验证：`ssh -T git@github.com` 应回 `Hi perchA5uka! You've successfully authenticated...`

**② 让 gh 登录**（看 CI 日志、下 APK 需要）

```bash
cd ~/workspace/Niriko
bash scripts/mobile/gh-login.sh
```

脚本会给出 8 位设备码 → 手机浏览器打开 https://github.com/login/device 输入并授权 → 自动完成，不手粘 token。

**③ 给 CI 配发布签名 secrets（推荐，一次配好以后都能覆盖安装）**

不配也能用，只是只能装 debug 包；而手机上已装的是 release 签名包，debug 包签名不同装不上去（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。

在电脑上把 `keystore.properties` 对应内容转成 secrets：

```bash
# 电脑上执行
base64 -w0 release.jks        # 整行复制（Windows 用 certutil 或 PowerShell [Convert]::ToBase64String）
```

GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret，加 4 个：

| Secret 名 | 值 |
| --- | --- |
| `NIRIKO_KEYSTORE_BASE64` | 上面那行 base64 |
| `NIRIKO_KEYSTORE_PASSWORD` | `keystore.properties` 的 `storePassword` |
| `NIRIKO_KEY_ALIAS` | `keystore.properties` 的 `keyAlias` |
| `NIRIKO_KEY_PASSWORD` | `keystore.properties` 的 `keyPassword` |

配好后 CI 会多跑一次 `assembleRelease`，产物 artifact 名 `niriko-release-apk`，与正式包同签名，可直接覆盖手机上已装版本并保留数据。

---

## 3. 日常闭环（标准动作）

### 3.0 一轮的时间预期

| 环节 | 耗时 |
| --- | --- |
| 交代任务 + AI 改代码 + 补单测 | 5~20 分钟（看改动大小） |
| push → CI 绿 | 6~7 分钟（可同时干别的） |
| 下包 + 安装 | 1~2 分钟 |
| 真机验证 + 截图反馈 | 2~5 分钟 |
| **合计** | **约 15~30 分钟一轮**，与桌面差不多，思考/写代码时间占比更高 |

### 3.1 开一个会话

新会话默认目录是 `/root/workspace`，先让 AI 进仓库：

> 「进 Niriko 开始：<你的需求>」

AI 会 `cd ~/workspace/Niriko`，之后项目级 `AGENTS.md` 自动生效（提交规范、验证顺序都在里面）。

任务来源三种：口头描述 / 直接引用 `plans/NN-*.md` 的条目 / 贴 GitHub issue 或截图。**先让 AI 给 2~4 步执行计划再动手**，比一句话让它改到底好复查。

### 3.2 改代码 + 补单测

- 要求 AI 按 README「开发约定」：纯函数优先、KDoc 用「」、新增逻辑必须带 JVM 单测。
- 手机上不能编译，所以**改完先做静态自查**：`git status --short`、`git diff --stat`，确认没夹带无关文件。

### 3.3 提交

```bash
cd ~/workspace/Niriko
git status --short
git diff --stat
git add -A
git commit -m "fix(ui): 顶部控件横屏下的间距" -m "为什么：横屏时 dock 与指示器重叠。
怎么验证：CI 绿 + 本机截图对比。"
```

提交信息用中文 conventional commit（`feat/fix/docs/test/chore(scope): …`），与现有历史一致。

### 3.4 推送

```bash
git push              # 首次：git push -u origin main
```

- 推 `main` 会直接触发一次 CI。
- 推**功能分支**不会触发；要在 GitHub 上开 PR 才跑 CI（这也是分支改动的评审入口）。
- 改动大或动数据库时，先开分支（见 §4.1）。

### 3.5 等 CI

```bash
bash scripts/mobile/ci.sh status    # 最近 5 轮（不需要登录）
bash scripts/mobile/ci.sh watch     # 阻塞等最新一轮，默认 15 分钟超时
```

CI 通过会打印 ✓；失败会打印 run-id 并提示看日志。同一分支再推会取消上一轮（concurrency: cancel-in-progress），不必等旧轮跑完。

### 3.6 拿 APK

```bash
bash scripts/mobile/ci.sh apk              # debug 包 → /sdcard/Download/DSH-Go/apk/
bash scripts/mobile/ci.sh apk --release    # 同签名 release 包（需先配 §2.2 ③ 的 secrets）
```

### 3.7 装机验证

- 手机「文件管理」→ 下载 → DSH-Go → apk → 点 APK → 安装（首次需允许「安装未知应用」）。
- 也可以直接在会话里说「装这个包并打开」，AI 用手机操控工具走安装界面。
- 打开后按需要验证；把结果或截图丢回会话（AI 能读图），继续下一轮。

### 3.8 失败了怎么办

```bash
bash scripts/mobile/ci.sh logs      # 只打印失败步骤的日志
```

把关键报错贴给 AI，或让它自己 `logs` 拉取。常见根因：单测断言过时、Room schema 与迁移 DDL 不一致、新依赖版本冲突、`app/schemas` 没随迁移一起提交。

### 3.9 可选：本地快速验证（不推 CI 也能编译）

环境已装好（§6），连续改 UI 时用本地构建代替等 CI：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/root/android-sdk
cd ~/workspace/Niriko
./gradlew :app:assembleDebug --console=plain    # 第一次约 6 分钟，增量 1~2 分钟
```

产物仍是 `app/build/outputs/apk/debug/app-debug.apk`（debug 签名，装不上已装的 release 包）。
本地构建**不能替代** CI：合并前仍要等 Actions 绿（CI 环境干净、有单测报告）。

---

## 4. 分支、提交与发布

### 4.1 什么时候开分支

| 情况 | 做法 |
| --- | --- |
| 单文件小修、文案、注释 | 直接 `main` |
| 跨多文件 / 公共组件 / UI 大改 | `git switch -c feat/xxx` → 推 → GitHub 开 PR（**分支 push 本身不触发 CI，开 PR 才触发**；手机浏览器看 diff）→ 绿了合 |
| 动 Room 迁移 / 版本号 | 必须开分支 + PR，并真机升级验证（见 §5） |
| 只改文档 | 直接 `main`，commit message 里加 `[skip ci]` 省一轮构建 |

### 4.2 提交规范

中文 conventional commit，正文写「为什么改 + 怎么验证」。已发布的版本靠 tag（`v1.0.0`…`v1.2.1`），`main` 历史就是发布史。

### 4.3 正式发布（仍在电脑上，10 分钟）

0. 签名已收敛到仓库密钥：CI Secrets 里配了 `NIRIKO_KEYSTORE_BASE64` / `NIRIKO_KEYSTORE_PASSWORD` / `NIRIKO_KEY_ALIAS` / `NIRIKO_KEY_PASSWORD`，工作流会解出 keystore 并构建带签名的 `release` 包；本机 `keystore.properties` 指向同一把钥匙，本地 `assembleRelease` 产物与 CI 同签名。装包一律走 `ninstall`（见 §5）。
1. 电脑上 `gradlew assembleRelease` 出正式包，按 `docs/release-vX.Y.Z.md` 写发布说明。
2. 手机端打 tag 并推：`git tag v1.2.2 && git push origin v1.2.2`。
3. GitHub 网页建 Release，上传 APK（与以前一致）。
4. 可选优化：加一个 tag 触发的 workflow，让 CI 自动打包并创建 Release（依赖 §2.2 ③ 的 secrets，以后可做）。

---

## 5. 手机端真机验证清单

手机是 Android 17 / SDK 37 / 8 核，比模拟器可信，但有几点和桌面不同：

- **升级安装才会验迁移**：Room 迁移校验只在真机首次开库时执行。要测迁移，必须用**同签名**包覆盖安装（配了 secrets 的 `--release` 包），debug 包一装不上、二测不到迁移。
- **别随便卸载**：卸载会清掉本地数据库。要换签名重装前，先用应用内备份 / WebDAV 同步导出。
- **动画/玻璃效果**：用改前/改后两张截图对比，AI 能直接读图判断。
- **性能**：微信/后台应用多时手机可能降频，测流畅度前先清后台。
- **日志**：手机上拿不到 logcat（无 adb），所以 UI 行为靠肉眼 + 截图；崩溃靠应用自身提示与 CI 单测兜底。

---

## 6. 手机本地编译（已实测：可行）

> 2026-11-08 在本机实测：`assembleDebug` 成功出包，耗时 5 分 54 秒（含首次依赖下载）。
> 环境用一条命令配置：`bash scripts/mobile/local-build-setup.sh`（幂等，可重复跑；`--check` 只检查）。

### 6.1 为什么默认还在云端构建

Google 的 `aapt2` 只发布 x86_64 ELF（`aapt2-8.13.2-linux.jar` 里实测是 `ELF 64-bit x86-64`），aarch64 直接跑会在 `processDebugResources` 失败。绕过方案是 qemu-user 转译 + 隔离 amd64 sysroot，可行但要占资源：

| 占用 | 大小 |
| --- | --- |
| `~/.gradle`（依赖缓存） | 1.8GB |
| `/root/android-sdk` | 649MB |
| `/root/amd64-root`（amd64 运行库） | 17MB |
| 构建时内存峰值 | 约 1GB（Gradle 上限设 2.56GB） |

构建那 6 分钟手机会明显变慢。所以日常仍然「推 CI」，本地编译只在连续快改 UI 时开（见 §3.9）。

### 6.2 环境配置（一次性，约 5~10 分钟）

```bash
cd ~/workspace/Niriko
bash scripts/mobile/local-build-setup.sh
```

脚本做的事（全部写本机路径，不动仓库构建配置）：

1. 装 JDK 21（顺手修 proot 里 `/usr/share/man/man1` 缺失导致的 dpkg 报错）；
2. 装 Android cmdline-tools + `platforms;android-37.0` + `build-tools;35.0.0/37.0.0`；
3. `apt install qemu-user`，把 `libc6/libgcc-s1/libstdc++6/zlib1g` 的 amd64 包解到 `/root/amd64-root`（隔离 sysroot，不动系统 glibc）；
4. 解出官方 aapt2 8.13.2，生成 wrapper `/root/android-aapt2/bin/aapt2`（内容：`exec qemu-x86_64 -L /root/amd64-root /root/android-aapt2/aapt2 "$@"`）；
5. 往 `~/.gradle/gradle.properties` 写 `android.aapt2FromMavenOverride=/root/android-aapt2/bin/aapt2` 和 `org.gradle.jvmargs=-Xmx2560m …`。

### 6.3 实测结论

| 项 | 结果 |
| --- | --- |
| JDK21 + sdkmanager 在 aarch64 | ✅ 可用（sdkmanager 是 JVM 程序） |
| qemu 转译官方 aapt2 | ✅ `aapt2 version` / `compile` 冒烟通过 |
| `:app:processDebugResources` | ✅ 2 分 09 秒（含首次下载 Maven 依赖） |
| `:app:assembleDebug` | ✅ 5 分 54 秒，产物 `app-debug.apk` 31MB |
| `:app:testDebugUnitTest` | ⚠️ 1412 个测试里 42 个失败：Robolectric 的 native runtime 不支持 Linux aarch64（Room DAO/迁移、渲染类测试）。**单测结论以 CI 为准**；纯 JVM 测试本地可跑（用 `--tests` 指定类） |

### 6.4 三个必须记住的坑

1. **aapt2 wrapper 文件名必须以 `aapt2` 结尾**：AGP 的 `Aapt2FromMaven` 会校验 `endsWith("aapt2")`，叫 `aapt2-arm` 会报 `Custom AAPT2 location does not point to an AAPT2 executable`。
2. **qemu sysroot 要补 `/lib64/ld-linux-x86-64.so.2`**：Ubuntu 的 amd64 libc6 包把这个加载器放在 `usr/lib64/`，`qemu -L` 找的是 `lib64/`，需建相对软链（脚本已处理）。
3. **proot 里 openjdk 的 postinst 会因为缺 `/usr/share/man/man1` 失败**，先 `mkdir -p /usr/share/man/man1` 再装（脚本已处理）。

## 7. 速查与排障

### 7.1 速查

| 想做的事 | 命令 / 位置 |
| --- | --- |
| 进项目 | `cd ~/workspace/Niriko` |
| 看 CI | `bash scripts/mobile/ci.sh status` |
| 等 CI | `bash scripts/mobile/ci.sh watch` |
| 看失败日志 | `bash scripts/mobile/ci.sh logs` |
| 下 APK | `bash scripts/mobile/ci.sh apk [--release]` |
| 取包目录 | `/sdcard/Download/DSH-Go/apk/` |
| Actions 页面 | https://github.com/perchA5uka/Niriko/actions |
| 登录 gh | `bash scripts/mobile/gh-login.sh` |
| 配置本地编译环境 | `bash scripts/mobile/local-build-setup.sh` |
| 本地打包（可选） | `./gradlew :app:assembleDebug`（需先 export JAVA_HOME/ANDROID_HOME，见 §6.2） |
| 本地单测（仅纯 JVM） | `./gradlew :app:testDebugUnitTest --tests '…'`（Robolectric 类本地跑不了） |

### 7.2 常见报错

| 现象 | 原因 / 处理 |
| --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | debug 包与已装 release 包签名不同。用 `apk --release`（需 secrets），或先备份再卸载重装 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | CI 包的 versionCode 比手机上的小；bump `app/build.gradle.kts` 的 versionCode |
| `Permission denied (publickey)` | 公钥没加到 GitHub，或 `~/.ssh/config` 里 IdentityFile 不匹配 |
| `gh: To use GitHub CLI in a GitHub Actions workflow...` / 401 下载 artifact | gh 没登录：`bash scripts/mobile/gh-login.sh` |
| artifact 里没有 release 包 | 仓库没配 §2.2 ③ 的 4 个 secrets |
| CI 绿但运行崩溃 | 单测覆盖不到的真机分支；用截图/复现步骤让 AI 缩小范围，必要时电脑上 Android Studio 调 |
| 手机发烫/卡顿 | 后台上一个 Gradle 实验残留进程：`pkill -f gradle`；AI 后台任务结束记得收尾 |
| 大文档把上下文吃满 | 让 AI 先 grep 定位再读片段（`AGENTS.md` 已写明） |

---

## 8. 与桌面流程的差异（心里有数）

| 项 | 桌面（以前） | 手机（现在） |
| --- | --- | --- |
| 编译 | 本地 `gradlew assembleDebug` | 默认推送后 GitHub Actions（约 6~7 分钟）；本地也能编（qemu aapt2，约 6 分钟），见 §6 |
| 跑单测 | 本地 `testDebugUnitTest` | CI 里跑（本地 42 个 Robolectric 用例跑不了，见 §6.3） |
| 装包 | adb / Android Studio | 下载 APK 手点安装 |
| 看日志 | logcat | 无 adb；靠截图 + CI 单测 |
| 看 diff | IDE | `git diff` + GitHub PR 页面 |
| 改代码 | IDE 补全 | AI 改 + grep 复核 |
| 发布 | 本地打包上传 | 电脑打包上传；tag 在手机推 |

手机端的优势：手机就是目标设备，装完即测；不占桌面；随时开聊。
手机端的代价：编译要等 CI（6~7 分钟）、没有 IDE 补全、错误定位更依赖单测与截图。
