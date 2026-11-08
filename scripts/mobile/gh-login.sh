#!/usr/bin/env bash
# 手机端 GitHub 登录（设备码流程，不需要手输/粘贴 token）
#
# 用法：bash scripts/mobile/gh-login.sh
# 流程：脚本给出 8 位设备码 → 手机浏览器打开 https://github.com/login/device → 输入码并授权
#       → 脚本自动把 token 交给 gh 保存（~/.config/gh/hosts.yml，权限 600）。
# scopes：repo（看自己仓库的 Actions 日志 / artifact / release 需要它）
set -euo pipefail

CLIENT_ID="178c6fc778ccc68e1d6a"   # GitHub CLI 的公开 OAuth client id（设备码流程）
SCOPE="${NIRIKO_GH_SCOPE:-repo}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "缺少命令 $1" >&2; exit 1; }; }
need curl
need jq

start=$(curl -fsS -X POST https://github.com/login/device/code \
  -H 'Accept: application/json' \
  -d "client_id=$CLIENT_ID" -d "scope=$SCOPE")

device_code=$(printf '%s' "$start" | jq -r '.device_code')
user_code=$(printf '%s' "$start" | jq -r '.user_code')
uri=$(printf '%s' "$start" | jq -r '.verification_uri')
interval=$(printf '%s' "$start" | jq -r '.interval // 5')
expires=$(printf '%s' "$start" | jq -r '.expires_in // 900')

cat <<EOF

────────────────────────────────────────
请在手机上完成 GitHub 授权（${expires} 秒内有效）：

  1. 打开  $uri
  2. 输入设备码：  $user_code
  3. 点「Authorize GitHub CLI」
  4. 完成后回来看本终端，脚本会自动继续
────────────────────────────────────────
EOF

deadline=$(( $(date +%s) + expires ))
while :; do
  sleep "$interval"
  resp=$(curl -fsS -X POST https://github.com/login/oauth/access_token \
    -H 'Accept: application/json' \
    -d "client_id=$CLIENT_ID" -d "device_code=$device_code" \
    -d "grant_type=urn:ietf:params:oauth:grant-type:device_code" || true)
  err=$(printf '%s' "$resp" | jq -r '.error // empty')
  token=$(printf '%s' "$resp" | jq -r '.access_token // empty')
  if [ -n "$token" ]; then
    printf '%s' "$token" | gh auth login --hostname github.com --git-protocol ssh --with-token
    gh config set git_protocol ssh --hostname github.com >/dev/null 2>&1 || true
    echo
    echo "✓ 登录成功：$(gh api user -q .login)"
    echo "  现在可以：bash scripts/mobile/ci.sh apk / logs"
    exit 0
  fi
  case "$err" in
    authorization_pending) printf '.' ;;
    slow_down) interval=$((interval + 5)); printf '.' ;;
    expired_token) echo; echo "✗ 设备码已过期，重新运行本脚本" >&2; exit 1 ;;
    access_denied) echo; echo "✗ 授权被拒绝" >&2; exit 1 ;;
    "") echo; echo "✗ 未知响应：$resp" >&2; exit 1 ;;
    *) echo; echo "✗ $err（$resp）" >&2; exit 1 ;;
  esac
  [ "$(date +%s)" -ge "$deadline" ] && { echo; echo "✗ 超时，重新运行本脚本" >&2; exit 1; }
done
