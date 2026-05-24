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

export interface DouyinOAuthAuthorizeUrl {
  authorizeUrl: string;
  state: string;
  redirectUri: string;
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
const REAL_PRE_REQUEST_TIMEOUT_MS = 120_000;

export function getDouyinTokenStatus(appId?: string) {
  return request.get('/douyin/tokens', { params: { appId }, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function refreshDouyinToken(appId?: string) {
  return request.post('/douyin/token-refreshes', null, { params: { appId }, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function createDouyinToken(data: DouyinTokenCreateRequest) {
  return request.post('/douyin/tokens', data, { timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinTokenStatus>(res));
}

export function getDouyinAuthorizeUrl(appId?: string) {
  return request.get('/douyin/oauth/authorize-url', { params: { appId }, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinOAuthAuthorizeUrl>(res));
}

export function getDouyinActivityTest(appId?: string) {
  return request.get('/douyin/activities', { params: { appId }, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinActivityDetail(appId: string | undefined, activityId: string) {
  return request.get(`/douyin/activities/${activityId}`, { params: { appId }, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinInstitutionInfo(appId?: string) {
  return request.get('/douyin/institution-info', { params: { appId }, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinProductActivities(params: Record<string, any>) {
  return request.get('/douyin/activity-products', { params, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinActivityProductList(params: { appId?: string; activityId: string; count?: number; cursor?: string }) {
  return request.get('/douyin/activity-product-list', { params, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinOrderSettlements(params: DouyinOrderSettlementParams) {
  return request.get('/douyin/order-settlements', { params, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function getDouyinProductsByActivity(params: { appId?: string; activityId: string; count?: number; cursor?: string }) {
  const { activityId, ...query } = params;
  return request.get(`/douyin/activities/${activityId}/products`, { params: query, timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function postDouyinRawProbe(data: Record<string, any>) {
  return request.post('/douyin/promotion-link-probes/raw', data, { timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}

export function createOrUpdateDouyinActivity(data: Record<string, any>) {
  if (data?.activityId) {
    return request.put(`/douyin/activities/${data.activityId}`, data, { timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
  }
  return request.post('/douyin/activities', data, { timeout: REAL_PRE_REQUEST_TIMEOUT_MS }).then((res) => unwrap<DouyinDebugResult>(res));
}
