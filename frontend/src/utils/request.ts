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

const buildFriendlyErrorMessage = (error: any): string => {
  const serverMsg = error?.response?.data?.msg;
  if (serverMsg && String(serverMsg).trim()) {
    return String(serverMsg);
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
    if (response?.data?.code === 401) {
      loadingBar.finish();
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      discreteMessage.error('登录已过期，请重新登录');
      window.location.href = '/login';
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
