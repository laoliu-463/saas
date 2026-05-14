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
  grantType?: 'authorization_code' | string;
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

export interface DouyinOrderSettlementParams {
  appId?: string;
  size?: number;
  cursor?: string;
  timeType?: 'update' | 'settle' | string;
  startTime?: string;
  endTime?: string;
  orderIds?: string;
}

const unwrap = <T>(response: any): T => response?.data ?? response;
const isTestEnv = ['MOCK', 'TEST'].some((keyword) =>
  String(import.meta.env.VITE_ENV_LABEL || '').toUpperCase().includes(keyword)
);

export function getDouyinTokenStatus(appId?: string) {
  if (isTestEnv) {
    return Promise.resolve({
      appId: appId || 'test-app-id',
      hasAccessToken: true,
      maskedAccessToken: 'test_access_token_****',
      hasRefreshToken: true,
      maskedRefreshToken: 'test_refresh_token_****',
      tokenExpireAtEpochSeconds: Math.floor(Date.now() / 1000) + 7200,
      tokenExpiringSoon: false,
      reauthorizeRequired: false
    } as DouyinTokenStatus);
  }
  return request.get('/douyin/tokens', { params: { appId } }).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function refreshDouyinToken(appId?: string) {
  if (isTestEnv) {
    return Promise.resolve({
      appId: appId || 'test-app-id',
      hasAccessToken: true,
      maskedAccessToken: 'test_access_token_refreshed_****',
      hasRefreshToken: true,
      maskedRefreshToken: 'test_refresh_token_****',
      tokenExpireAtEpochSeconds: Math.floor(Date.now() / 1000) + 7200,
      tokenExpiringSoon: false,
      reauthorizeRequired: false
    } as DouyinTokenStatus);
  }
  return request.post('/douyin/token-refreshes', null, { params: { appId } }).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function createDouyinToken(data: DouyinTokenCreateRequest) {
  if (isTestEnv) {
    return Promise.resolve({
      appId: data?.appId || 'test-app-id',
      hasAccessToken: true,
      maskedAccessToken: 'test_access_token_created_****',
      hasRefreshToken: true,
      maskedRefreshToken: 'test_refresh_token_created_****',
      tokenExpireAtEpochSeconds: Math.floor(Date.now() / 1000) + 7200,
      tokenExpiringSoon: false,
      reauthorizeRequired: false
    } as DouyinTokenStatus);
  }
  return request.post('/douyin/tokens', data).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function getDouyinActivityTest(appId?: string) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: '活动调用成功（Test）',
      appId: appId || 'test-app-id',
      endpoint: '/douyin/activities',
      remoteResponse: {
        total: 1,
        activities: [{ activityId: 'test_activity_001', title: 'Test活动' }]
      }
    } as DouyinDebugResult);
  }
  return request.get('/douyin/activities', { params: { appId } }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinActivityDetail(appId: string | undefined, activityId: string) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: '活动详情调用成功（Test）',
      appId: appId || 'test-app-id',
      endpoint: `/douyin/activities/${activityId}`,
      remoteResponse: {
        activityId,
        title: 'Test活动详情'
      }
    } as DouyinDebugResult);
  }
  return request.get(`/douyin/activities/${activityId}`, { params: { appId } }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinInstitutionInfo(appId?: string) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: '授权主体查询成功（Test）',
      appId: appId || 'test-app-id',
      endpoint: 'buyin.institutionInfo',
      remoteResponse: {
        code: 10000,
        msg: 'success'
      }
    } as DouyinDebugResult);
  }
  return request.get('/douyin/institution-info', { params: { appId } }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinProductActivities(params: Record<string, any>) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: '活动商品调用成功（Test）',
      appId: params?.appId || 'test-app-id',
      endpoint: '/douyin/activity-products',
      remoteResponse: {
        total: 1,
        list: [{ activityId: 'test_activity_001', productId: 'test_product_001' }]
      }
    } as DouyinDebugResult);
  }
  return request.get('/douyin/activity-products', { params }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinActivityProductList(params: { appId?: string; activityId: string; count?: number; cursor?: string }) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: '活动商品列表调用成功（Test）',
      appId: params?.appId || 'test-app-id',
      endpoint: 'alliance.colonelActivityProduct',
      remoteResponse: {
        code: 10000,
        msg: 'success',
        data: {
          data: [{ product_id: 'test_product_001', title: 'Test商品' }]
        }
      }
    } as DouyinDebugResult);
  }
  return request.get('/douyin/activity-product-list', { params }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinOrderSettlements(params: DouyinOrderSettlementParams) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: '团长分次结算订单查询成功（Test）',
      appId: params?.appId || 'test-app-id',
      endpoint: 'buyin.colonelMultiSettlementOrders',
      query: params,
      remoteResponse: {
        code: 10000,
        msg: 'success',
        data: {
          cursor: '',
          orders: []
        }
      }
    } as DouyinDebugResult);
  }
  return request.get('/douyin/order-settlements', { params }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinProductsByActivity(params: { appId?: string; activityId: string; count?: number; cursor?: string }) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: '按活动查询商品成功（Test）',
      appId: params?.appId || 'test-app-id',
      endpoint: `/douyin/activities/${params.activityId}/products`,
      remoteResponse: {
        total: 1,
        list: [{ productId: 'test_product_001', title: 'Test商品' }]
      }
    } as DouyinDebugResult);
  }
  const { activityId, ...query } = params;
  return request.get(`/douyin/activities/${activityId}/products`, { params: query }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function postDouyinRawProbe(data: Record<string, any>) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: 'RAW 探针成功（Test）',
      appId: data?.appId || 'test-app-id',
      endpoint: data?.method || 'test.method',
      remoteResponse: {
        code: 10000,
        msg: 'success'
      }
    } as DouyinDebugResult);
  }
  return request.post('/douyin/promotion-link-probes/raw', data).then((res) => unwrap<DouyinDebugResult>(res));
}

export function createOrUpdateDouyinActivity(data: Record<string, any>) {
  if (isTestEnv) {
    return Promise.resolve({
      status: 'success',
      message: data?.activityId ? '活动更新成功（Test）' : '活动创建成功（Test）',
      appId: data?.appId || 'test-app-id',
      endpoint: data?.activityId ? `/douyin/activities/${data.activityId}` : '/douyin/activities',
      remoteResponse: {
        activityId: data?.activityId || 'test_activity_created_001'
      }
    } as DouyinDebugResult);
  }
  if (data?.activityId) {
    return request.put(`/douyin/activities/${data.activityId}`, data).then((res) => unwrap<DouyinDebugResult>(res));
  }
  return request.post('/douyin/activities', data).then((res) => unwrap<DouyinDebugResult>(res));
}
