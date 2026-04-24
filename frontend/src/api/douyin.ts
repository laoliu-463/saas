import request from '../utils/request';

export interface DouyinTokenStatus {
  appId: string;
  hasAccessToken: boolean;
  maskedAccessToken: string;
  hasRefreshToken: boolean;
  maskedRefreshToken: string;
  tokenExpireAtEpochSeconds: number;
  tokenExpiringSoon: boolean;
  reauthorizeRequired: boolean;
}

export interface DouyinTokenCreateRequest {
  appId?: string;
  code?: string;
  grantType?: 'authorization_code' | 'authorization_self' | string;
}

export interface DouyinDebugResult<T = any> {
  status?: string;
  message?: string;
  errorCode?: number;
  subCode?: string;
  logId?: string;
  failedEndpoint?: string;
  remoteResponse?: T;
  endpoint?: string;
  appId?: string;
  [key: string]: any;
}

const unwrap = <T>(response: any): T => response?.data ?? response;
const isMockEnv = String(import.meta.env.VITE_ENV_LABEL || '').toUpperCase().includes('MOCK');

export function getDouyinTokenStatus(appId?: string) {
  if (isMockEnv) {
    return Promise.resolve({
      appId: appId || 'mock-app-id',
      hasAccessToken: true,
      maskedAccessToken: 'mock_access_token_****',
      hasRefreshToken: true,
      maskedRefreshToken: 'mock_refresh_token_****',
      tokenExpireAtEpochSeconds: Math.floor(Date.now() / 1000) + 7200,
      tokenExpiringSoon: false,
      reauthorizeRequired: false
    } as DouyinTokenStatus);
  }
  return request.get('/douyin/tokens', { params: { appId } }).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function refreshDouyinToken(appId?: string) {
  if (isMockEnv) {
    return Promise.resolve({
      appId: appId || 'mock-app-id',
      hasAccessToken: true,
      maskedAccessToken: 'mock_access_token_refreshed_****',
      hasRefreshToken: true,
      maskedRefreshToken: 'mock_refresh_token_****',
      tokenExpireAtEpochSeconds: Math.floor(Date.now() / 1000) + 7200,
      tokenExpiringSoon: false,
      reauthorizeRequired: false
    } as DouyinTokenStatus);
  }
  return request.post('/douyin/token-refreshes', null, { params: { appId } }).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function createDouyinToken(data: DouyinTokenCreateRequest) {
  if (isMockEnv) {
    return Promise.resolve({
      appId: data?.appId || 'mock-app-id',
      hasAccessToken: true,
      maskedAccessToken: 'mock_access_token_created_****',
      hasRefreshToken: true,
      maskedRefreshToken: 'mock_refresh_token_created_****',
      tokenExpireAtEpochSeconds: Math.floor(Date.now() / 1000) + 7200,
      tokenExpiringSoon: false,
      reauthorizeRequired: false
    } as DouyinTokenStatus);
  }
  return request.post('/douyin/tokens', data).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function getDouyinActivityTest(appId?: string) {
  if (isMockEnv) {
    return Promise.resolve({
      status: 'success',
      message: '活动调用成功（Mock）',
      appId: appId || 'mock-app-id',
      endpoint: '/douyin/activities',
      remoteResponse: {
        total: 1,
        activities: [{ activityId: 'mock_activity_001', title: 'Mock活动' }]
      }
    } as DouyinDebugResult);
  }
  return request.get('/douyin/activities', { params: { appId } }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinActivityDetail(appId: string | undefined, activityId: string) {
  if (isMockEnv) {
    return Promise.resolve({
      status: 'success',
      message: '活动详情调用成功（Mock）',
      appId: appId || 'mock-app-id',
      endpoint: `/douyin/activities/${activityId}`,
      remoteResponse: {
        activityId,
        title: 'Mock活动详情'
      }
    } as DouyinDebugResult);
  }
  return request.get(`/douyin/activities/${activityId}`, { params: { appId } }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinProductActivities(params: Record<string, any>) {
  if (isMockEnv) {
    return Promise.resolve({
      status: 'success',
      message: '活动商品调用成功（Mock）',
      appId: params?.appId || 'mock-app-id',
      endpoint: '/douyin/activity-products',
      remoteResponse: {
        total: 1,
        list: [{ activityId: 'mock_activity_001', productId: 'mock_product_001' }]
      }
    } as DouyinDebugResult);
  }
  return request.get('/douyin/activity-products', { params }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinProductsByActivity(params: { appId?: string; activityId: string; count?: number; cursor?: string }) {
  if (isMockEnv) {
    return Promise.resolve({
      status: 'success',
      message: '按活动查询商品成功（Mock）',
      appId: params?.appId || 'mock-app-id',
      endpoint: `/douyin/activities/${params.activityId}/products`,
      remoteResponse: {
        total: 1,
        list: [{ productId: 'mock_product_001', title: 'Mock商品' }]
      }
    } as DouyinDebugResult);
  }
  const { activityId, ...query } = params;
  return request.get(`/douyin/activities/${activityId}/products`, { params: query }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function createOrUpdateDouyinActivity(data: Record<string, any>) {
  if (isMockEnv) {
    return Promise.resolve({
      status: 'success',
      message: data?.activityId ? '活动更新成功（Mock）' : '活动创建成功（Mock）',
      appId: data?.appId || 'mock-app-id',
      endpoint: data?.activityId ? `/douyin/activities/${data.activityId}` : '/douyin/activities',
      remoteResponse: {
        activityId: data?.activityId || 'mock_activity_created_001'
      }
    } as DouyinDebugResult);
  }
  if (data?.activityId) {
    return request.put(`/douyin/activities/${data.activityId}`, data).then((res) => unwrap<DouyinDebugResult>(res));
  }
  return request.post('/douyin/activities', data).then((res) => unwrap<DouyinDebugResult>(res));
}
