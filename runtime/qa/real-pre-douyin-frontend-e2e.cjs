/**
 * Real-pre 抖店联调 E2E — 入口脚本（逻辑已迁移至 Playwright）
 *
 * 通常通过根目录 `npm run e2e:real-pre` 调用本脚本。
 *
 * 环境变量（可选）：
 *   FRONTEND_URL / E2E_BASE_URL — 默认 http://localhost:3001
 *   BACKEND_URL / E2E_BACKEND_URL — 默认 http://localhost:8081
 */

const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.join(__dirname, '..', '..');

process.env.E2E_REAL_PRE = 'true';
if (!process.env.E2E_BASE_URL) {
  process.env.E2E_BASE_URL = process.env.FRONTEND_URL || 'http://localhost:3001';
}
if (!process.env.E2E_BACKEND_URL) {
  process.env.E2E_BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8081';
}

const result = spawnSync(
  'npx',
  ['playwright', 'test', '--project=real-pre', 'tests/e2e/08-real-pre-douyin-integration.spec.ts'],
  {
    cwd: root,
    stdio: 'inherit',
    shell: true,
    env: process.env
  }
);

process.exit(result.status === null ? 1 : result.status);
