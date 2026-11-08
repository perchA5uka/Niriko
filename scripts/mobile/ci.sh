#!/usr/bin/env bash
# Niriko 手机端 CI 助手（Android CI：单测 + 打包）
#
# 用法：
#   bash scripts/mobile/ci.sh status            # 最近几轮 CI 状态（不需要登录）
#   bash scripts/mobile/ci.sh watch             # 等当前分支最新一轮跑完（默认 15 分钟超时）
#   bash scripts/mobile/ci.sh logs [run-id]     # 打印失败步骤日志（需要 gh 已登录）
#   bash scripts/mobile/ci.sh apk [--release]   # 下载最新成功构建的 APK 到手机下载目录（需要 gh 已登录）
#   bash scripts/mobile/ci.sh open              # 打印 Actions 页面地址，用手机浏览器打开
#
# 环境变量：
#   NIRIKO_REPO     默认 perchA5uka/Niriko
#   NIRIKO_APK_DIR  默认 /sdcard/Download/DSH-Go/apk
#   NIRIKO_ARTIFACT debug|release 等价于 apk --release
set -euo pipefail

REPO="${NIRIKO_REPO:-perchA5uka/Niriko}"
APK_DIR="${NIRIKO_APK_DIR:-/sdcard/Download/DSH-Go/apk}"
API="https://api.github.com/repos/$REPO"
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)"

log()  { printf '%s\n' "$*"; }
die()  { printf '错误：%s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "缺少命令 $1（apt-get install -y $1）"; }

has_gh() {
  command -v gh >/dev/null 2>&1 || return 1
  gh auth status --hostname github.com >/dev/null 2>&1 || return 1
  return 0
}

# ── 不需要登录的接口：直接 curl 公开 API ─────────────────────────────────────
runs_json() {
  local n="${1:-5}"
  curl -fsS -H 'Accept: application/vnd.github+json' -H 'User-Agent: niriko-mobile-ci' \
    "$API/actions/runs?branch=$BRANCH&per_page=$n" 2>/dev/null
}

print_runs() {
  local json="$1"
  printf '%s' "$json" | jq -r '
    .workflow_runs[]? |
    [ (if .status == "completed" then (.conclusion // "-") else .status end),
      .head_branch,
      (.head_sha[0:7]),
      .created_at,
      (.id|tostring),
      .display_title ] | @tsv
  ' | awk -F'\t' 'BEGIN{printf "%-10s %-14s %-9s %-21s %-12s %s\n","状态","分支","commit","创建时间","run-id","标题"}
                   {printf "%-10s %-14s %-9s %-21s %-12s %s\n",$1,$2,$3,$4,$5,$6}'
}

run_json() { # $1=run-id（空=当前分支最新一轮）
  local id="${1:-}"
  if [ -n "$id" ]; then
    curl -fsS -H 'Accept: application/vnd.github+json' -H 'User-Agent: niriko-mobile-ci' \
      "$API/actions/runs/$id" 2>/dev/null
  else
    runs_json 1 | jq -c '.workflow_runs[0] // empty' 2>/dev/null
  fi
}

cmd_status() {
  local json
  json="$(runs_json 5)" || die "接口请求失败（检查网络）"
  log "仓库 $REPO · 分支 $BRANCH（最近 5 轮）"
  if [ "$(printf '%s' "$json" | jq '.workflow_runs | length')" = "0" ]; then
    log "（这个分支还没有 CI 记录——push 之后再看；main 的历史用 --branch main 查）"
    return 0
  fi
  print_runs "$json"
  log ""
  log "看日志：bash scripts/mobile/ci.sh logs    取包：bash scripts/mobile/ci.sh apk"
}

cmd_watch() {
  local timeout="${1:-900}" start now id state conclusion interval json
  start=$(date +%s)
  # 未登录时公开接口限流 60 次/小时，放宽轮询间隔；gh 登录后 5000 次/小时，20 秒一次
  if has_gh; then interval=20; else interval=60; fi
  interval="${NIRIKO_POLL:-$interval}"
  log "等 $BRANCH 的最新一轮 CI（超时 ${timeout}s，每 ${interval}s 查一次）…"
  while :; do
    now=$(date +%s)
    json="$(run_json)" || json=""
    [ "$json" = "null" ] && json=""
    if [ -z "$json" ]; then
      if runs_json 1 >/dev/null 2>&1; then
        log "（$BRANCH 还没有 CI 记录：先 push 这个分支，或改用 --branch 指定分支）"
      else
        log "（查询失败，${interval}s 后重试；可能是未登录被限流，可先跑 bash scripts/mobile/gh-login.sh）"
      fi
    else
      id="$(printf '%s' "$json" | jq -r '.id // empty')"
      state="$(printf '%s' "$json" | jq -r '.status // empty')"
      conclusion="$(printf '%s' "$json" | jq -r '.conclusion // empty')"
      if [ "$state" = "completed" ]; then
        if [ "$conclusion" = "success" ]; then
          log "✓ CI 通过（run $id，用时 $((now - start))s）"
          log "取包：bash scripts/mobile/ci.sh apk   （同签名包加 --release）"
          return 0
        fi
        log "✗ CI 失败（run $id，conclusion=$conclusion，用时 $((now - start))s）"
        log "看日志：bash scripts/mobile/ci.sh logs $id"
        return 1
      fi
      log "… run $id 状态 $state（已等 $((now - start))s）"
    fi
    if [ $((now - start)) -ge "$timeout" ]; then
      log "超时：run ${id:-?} 仍是 ${state:-unknown}，去 Actions 页面看：https://github.com/$REPO/actions"
      return 2
    fi
    sleep "$interval"
  done
}

cmd_logs() {
  need gh
  has_gh || die "看日志需要 gh 已登录：bash scripts/mobile/gh-login.sh"
  local id="${1:-}"
  if [ -z "$id" ]; then
    id="$(gh run list --repo "$REPO" --branch "$BRANCH" --limit 1 --json databaseId -q '.[0].databaseId')"
  fi
  [ -n "$id" ] || die "找不到 run"
  log "run $id 失败步骤日志："
  gh run view "$id" --repo "$REPO" --log-failed 2>/dev/null || gh run view "$id" --repo "$REPO" --log 2>/dev/null | tail -300
}

cmd_apk() {
  need gh
  has_gh || die "下载 artifact 需要 gh 已登录：bash scripts/mobile/gh-login.sh"
  local want="debug" id tmp apk
  case "${1:-}" in
    --release|release) want="release" ;;
    ""|--debug|debug) want="debug" ;;
    *) die "未知参数 $1（可用：--release）" ;;
  esac
  local artifact="niriko-${want}-apk"
  id="$(gh run list --repo "$REPO" --branch "$BRANCH" --workflow android-ci.yml \
        --status success --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"
  if [ -z "$id" ]; then
    id="$(gh run list --repo "$REPO" --branch "$BRANCH" --status success --limit 1 \
          --json databaseId -q '.[0].databaseId')"
  fi
  [ -n "$id" ] || die "该分支还没有成功的 CI run"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  log "从 run $id 下载 $artifact …"
  if ! gh run download "$id" --repo "$REPO" -n "$artifact" -D "$tmp" 2>/dev/null; then
    die "下载失败：可能没有 $artifact（release 包需要先配签名 secrets，见流程文档 §4.3）"
  fi
  apk="$(find "$tmp" -name '*.apk' | head -1)"
  [ -n "$apk" ] || die "artifact 里没找到 apk"
  mkdir -p "$APK_DIR"
  local dest="$APK_DIR/$(basename "$apk")"
  cp "$apk" "$dest"
  rm -rf "$tmp"; trap - EXIT
  log "✓ 已保存：$dest（$(du -h "$dest" | cut -f1)）"
  log "安装：手机「文件管理」→ 下载/DSH-Go/apk → 点开 $(basename "$apk") → 安装"
  log "  提示：debug 包与已装 release 包签名不同，装不上；要覆盖安装并保留数据用 --release"
}

cmd_open() {
  log "Actions：https://github.com/$REPO/actions"
  log "最新 release：https://github.com/$REPO/releases/latest"
  log "对比/PR：https://github.com/$REPO/compare"
}

cmd="${1:-status}"; shift || true
case "$cmd" in
  status) cmd_status ;;
  watch)  cmd_watch "${1:-900}" ;;
  logs)   cmd_logs "${1:-}" ;;
  apk)    cmd_apk "${1:-}" ;;
  open)   cmd_open ;;
  *) die "未知命令 $cmd（status|watch|logs|apk|open）" ;;
esac
