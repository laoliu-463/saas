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

request.interceptors.request.use(
  (config) => {
    loadingBar.start();
    const token = localStorage.getItem('token');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    // 强制 GET 请求防缓存
    if (config.method?.toUpperCase() === 'GET') {
      config.params = {
        ...config.params,
        _t: Date.now()
      };
    }
    // 禁用浏览器缓存
    config.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate';
    config.headers['Pragma'] = 'no-cache';
    config.headers['Expires'] = '0';
    return config;
  },
  (error) => {
    loadingBar.error();
    return Promise.reject(error);
  }
);

request.interceptors.response.use(
  response => {
    if (response.status === 304) {
      loadingBar.finish();
      return Promise.reject(new Error('资源未修改，使用缓存'));
    }
    loadingBar.finish();
    return response.data;
  },
  error => {
    loadingBar.error();
    const msg = error.response?.data?.msg || error.message || '请求失败';
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
