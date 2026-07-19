#!/usr/bin/env bash
set -eu

echo 'BLOCKED: 旧手工回滚入口已停用。回滚必须通过 Jenkins 发布队列并明确设置 ROLLBACK_APPROVED=true。' >&2
exit 2
