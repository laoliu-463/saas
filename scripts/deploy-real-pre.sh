#!/usr/bin/env bash
set -eu

echo 'BLOCKED: 旧手工 real-pre 部署入口已停用。禁止在服务器工作树现场构建；请使用 release/real-pre 与 Jenkins 唯一发布队列。' >&2
exit 2
