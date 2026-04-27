import request from '../utils/request';

export interface TalentListItem {
  id: string
  nickname?: string | null
  douyinUid?: string | null
  douyinNo?: string | null
  uid?: string | null
  fansCount?: number | null
  likesCount?: number | null
  worksCount?: number | null
  ipLocation?: string | null
  level?: string | null
  monthlySales?: number | null
  poolStatus?: string | null
  ownerId?: string | null
  ownerName?: string | null
  protectedUntil?: string | null
  sampleCount?: number | null
  orderCount?: number | null
  serviceFeeContribution?: number | null
}

export interface TalentDetailResponse {
  talent?: {
    id?: string
    nickname?: string | null
    douyinUid?: string | null
    douyinNo?: string | null
    uid?: string | null
    secUid?: string | null
    profileUrl?: string | null
    fansCount?: number | null
    likesCount?: number | null
    worksCount?: number | null
    ipLocation?: string | null
    level?: string | null
    monthlySales?: number | null
    contactPhone?: string | null
    remark?: string | null
  }
  claim?: {
    poolStatus?: string | null
    ownerId?: string | null
    ownerName?: string | null
    claimedAt?: string | null
    protectedUntil?: string | null
  }
  samples?: Array<{
    sampleRequestId?: string | null
    productName?: string | null
    status?: string | null
    statusText?: string | null
    createTime?: string | null
    completeTime?: string | null
  }>
  orders?: Array<{
    orderId?: string | null
    productName?: string | null
    orderAmount?: number | null
    serviceFee?: number | null
    channelName?: string | null
    createTime?: string | null
  }>
}

// CRM talents
export const getTalentPage = (params: any) => request.get('/talents', { params });
export const getTalentList = (params: any) => getTalentPage(params);
export const getTalentPublic = (params: any) => request.get('/talents/pools/public', { params });
export const getTalentPrivate = (params: any) => request.get('/talents/pools/private', { params });
export const getTalentById = (id: string): Promise<TalentDetailResponse> =>
  request.get(`/talents/${id}`).then((res: any) => res.data as TalentDetailResponse);
export const createTalent = (data: any) => request.post('/talents', data);
export const updateTalent = (id: string, data: any) => request.put(`/talents/${id}`, data);
export const deleteTalent = (id: string) => request.delete(`/talents/${id}`);
export const claimTalent = (id: string) => request.post(`/talents/${id}/claims`);
export const releaseTalent = (id: string) => request.post(`/talents/${id}/release`);
export const refreshTalent = (id: string) => request.post(`/talents/${id}/refresh`);
export const refreshWeeklyTalents = () => request.post('/talents/refresh/weekly');
export const manualFillTalent = (id: string, data: any) => request.put(`/talents/${id}/manual-fill`, data);
export const getLatestEnrichTask = (id: string) => request.get(`/talents/${id}/enrich-task/latest`);
export const exclusiveCheck = (id: string) => request.get(`/talents/${id}/exclusive-status`);
