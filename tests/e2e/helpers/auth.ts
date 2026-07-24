import fs from 'node:fs';
import path from 'node:path';
import {
  request as playwrightRequest,
  type APIRequestContext,
  type BrowserContext,
  type Page
} from '@playwright/test';
import { accounts, type AccountCredential, type AccountKey } from './test-data';

export type AuthPayload = Record<string, unknown> & {
  token?: string;
  accessToken?: string;
  refreshToken?: string;
  refreshExpiresIn?: number | string | null;
  accessTokenExpiresIn?: number | string | null;
  expiresIn?: number | string | null;
  user?: Record<string, unknown>;
};

export interface LoginOptions {
  backendUrl?: string;
  retries?: number;
  timeout?: number;
}

type AuthInitTarget = Pick<Page, 'addInitScript'> | Pick<BrowserContext, 'addInitScript'>;

export function getBackendOrigin(backendUrl = process.env.E2E_BACKEND_URL || 'http://127.0.0.1:8080'): string {
  return backendUrl.replace(/\/api\/?$/, '').replace(/\/$/, '');
}

export function getFrontendOrigin(frontendUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:3000'): string {
  return new URL(frontendUrl).origin;
}

/** 唯一的登录入口：setup、API 断言和 real-pre 都复用这段协议。 */
export async function loginWithCredentials(
  credentials: AccountCredential | { username: string; password: string },
  options: LoginOptions = {}
): Promise<AuthPayload> {
  const retries = options.retries ?? 5;
  const timeout = options.timeout ?? 30_000;
  const ctx: APIRequestContext = await playwrightRequest.newContext({
    baseURL: getBackendOrigin(options.backendUrl),
    ignoreHTTPSErrors: true,
    timeout
  });
  let lastError = 'unknown error';

  try {
    for (let attempt = 1; attempt <= retries; attempt += 1) {
      try {
        const response = await ctx.post('/api/auth/login', {
          data: credentials,
          headers: { 'Content-Type': 'application/json' }
        });
        const body = await response.json().catch(() => null) as {
          data?: AuthPayload;
          msg?: string;
        } | null;
        const data = body?.data;
        const token = data?.token || data?.accessToken;
        if (response.ok() && data && token) {
          return { ...data, token: String(token) };
        }
        lastError = `HTTP ${response.status()}: ${body?.msg || response.statusText()}`;
      } catch (error) {
        lastError = error instanceof Error ? error.message : String(error);
      }

      if (attempt < retries) {
        await new Promise((resolve) => setTimeout(resolve, attempt * 500));
      }
    }
  } finally {
    await ctx.dispose();
  }

  throw new Error(`登录接口失败: ${credentials.username} ${lastError}`);
}

export async function loginAs(role: AccountKey, options: LoginOptions = {}): Promise<AuthPayload> {
  return loginWithCredentials(accounts[role], options);
}

function userInfoFromPayload(payload: AuthPayload): Record<string, unknown> {
  return payload.user && typeof payload.user === 'object' ? payload.user : payload;
}

function stringValue(value: unknown): string {
  return value == null ? '' : String(value);
}

export function authStorageEntries(payload: AuthPayload): Array<{ name: string; value: string }> {
  const token = payload.token || payload.accessToken || '';
  const accessTokenExpiresIn = payload.accessTokenExpiresIn ?? payload.expiresIn;
  return [
    { name: 'token', value: stringValue(token) },
    { name: 'refreshToken', value: stringValue(payload.refreshToken) },
    { name: 'refreshExpiresIn', value: stringValue(payload.refreshExpiresIn) },
    { name: 'accessTokenExpiresIn', value: stringValue(accessTokenExpiresIn) },
    { name: 'userInfo', value: JSON.stringify(userInfoFromPayload(payload)) }
  ];
}

export function createStorageState(payload: AuthPayload, origin = getFrontendOrigin()) {
  return {
    cookies: [],
    origins: [{ origin, localStorage: authStorageEntries(payload) }]
  };
}

export function writeStorageState(statePath: string, payload: AuthPayload): void {
  fs.mkdirSync(path.dirname(statePath), { recursive: true });
  fs.writeFileSync(statePath, JSON.stringify(createStorageState(payload), null, 2), 'utf8');
}

/** 给真实 real-pre 浏览器上下文注入同一套认证状态，避免每个 spec 自己拼 localStorage。 */
export async function installAuth(target: AuthInitTarget, payload: AuthPayload): Promise<void> {
  await target.addInitScript((entries: Array<{ name: string; value: string }>) => {
    for (const entry of entries) {
      window.localStorage.setItem(entry.name, entry.value);
    }
  }, authStorageEntries(payload));
}
