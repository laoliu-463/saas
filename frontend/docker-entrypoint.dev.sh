#!/bin/sh
set -eu

lock_hash="$(sha256sum pnpm-lock.yaml | awk '{print $1}')"
installed_hash=""
image_lock_hash=""

if [ -f node_modules/.lockfile.sha256 ]; then
  installed_hash="$(cat node_modules/.lockfile.sha256)"
fi

if [ -f /opt/frontend-node_modules.lock.sha256 ]; then
  image_lock_hash="$(cat /opt/frontend-node_modules.lock.sha256)"
fi

if [ ! -d node_modules/.pnpm ] || [ "$installed_hash" != "$lock_hash" ]; then
  if [ "$image_lock_hash" = "$lock_hash" ] && [ -d /opt/frontend-node_modules/.pnpm ]; then
    echo "[frontend-dev] Syncing node_modules from image cache"
    mkdir -p node_modules
    find node_modules -mindepth 1 -maxdepth 1 -exec rm -rf {} \;
    cp -a /opt/frontend-node_modules/. node_modules/
  else
    echo "[frontend-dev] Image dependency cache is stale; running pnpm install"
    CI=true pnpm install --frozen-lockfile
  fi
  printf '%s' "$lock_hash" > node_modules/.lockfile.sha256
fi

if [ "${FRONTEND_SERVE_MODE:-dev}" = "preview" ]; then
  pnpm build
  exec pnpm preview --host 0.0.0.0 --port "${VITE_DEV_PORT:-3000}"
fi

exec pnpm dev --host 0.0.0.0
