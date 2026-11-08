#!/usr/bin/env bash
# 智能装包：签名一致就地覆盖；签名不一致自动「备份 → 卸载 → 安装 → 恢复数据」。
#
# 为什么需要它（2026-11-08 实测踩过的坑，全部在这里收口）：
#   1. 小米 HyperOS 下 adb install 直接报 INSTALL_FAILED_USER_RESTRICTED: Invalid apk，
#      但不是包的问题 —— 必须走 root：先 cp 到 /data/local/tmp 再 pm install。
#      （/sdcard 是 FUSE，system_server 读不到，所以一定要先拷进 /data/local/tmp）
#   2. 重装后应用 uid 会变（历史数据属主是旧 uid），恢复数据必须按**新 uid** chown，
#      否则 SQLite 报 unable to open database file / Permission denied。
#   3. SELinux 上下文要按「新装应用自己创建的目录」的 categories 递归 chcon，
#      单纯 restorecon 补不回 per-app 的 MLS categories。
#
# 用法：
#   bash scripts/mobile/install-apk.sh                 # 装 /sdcard/Download/DSH-Go/apk 下最新的 apk
#   bash scripts/mobile/install-apk.sh path/to/x.apk
set -uo pipefail

PKG="${NIRIKO_PKG:-com.otakup.niriko}"
ADB_SERIAL="${ANDROID_SERIAL:-127.0.0.1:5555}"
ADB=(adb -s "$ADB_SERIAL")
BK_ROOT="/sdcard/Download/DSH-Go"
ROOT_SH="/root/workspace/dev-env/root-adb/r.sh"
TMP_APK="/data/local/tmp/niriko-install.apk"

log() { printf '%s\n' "$*"; }
die() { printf '错误：%s\n' "$*" >&2; exit 1; }

APK="${1:-}"
if [ -z "$APK" ]; then
  APK="$(ls -1t "$BK_ROOT/apk"/*.apk 2>/dev/null | head -1)"
fi
[ -n "$APK" ] && [ -f "$APK" ] || die "找不到 APK；先 bash scripts/mobile/ci.sh apk [--release]"
"${ADB[@]}" get-state >/dev/null 2>&1 || die "adb 没连上：先 adb connect $ADB_SERIAL"
[ -f "$ROOT_SH" ] || die "缺少 root 通道脚本 $ROOT_SH（dev-env/root-adb 的模块没装）"
ROOT() { bash "$ROOT_SH" "$1"; }

log "设备：$(adb -s "$ADB_SERIAL" shell getprop ro.product.model | tr -d '\r')"
log "安装包：$APK（$(du -h "$APK" | cut -f1)）"

# 手机侧 root 看不到 proot 里的路径（/root/workspace 是 DSH 的根文件系统，不是 Android 的），
# 所以任何不在 /sdcard 下的包都要先落一份到手机存储，后续 root 侧一律用 HOST_APK。
case "$APK" in
  /sdcard/*) HOST_APK="$APK" ;;
  *)
    mkdir -p "$BK_ROOT/apk"
    HOST_APK="$BK_ROOT/apk/$(basename "$APK")"
    cp -f "$APK" "$HOST_APK" || die "复制到 $HOST_APK 失败"
    log "  · 已复制到 $HOST_APK（root 侧可见）"
    ;;
esac

# ---- 1. root 就地安装（签名一致时最短路径） --------------------------------
ROOT "cp '$HOST_APK' '$TMP_APK' && chmod 0644 '$TMP_APK'" >/dev/null || die "拷包到 $TMP_APK 失败"
OUT="$(ROOT "pm install -r -d '$TMP_APK'")"
if printf '%s' "$OUT" | grep -q 'Success'; then
  log "✓ 签名一致，已就地覆盖安装（数据未动）"
  ROOT "rm -f '$TMP_APK'" >/dev/null
  exit 0
fi
if ! printf '%s' "$OUT" | grep -qi 'UPDATE_INCOMPATIBLE\|signatures do not match\|INSTALL_FAILED_UPDATE_INCOMPATIBLE'; then
  printf '%s\n' "$OUT" | tail -6
  die "安装失败（不是签名问题，未做任何破坏性操作）"
fi

# ---- 2. 签名不一致：备份 → 卸载 → 安装 → 恢复 -----------------------------
log "签名不一致，进入「备份 → 卸载 → 安装 → 恢复」流程"
TS="$(date +%Y%m%d-%H%M%S)"
BK="$BK_ROOT/niriko-backup-$TS"
ROOT "mkdir -p '$BK'" >/dev/null
log "备份目录：$BK"

P="$(ROOT "pm path $PKG | head -1 | cut -d: -f2" | tr -d '\r' | grep '^/' | head -1)"
[ -n "$P" ] && ROOT "cp '$P' '$BK/installed.apk'" >/dev/null && log "  · 已备份当前 APK"
HAVE_DATA=0
if ROOT "tar czf '$BK/data.tgz' -C /data/data $PKG 2>/dev/null; [ -s '$BK/data.tgz' ] && echo OK" | grep -q OK; then
  HAVE_DATA=1; log "  · 已备份应用数据"
fi

adb -s "$ADB_SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1
ROOT "pm uninstall $PKG" >/dev/null 2>&1 || log "  · 原本未安装"
ROOT "pm install -r -d '$TMP_APK'" | grep -q 'Success' || die "新包安装失败；回滚包在 $BK/installed.apk"
ROOT "rm -f '$TMP_APK'" >/dev/null

if [ "$HAVE_DATA" = "1" ]; then
  # 先启动一次：让系统按新 uid/新 categories 建目录，作为属主与上下文的参照
  adb -s "$ADB_SERIAL" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 3
  adb -s "$ADB_SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1
  NEW_UID="$(adb -s "$ADB_SERIAL" shell pm list packages -U | grep "$PKG" | sed 's/.*uid://' | tr -d '\r')"
  [ -n "$NEW_UID" ] || die "拿不到新 uid"
  ROOT "tar xzf '$BK/data.tgz' -C /data/data" >/dev/null 2>&1
  ROOT "chown -R '$NEW_UID:$NEW_UID' /data/data/$PKG" >/dev/null 2>&1
  CTX="$(ROOT "stat -c '%C' /data/data/$PKG" | tr -d '\r' | grep app_data_file | head -1)"
  [ -n "$CTX" ] && ROOT "chcon -R '$CTX' /data/data/$PKG" >/dev/null 2>&1
  log "  · 数据已恢复（uid=$NEW_UID，上下文=$CTX）"
fi

adb -s "$ADB_SERIAL" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
PID="$(adb -s "$ADB_SERIAL" shell pidof "$PKG" | tr -d '\r')"
VER="$(adb -s "$ADB_SERIAL" shell dumpsys package "$PKG" | grep -m1 versionName | tr -d '\r' | sed 's/.*=//')"
if [ -n "$PID" ]; then
  log "✓ 安装完成并已启动：versionName=$VER pid=$PID"
else
  log "⚠ 装上了但进程没起来，看崩溃：nlog crash"
fi
log "回滚：$BK/installed.apk（数据：$BK/data.tgz）"
