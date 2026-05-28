import fs from 'node:fs';
import path from 'node:path';
import { test as setup, expect } from '@playwright/test';
import { accounts, storageStates } from './helpers/test-data';

const authDir = path.join(process.cwd(), 'tests', 'e2e', '.auth');
const frontendOrigin = new URL(process.env.E2E_BASE_URL || 'http://127.0.0.1:3000').origin;
const backendOrigin = (process.env.E2E_BACKEND_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');

setup.setTimeout(180_000);

setup.beforeAll(() => {
  fs.mkdirSync(authDir, { recursive: true });
});

async function loginApi(username: string, password: string) {
  let lastError = '';
  for (let attempt = 1; attempt <= 6; attempt += 1) {
    try {
      const response = await fetch(`${backendOrigin}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const body = await response.json().catch(() => null) as { data?: Record<string, unknown>; msg?: string } | null;
      if (response.ok && body?.data) {
        return body.data;
      }
      lastError = `HTTP ${response.status}: ${body?.msg || response.statusText}`;
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, attempt * 1000));
  }
  throw new Error(`登录接口失败: ${username} ${lastError}`);
}

async function loginAndSave(username: string, password: string, statePath: string) {
  const data = await loginApi(username, password);
  const token = String(data.token || data.accessToken || '');
  expect(token, `登录无 token: ${username}`).toBeTruthy();

  const userInfo = {
    ...data,
    token,
    id: data.id || data.userId,
    accessTokenExpiresIn: data.accessTokenExpiresIn || data.expiresIn
  };
  const localStorage = [
    { name: 'token', value: token },
    { name: 'accessTokenExpiresIn', value: String(userInfo.accessTokenExpiresIn || '') },
    { name: 'refreshToken', value: String(data.refreshToken || '') },
    { name: 'refreshExpiresIn', value: String(data.refreshExpiresIn || '') },
    { name: 'userInfo', value: JSON.stringify(userInfo) }
  ];

  fs.writeFileSync(
    statePath,
    JSON.stringify({ cookies: [], origins: [{ origin: frontendOrigin, localStorage }] }, null, 2),
    'utf8'
  );
}

setup('prepare role storage states', async () => {
  await loginAndSave(accounts.admin.username, accounts.admin.password, storageStates.admin);
  await loginAndSave(accounts.bizLeader.username, accounts.bizLeader.password, storageStates.bizLeader);
  await loginAndSave(accounts.bizStaff.username, accounts.bizStaff.password, storageStates.bizStaff);
  await loginAndSave(accounts.channelLeader.username, accounts.channelLeader.password, storageStates.channelLeader);
  await loginAndSave(accounts.channelStaff.username, accounts.channelStaff.password, storageStates.channelStaff);
  await loginAndSave(accounts.ops.username, accounts.ops.password, storageStates.ops);
});
