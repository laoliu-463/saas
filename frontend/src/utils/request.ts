import axios from 'axios';
import { createDiscreteApi } from 'naive-ui';

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

request.interceptors.request.use(
  (config) => {
    loadingBar.start();
    const token = localStorage.getItem('token');
    const headers = (config.headers || {}) as any;
    config.headers = headers;
    if (isValidToken(token)) {
      headers['Authorization'] = `Bearer ${token}`;
    } else {
      delete headers['Authorization'];
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
    if (response.status === 304) {
      loadingBar.finish();
      return Promise.reject(new Error('资源未修改，使用缓存'));
    }
    const businessCode = response?.data?.code;
    if (businessCode === 401) {
      loadingBar.finish();
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      discreteMessage.error('登录已过期，请重新登录');
      window.location.href = '/login';
      return Promise.reject(response.data);
    }
    if (typeof businessCode === 'number' && businessCode !== 200) {
      loadingBar.error();
      const msg = normalizeServerMessage(String(response?.data?.msg || '请求失败，请稍后重试'));
      discreteMessage.error(msg);
      return Promise.reject(response.data);
    }
    loadingBar.finish();
    return response.data;
  },
  (error) => {
    loadingBar.error();
    const msg = buildFriendlyErrorMessage(error);
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      discreteMessage.error('登录失效或未授权，请重新登录');
      window.location.href = '/login';
    } else {
      discreteMessage.error(msg);
    }
    return Promise.reject(error);
  }
);

export default request;
