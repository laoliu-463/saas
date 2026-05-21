import axios from 'axios';
import { createDiscreteApi } from 'naive-ui';
import { h } from 'vue';
import { useAuthStore } from '../stores/auth';
import { nowMs, recordFrontendTiming } from './performanceTiming';
import { extractTraceId } from './requestError';

const { loadingBar, message: discreteMessage } = createDiscreteApi(['loadingBar', 'message']);

declare global {
  interface Window {
    $message?: any;
  }
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

const refreshClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

let refreshPromise: Promise<string | null> | null = null;

const markRequestStarted = (config: any) => {
  config.__requestStartedAt = nowMs();
  return config;
};

const getTimingUrl = (config: any): string => {
  const baseURL = String(config?.baseURL || '').replace(/\/+$/, '');
  const url = String(config?.url || '');
  return `${baseURL}${url}`;
};

const logRequestTiming = (config: any, status: number | string, failed = false) => {
  const startedAt = Number(config?.__requestStartedAt || 0);
  const durationMs = startedAt ? Math.round(nowMs() - startedAt) : 0;
  const payload = {
    method: String(config?.method || 'GET').toUpperCase(),
    url: getTimingUrl(config),
    status,
    durationMs
  };
  recordFrontendTiming('api', payload, { failed });
};

const isAuthLoginRequest = (configOrError: any): boolean => {
  const url = String(configOrError?.url || configOrError?.config?.url || '');
  return url.includes('/auth/login');
};

const isAuthRefreshRequest = (configOrError: any): boolean => {
  const url = String(configOrError?.url || configOrError?.config?.url || '');
  return url.includes('/auth/refresh');
};

const isValidToken = (token: unknown): token is string => {
  if (typeof token !== 'string') return false;
  const normalized = token.trim();
  return Boolean(normalized) && normalized !== 'undefined' && normalized !== 'null';
};

const normalizeServerMessage = (message: string): string => {
  const raw = String(message || '').trim();
  const lower = raw.toLowerCase();

  if (!raw) return '请求失败，请稍后重试';
  if (
    lower.includes('java.time.localdatetime') ||
    lower.includes('datetimeparseexception') ||
    lower.includes('nextfollowtime')
  ) {
    return '跟进时间格式不正确，请重新选择时间';
  }
  if (raw.includes('AssignRequest["assigneeId"]') || raw.includes('assigneeId')) {
    return '负责人ID格式不正确，请输入系统中的标准负责人ID';
  }
  if (lower.includes('cannot deserialize value of type') && lower.includes('java.util.uuid')) {
    return '负责人ID格式不正确，请输入系统中的标准负责人ID';
  }
  if (lower.includes('uuid has to be represented by standard 36-char representation')) {
    return '负责人ID格式不正确，请输入36位标准格式的负责人ID';
  }
  if (lower.includes('json parse error')) {
    return '提交内容格式不正确，请检查后重试';
  }
  return raw;
};

const buildFriendlyErrorMessage = (error: any): string => {
  const serverMsg = error?.response?.data?.msg;
  if (serverMsg && String(serverMsg).trim()) {
    return normalizeServerMessage(String(serverMsg));
  }

  const status = error?.response?.status;
  const rawMsg = String(error?.message || '').toLowerCase();
  const code = String(error?.code || '').toUpperCase();

  if (rawMsg.includes('socket hang up') || rawMsg.includes('network error')) {
    return '网络连接异常，请确认服务已启动后重试';
  }
  if (code === 'ECONNABORTED' || rawMsg.includes('timeout')) {
    return '请求超时，请稍后重试';
  }
  if (status === 401) {
    return '登录已失效，请重新登录';
  }
  if (status === 403) {
    return '权限不足，无法执行当前操作';
  }
  if (status === 404) {
    return '接口不存在，请联系管理员确认接口地址';
  }
  if (status >= 500) {
    return '服务暂时不可用，请稍后重试';
  }
  if (!status) {
    return '请求发送失败，请检查网络或服务状态';
  }
  return '请求失败，请稍后重试';
};

const copyTraceId = async (traceId: string) => {
  try {
    if (!navigator?.clipboard?.writeText) {
      throw new Error('clipboard unavailable');
    }
    await navigator.clipboard.writeText(traceId);
    discreteMessage.success('traceId 已复制');
  } catch {
    discreteMessage.warning('浏览器未允许复制，请手动复制 traceId');
  }
};

const showErrorNotice = (message: string, traceId?: string) => {
  const normalizedTraceId = String(traceId || '').trim();
  const options = { duration: 5000, closable: true };
  if (!normalizedTraceId) {
    discreteMessage.error(message, options);
    return;
  }

  discreteMessage.error(
    () => h(
      'div',
      {
        style: 'display:flex;align-items:center;gap:8px;line-height:1.4;'
      },
      [
        h('span', null, `${message}（traceId: ${normalizedTraceId}）`),
        h(
          'button',
          {
            type: 'button',
            style: 'border:1px solid rgba(255,255,255,.55);border-radius:4px;background:transparent;color:inherit;padding:2px 6px;cursor:pointer;font-size:12px;',
            onClick: (event: MouseEvent) => {
              event.stopPropagation();
              void copyTraceId(normalizedTraceId);
            }
          },
          '复制 traceId'
        )
      ]
    ),
    options
  );
};

const clearStoredAuth = () => {
  try {
    useAuthStore().clearAuth();
  } catch (_error) {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('refreshExpiresIn');
    localStorage.removeItem('accessTokenExpiresIn');
    localStorage.removeItem('userInfo');
  }
};

const redirectToLogin = () => {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
};

const performTokenRefresh = async (): Promise<string | null> => {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!isValidToken(refreshToken)) {
    return null;
  }
  if (!refreshPromise) {
    refreshPromise = refreshClient
      .post('/auth/refresh', { refreshToken })
      .then((response) => {
        const payload = response?.data?.data;
        const accessToken = String(payload?.accessToken || '').trim();
        if (!accessToken) {
          throw new Error('刷新接口未返回 access token');
        }
        useAuthStore().updateTokens({
          token: accessToken,
          refreshToken: payload?.refreshToken || refreshToken,
          refreshExpiresIn: payload?.refreshExpiresIn ?? null,
          accessTokenExpiresIn: payload?.accessTokenExpiresIn ?? null
        });
        return accessToken;
      })
      .catch((error) => {
        clearStoredAuth();
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
};

const shouldTryRefresh = (configOrError: any): boolean => {
  if (isAuthLoginRequest(configOrError) || isAuthRefreshRequest(configOrError)) {
    return false;
  }
  return isValidToken(localStorage.getItem('refreshToken'));
};

refreshClient.interceptors.request.use(
  (config) => markRequestStarted(config),
  (error) => Promise.reject(error)
);

refreshClient.interceptors.response.use(
  (response) => {
    logRequestTiming(response.config, response.status);
    return response;
  },
  (error) => {
    logRequestTiming(error?.config, error?.response?.status || error?.code || 'ERR', true);
    return Promise.reject(error);
  }
);

request.interceptors.request.use(
  (config) => {
    markRequestStarted(config);
    loadingBar.start();
    const token = localStorage.getItem('token');
    const headers = (config.headers || {}) as any;
    config.headers = headers;
    if (isValidToken(token)) {
      headers['Authorization'] = `Bearer ${token}`;
    } else {
      delete headers['Authorization'];
      if (!isAuthLoginRequest(config)) {
        loadingBar.error();
        clearStoredAuth();
        redirectToLogin();
        logRequestTiming(config, 'CANCELLED', true);
        return Promise.reject(new axios.Cancel('缺少登录态，已阻止未授权请求'));
      }
    }
    if (config.method?.toUpperCase() === 'GET') {
      config.params = {
        ...config.params,
        _t: Date.now()
      };
    }
    headers['Cache-Control'] = 'no-cache, no-store, must-revalidate';
    headers['Pragma'] = 'no-cache';
    headers['Expires'] = '0';
    return config;
  },
  (error) => {
    loadingBar.error();
    return Promise.reject(error);
  }
);

request.interceptors.response.use(
  (response) => {
    logRequestTiming(response.config, response.status);
    if (response.status === 304) {
      loadingBar.finish();
      return Promise.reject(new Error('资源未修改，使用缓存'));
    }
    const businessCode = response?.data?.code;
    if (businessCode === 401) {
      return Promise.reject({
        __business401: true,
        response: {
          ...response,
          status: 401
        },
        config: response.config,
        data: response.data
      });
    }
    if (typeof businessCode === 'number' && businessCode !== 200) {
      loadingBar.error();
      const msg = normalizeServerMessage(String(response?.data?.msg || '请求失败，请稍后重试'));
      const traceId = extractTraceId(response);
      showErrorNotice(msg, traceId);
      return Promise.reject({ ...response.data, traceId });
    }
    loadingBar.finish();
    return response.data;
  },
  async (error) => {
    if (axios.isCancel(error)) {
      return Promise.reject(error);
    }
    logRequestTiming(error?.config, error?.response?.status || error?.code || 'ERR', true);
    const originalRequest = error?.config;
    const isUnauthorized = error?.response?.status === 401 || error?.__business401 === true;
    if (isUnauthorized && originalRequest && !originalRequest.__isRetryRequest && shouldTryRefresh(error)) {
      originalRequest.__isRetryRequest = true;
      try {
        const nextToken = await performTokenRefresh();
        if (isValidToken(nextToken)) {
          originalRequest.headers = originalRequest.headers || {};
          originalRequest.headers.Authorization = `Bearer ${nextToken}`;
          return request(originalRequest);
        }
      } catch (_refreshError) {
        loadingBar.error();
        if (!isAuthLoginRequest(error)) {
          showErrorNotice('登录已过期，请重新登录', extractTraceId(_refreshError));
          redirectToLogin();
        }
        return Promise.reject(_refreshError);
      }
    }
    loadingBar.error();
    const msg = buildFriendlyErrorMessage(error);
    if (isUnauthorized) {
      clearStoredAuth();
      if (!isAuthLoginRequest(error)) {
        showErrorNotice('登录失效或未授权，请重新登录', extractTraceId(error));
        redirectToLogin();
      }
    } else {
      showErrorNotice(msg, extractTraceId(error));
    }
    return Promise.reject(error);
  }
);

export default request;
