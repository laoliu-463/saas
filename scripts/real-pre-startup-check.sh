#!/usr/bin/env bash
# real-pre 启动自检脚本
# 用途：部署前验证 .env.real-pre 的开关基线，防止配置漂移导致真实联调失败。
# 用法：scripts/real-pre-startup-check.sh [env-file]
# 退出码：0 = 全部基线通过；1 = 存在基线违反（阻止部署）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ---------------------------------------------------------------------------
# 环境文件解析（复用 health-check.sh 的 get_env 模式）
# ---------------------------------------------------------------------------
if [ -n "${1:-}" ]; then
  ENV_FILE="$1"
elif [ -n "${ENV_FILE:-}" ]; then
  ENV_FILE="${ENV_FILE}"
elif [ -f "/opt/saas/env/.env.real-pre" ]; then
  ENV_FILE="/opt/saas/env/.env.real-pre"
else
  ENV_FILE="${REPO_ROOT}/.env.real-pre"
fi

if [ ! -f "${ENV_FILE}" ]; then
  echo "FAIL: 环境文件不存在: ${ENV_FILE}" >&2
  exit 1
fi

echo "real-pre 启动自检: ${ENV_FILE}"
echo "-------------------------------------------"

get_env() {
  local key="$1"
  local default_value="${2:-}"
  local value
  value="$(awk -F= -v key="${key}" '
    /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
    {
      k=$1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", k)
      if (k == key) {
        v=$0
        sub(/^[^=]*=/, "", v)
        gsub(/\r$/, "", v)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", v)
        gsub(/^"|"$/, "", v)
        print v
        exit
      }
    }
  ' "${ENV_FILE}")"
  printf '%s' "${value:-${default_value}}"
}

check_required_env() {
  local key="$1"
  local description="$2"
  local value
  value="$(get_env "${key}" "")"
  if [ -z "${value}" ] \
    || [ "${value#MUST_CHANGE}" != "${value}" ] \
    || [ "${value#*YOUR_}" != "${value}" ] \
    || [ "${value#*PLACEHOLDER}" != "${value}" ]; then
    echo "[FAIL] ${key} 未配置或仍是占位值 — ${description}" >&2
    BASELINE_FAILURES=$((BASELINE_FAILURES + 1))
  else
    echo "[PASS] ${key} 已配置 — ${description}"
  fi
}

# ---------------------------------------------------------------------------
# 基线开关检查（必须满足才能进入真实联调）
# ---------------------------------------------------------------------------
BASELINE_FAILURES=0

check_baseline() {
  local key="$1"
  local expected="$2"
  local description="$3"
  local actual
  actual="$(get_env "${key}" "")"

  # 未设置视为使用 Spring 默认值（文档已注明默认值）
  if [ -z "${actual}" ]; then
    local default_val="$4"
    actual="${default_val}"
    echo "[默认] ${key} 未设置，使用默认值 ${default_val}"
  fi

  if [ "${actual}" = "${expected}" ]; then
    echo "[PASS] ${key}=${actual}  — ${description}"
  else
    echo "[FAIL] ${key}=${actual}（期望 ${expected}）— ${description}" >&2
    BASELINE_FAILURES=$((BASELINE_FAILURES + 1))
  fi
}

#                        环境变量                          期望值   说明                         Spring 默认值
check_baseline "APP_TEST_ENABLED"                    "false"  "关闭应用侧 mock/test 模式"      "false"
check_baseline "DOUYIN_TEST_ENABLED"                 "false"  "关闭抖音 mock gateway"          "false"
check_baseline "DOUYIN_REAL_UPSTREAM_MODE"           "live"   "使用真实抖店 upstream"           "live"
check_baseline "ORDER_SYNC_ENABLED"                  "true"   "开启真实订单同步"               "true"
check_baseline "PRODUCT_ACTIVITY_SYNC_ENABLED"       "true"   "开启活动商品定时同步"           "false"

echo ""

# ---------------------------------------------------------------------------
# 真实推广写双开关状态报告（不阻止部署，仅输出当前状态）
# ---------------------------------------------------------------------------
PROMO_WRITE_ENABLED="$(get_env "DOUYIN_REAL_PROMOTION_WRITE_ENABLED" "false")"
PROMO_WRITE_ALLOWED="$(get_env "ALLOW_REAL_PROMOTION_WRITE" "false")"

echo "-------------------------------------------"
echo "推广写操作双开关状态（不阻止部署）:"
echo "  DOUYIN_REAL_PROMOTION_WRITE_ENABLED = ${PROMO_WRITE_ENABLED}"
echo "  ALLOW_REAL_PROMOTION_WRITE           = ${PROMO_WRITE_ALLOWED}"

if [ "${PROMO_WRITE_ENABLED}" = "true" ] && [ "${PROMO_WRITE_ALLOWED}" = "true" ]; then
  echo "  状态: 双开关已开启 — 真实推广链接写入已激活"
  echo "  请确认已获得人工批准，并留存复原计划。"
else
  echo "  状态: 推广写操作未完全开启"
  echo "  generateLink() 将返回降级结果（链接字段为 null），复制基础简介仍可用。"
  echo "  若要生成真实推广链接，请同时设置两个开关为 true。"
fi

echo ""

if [ "$(get_env "LOGISTICS_KD100_SUBSCRIBE_ENABLED" "false")" = "true" ]; then
  echo "-------------------------------------------"
  echo "快递100订阅回调配置:"
  check_required_env "LOGISTICS_KD100_CALLBACK_URL" "快递100订阅公网回调地址"
  check_required_env "LOGISTICS_KD100_CALLBACK_SALT" "快递100回调验签盐"
  echo ""
fi

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
if [ "${BASELINE_FAILURES}" -gt 0 ]; then
  echo "FAIL: ${BASELINE_FAILURES} 项基线检查未通过，请修复后重新部署。" >&2
  exit 1
fi

echo "PASS: 所有基线开关检查通过。real-pre 可进入真实联调。"
exit 0
