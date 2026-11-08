#!/usr/bin/env bash
# 手机（aarch64 proot Ubuntu）本地构建 Niriko 的一键环境配置
#
# 背景：Google 只发布 x86_64 的 aapt2，aarch64 上 Gradle 的 processDebugResources 会失败。
#       这里用 qemu-user 转译官方 x86_64 aapt2 + 隔离的 amd64 sysroot，再通过
#       android.aapt2FromMavenOverride 指给 AGP。
#
# 用法：
#   bash scripts/mobile/local-build-setup.sh          # 配置（幂等，可重复跑）
#   bash scripts/mobile/local-build-setup.sh --check  # 只检查不安装
#
# 之后构建：
#   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
#   export ANDROID_HOME=/root/android-sdk
#   ./gradlew :app:assembleDebug --console=plain
#
# 只写本机路径（JDK / /root/android-sdk / /root/android-aapt2 / /root/amd64-root /
# ~/.gradle/gradle.properties），不改仓库里的任何构建配置。
set -euo pipefail

SDK=/root/android-sdk
AAPT2_DIR=/root/android-aapt2
SYSROOT=/root/amd64-root
CMDTOOLS_VER="${CMDTOOLS_VER:-14742923}"
AAPT2_URL="https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.13.2-14304508/aapt2-8.13.2-14304508-linux.jar"
CHECK_ONLY=0
[ "${1:-}" = "--check" ] && CHECK_ONLY=1

log()  { printf '%s\n' "$*"; }
ok()   { printf '  [OK]   %s\n' "$*"; }
warn() { printf '  [WARN] %s\n' "$*"; }
die()  { printf '错误：%s\n' "$*" >&2; exit 1; }

# ── 0. 前置 ──────────────────────────────────────────────────────────────────
[ "$(uname -m)" = "aarch64" ] || die "本脚本只适用于 aarch64 手机环境"
for t in curl unzip file git; do command -v "$t" >/dev/null || die "缺少 $t"; done

if [ "$CHECK_ONLY" = "1" ]; then
  log "== 检查本地构建环境 =="
  command -v java >/dev/null && java -version 2>&1 | head -1 || warn "没有 java"
  [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ] && ok "sdkmanager 存在" || warn "没有 sdkmanager"
  [ -d "$SDK/platforms/android-37.0" ] && ok "platform 37.0 存在" || warn "缺 platforms;android-37.0"
  [ -d "$SDK/build-tools/37.0.0" ] && ok "build-tools 37.0.0 存在" || warn "缺 build-tools;37.0.0"
  [ -x /usr/bin/qemu-x86_64 ] && ok "qemu-x86_64 存在" || warn "没有 qemu-x86_64"
  [ -x "$AAPT2_DIR/bin/aapt2" ] && ok "aapt2 wrapper 存在" || warn "没有 aapt2 wrapper"
  if [ -x "$AAPT2_DIR/bin/aapt2" ]; then "$AAPT2_DIR/bin/aapt2" version 2>&1 | head -1; fi
  grep -q '^android.aapt2FromMavenOverride=' ~/.gradle/gradle.properties 2>/dev/null \
    && ok "gradle.properties 已配置 aapt2 override" || warn "gradle.properties 未配置 override"
  exit 0
fi

# ── 1. JDK 21 ────────────────────────────────────────────────────────────────
log "== 1/6 JDK 21 =="
if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "21'; then
  ok "已安装：$(java -version 2>&1 | head -1)"
else
  # proot 里 /usr/share/man/man1 可能缺失，会让 openjdk 的 postinst 失败
  mkdir -p /usr/share/man/man1
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openjdk-21-jdk-headless \
    || { dpkg --configure -a; apt-get install -y -f --no-install-recommends openjdk-21-jdk-headless; }
  ok "已安装：$(java -version 2>&1 | head -1)"
fi
JAVA_HOME_LOCAL="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"

# ── 2. Android cmdline-tools ─────────────────────────────────────────────────
log "== 2/6 Android cmdline-tools =="
if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  ok "已存在：$("$SDK/cmdline-tools/latest/bin/sdkmanager" --version 2>/dev/null | tail -1)"
else
  mkdir -p "$SDK/cmdline-tools"
  if ! curl -fsSL --max-time 300 -o /tmp/cmdtools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDTOOLS_VER}_latest.zip"; then
    die "commandlinetools 下载失败（换 CMDTOOLS_VER 再试，可用值见 dl.google.com/android/repository/repository2-3.xml）"
  fi
  rm -rf /tmp/cmdtools && mkdir -p /tmp/cmdtools
  unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools
  rm -rf "$SDK/cmdline-tools/latest"
  mv /tmp/cmdtools/cmdline-tools "$SDK/cmdline-tools/latest"
  ok "已解包到 $SDK/cmdline-tools/latest"
fi
export JAVA_HOME="$JAVA_HOME_LOCAL"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"

# ── 3. SDK 组件 ──────────────────────────────────────────────────────────────
log "== 3/6 SDK 组件（platform 37 + build-tools）=="
if [ -d "$SDK/platforms/android-37.0" ] && [ -d "$SDK/build-tools/37.0.0" ]; then
  ok "platform 37.0 / build-tools 37.0.0 已就绪"
else
  yes | "$SDKM" --licenses >/dev/null 2>&1 || true
  yes | "$SDKM" "platforms;android-37.0" "build-tools;37.0.0" "build-tools;35.0.0" "platform-tools" >/tmp/sdk-install.log 2>&1 \
    || { tail -20 /tmp/sdk-install.log; die "SDK 组件安装失败"; }
  ok "已安装 platform 37.0 / build-tools 35.0.0+37.0.0 / platform-tools"
fi

# ── 4. qemu + amd64 sysroot ──────────────────────────────────────────────────
log "== 4/6 qemu-user + amd64 sysroot =="
command -v qemu-x86_64 >/dev/null 2>&1 || apt-get install -y --no-install-recommends qemu-user >/tmp/apt-qemu.log 2>&1
command -v qemu-x86_64 >/dev/null 2>&1 || die "qemu-x86_64 安装失败"
ok "qemu：$(qemu-x86_64 --version | head -1)"

if [ -e "$SYSROOT/usr/lib/x86_64-linux-gnu/libc.so.6" ]; then
  ok "amd64 运行库已在 $SYSROOT"
else
  log "  下载 amd64 基础运行库（libc6/libgcc-s1/libstdc++6/zlib1g）…"
  mkdir -p /tmp/amd64debs
  curl -fsSL --max-time 120 "https://archive.ubuntu.com/ubuntu/dists/noble/main/binary-amd64/Packages.gz" \
    -o /tmp/Packages.gz || die "取 amd64 包索引失败"
  gunzip -c /tmp/Packages.gz > /tmp/Packages-amd64
  base="https://archive.ubuntu.com/ubuntu"
  fetch_deb() { # $1=包名
    local pkg="$1" fn
    fn=$(awk -v pkg="$pkg" 'BEGIN{RS="";FS="\n"} {n="";f="";
      for(i=1;i<=NF;i++){ if($i ~ /^Package: /) n=substr($i,10); if($i ~ /^Filename: /) f=substr($i,11) }
      if(n==pkg && f!=""){print f; exit}}' /tmp/Packages-amd64)
    [ -n "$fn" ] || die "索引里找不到 $pkg"
    curl -fsSL --max-time 300 "$base/$fn" -o "/tmp/amd64debs/$pkg.deb" || die "$pkg 下载失败"
    dpkg -x "/tmp/amd64debs/$pkg.deb" "$SYSROOT" && ok "$pkg → $SYSROOT"
  }
  for p in libc6 libgcc-s1 libstdc++6 zlib1g; do fetch_deb "$p"; done
fi
# qemu -L 查的是 <sysroot>/lib64/ld-linux-x86-64.so.2，Ubuntu 包里在 usr/lib64，补一条相对软链
mkdir -p "$SYSROOT/lib64"
ln -sfn ../usr/lib64/ld-linux-x86-64.so.2 "$SYSROOT/lib64/ld-linux-x86-64.so.2"
ok "sysroot 动态加载器：$(readlink -f "$SYSROOT/lib64/ld-linux-x86-64.so.2")"

# ── 5. aapt2（qemu wrapper）──────────────────────────────────────────────────
log "== 5/6 aapt2（官方 x86_64 版 + qemu wrapper）=="
mkdir -p "$AAPT2_DIR/bin"
if [ ! -x "$AAPT2_DIR/aapt2" ]; then
  curl -fsSL --max-time 300 -o /tmp/aapt2.jar "$AAPT2_URL" || die "aapt2 jar 下载失败"
  unzip -o -j /tmp/aapt2.jar aapt2 -d "$AAPT2_DIR" >/dev/null
  chmod +x "$AAPT2_DIR/aapt2"
  ok "已解出官方 aapt2：$AAPT2_DIR/aapt2"
fi
# 注意：文件名必须以 aapt2 结尾——AGP 的 Aapt2FromMaven 会校验 endsWith("aapt2")
cat > "$AAPT2_DIR/bin/aapt2" <<EOF
#!/bin/bash
exec /usr/bin/qemu-x86_64 -L $SYSROOT $AAPT2_DIR/aapt2 "\$@"
EOF
chmod +x "$AAPT2_DIR/bin/aapt2"
ok "wrapper：$AAPT2_DIR/bin/aapt2"
"$AAPT2_DIR/bin/aapt2" version 2>&1 | head -1

# ── 6. Gradle 本机配置 ──────────────────────────────────────────────────────
log "== 6/6 ~/.gradle/gradle.properties =="
mkdir -p ~/.gradle
GP=~/.gradle/gradle.properties
touch "$GP"
grep -q '^android.aapt2FromMavenOverride=' "$GP" \
  && sed -i "s|^android.aapt2FromMavenOverride=.*|android.aapt2FromMavenOverride=$AAPT2_DIR/bin/aapt2|" "$GP" \
  || echo "android.aapt2FromMavenOverride=$AAPT2_DIR/bin/aapt2" >> "$GP"
# 手机内存有限，限制 Gradle 与 Kotlin 的内存上限（不改仓库里的 gradle.properties）
grep -q '^org.gradle.jvmargs=' "$GP" \
  || echo 'org.gradle.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' >> "$GP"
ok "当前配置："; cat "$GP"

cat <<EOF

────────────────────────────────────────
环境就绪。构建命令：

  export JAVA_HOME=$JAVA_HOME_LOCAL
  export ANDROID_HOME=$SDK
  ./gradlew :app:assembleDebug --console=plain

产物：app/build/outputs/apk/debug/app-debug.apk
首次构建要下载 Maven 依赖（约 1~2GB），之后增量构建快很多。
────────────────────────────────────────
EOF
