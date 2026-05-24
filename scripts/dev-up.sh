#!/bin/sh
set -eu

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# --- Backend: host-compile ---
cd "$REPO_ROOT/backend"
echo "==> Compiling backend (host) ..."
mvn package -DskipTests -q
echo "    target/colonel-saas.jar ready"

# --- Frontend: host-compile (skip if dist/ is fresh) ---
cd "$REPO_ROOT/frontend"
FRONTEND_DIST_MARKER="$REPO_ROOT/frontend/dist/.host-build-marker"
REBUILD_FRONTEND=1

if [ -f "$FRONTEND_DIST_MARKER" ]; then
  marker_mtime=$(stat -c %Y "$FRONTEND_DIST_MARKER" 2>/dev/null || stat -f %m "$FRONTEND_DIST_MARKER" 2>/dev/null || echo 0)
  newest_src=$(stat -c %Y package.json 2>/dev/null || stat -f %m package.json 2>/dev/null || echo 0)
  if [ "${newest_src:-0}" -le "${marker_mtime:-0}" ] 2>/dev/null; then
    REBUILD_FRONTEND=0
    echo "==> Frontend dist/ is up-to-date, skipping build"
  fi
fi

if [ "$REBUILD_FRONTEND" -eq 1 ]; then
  echo "==> Building frontend (host) ..."
  pnpm build
  touch "$FRONTEND_DIST_MARKER"
  echo "    frontend/dist ready"
fi

# --- Start containers ---
echo "==> Starting test environment ..."
cd "$REPO_ROOT"
export FRONTEND_SERVE_MODE=preview
exec docker compose -f docker-compose.test.yml up "$@"
