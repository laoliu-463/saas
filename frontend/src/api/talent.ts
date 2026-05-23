import request from '../utils/request';

export interface TalentQueryParams {
  page?: number
  size?: number
  keyword?: string
  view?: string
  category?: string
  claimStatus?: string
  minFans?: number
  maxFans?: number
  region?: string
  poolStatus?: string
  ownerKeyword?: string
}

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
  mainCategory?: string | null
  liveSalesBand?: string | null
  liveViewBand?: string | null
  liveGpmBand?: string | null
  videoSalesBand?: string | null
  videoPlayBand?: string | null
  videoGpmBand?: string | null
  blacklistReason?: string | null
  contactPhone?: string | null
  remark?: string | null
  avatarUrl?: string | null
  claimedAt?: string | null
  blacklisted?: boolean | null
  naturalOrderTalent?: boolean | null
  activeClaimCount?: number | null
  claimTags?: string[] | null
}

export interface TalentProfilePayload {
  douyinAccount?: string | null
  talentUid?: string | null
  nickname?: string | null
  avatarUrl?: string | null
  fansCount?: number | null
  likeCount?: number | null
  followingCount?: number | null
  worksCount?: number | null
  ipLocation?: string | null
  talentLevel?: string | null
  sales30d?: number | null
}

export interface ResolveTalentProfileResponse {
  success: boolean
  provider?: string | null
  syncStatus?: string | null
  profile?: TalentProfilePayload | null
  unsupportedFields?: string[]
  rawPayloadSaved?: boolean
  dataSource?: string | null
  syncErrorCode?: string | null
  syncErrorMessage?: string | null
}

export interface TalentDetailResponse {
  talent?: {
    id?: string
    nickname?: string | null
    douyinUid?: string | null
    douyinNo?: string | null
    uid?: string | null
    profileUrl?: string | null
    fansCount?: number | null
    likesCount?: number | null
    worksCount?: number | null
    ipLocation?: string | null
    level?: string | null
    monthlySales?: number | null
    mainCategory?: string | null
    liveSalesBand?: string | null
    liveViewBand?: string | null
    liveGpmBand?: string | null
    videoSalesBand?: string | null
    videoPlayBand?: string | null
    videoGpmBand?: string | null
    blacklisted?: boolean | null
    blacklistReason?: string | null
    orderCount?: number | null
    sampleCount?: number | null
    serviceFeeContribution?: number | null
    contactPhone?: string | null
    remark?: string | null
    avatarUrl?: string | null
    tags?: string[] | null
    tagUpdatedBy?: string | null
    shippingRecipientName?: string | null
    shippingRecipientPhone?: string | null
    shippingRecipientAddress?: string | null
    claimTags?: string[] | null
    dataSource?: string | null
    syncStatus?: string | null
    unsupportedFields?: string[]
    syncErrorMessage?: string | null
    talentLevel?: string | null
    sales30d?: number | null
  }
  claim?: {
    poolStatus?: string | null
    ownerId?: string | null
    ownerName?: string | null
    claimedAt?: string | null
    protectedUntil?: string | null
    lastOrderAt?: string | null
    recipientName?: string | null
    recipientPhone?: string | null
    recipientAddress?: string | null
    activeClaimCount?: number | null
  activeClaimOwners?: Array<{
    userId?: string | null
    ownerName?: string | null
    claimedAt?: string | null
    protectedUntil?: string | null
  }>
  claimStatus?: string | null
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

export interface TalentStatusTransitionsResponse {
  protectionConfigKey: string
  allowMultiClaim: boolean
  states: Array<{
    code: string
    label: string
    description: string
    canClaim: boolean
    canRelease: boolean
    visibleToRoles: string[]
  }>
  transitions: Array<{
    fromState: string
    action: string
    actionLabel: string
    toState: string
    actorRoles: string[]
    condition: string
    effect: string
  }>
}

// CRM talents
export const getTalentPage = (params: TalentQueryParams) => request.get('/talents', { params });
export const getTalentList = (params: any) => getTalentPage(params);
export const getTalentPublic = (params: any) => request.get('/talents/pools/public', { params });
export const getTalentPrivate = (params: any) => request.get('/talents/pools/private', { params });
export const getTalentStatusTransitions = () => request.get('/talents/status-transitions');
export const getTalentById = (id: string): Promise<TalentDetailResponse> =>
  request.get(`/talents/${id}`).then((res: any) => res.data as TalentDetailResponse);
export const resolveTalentProfile = (payload: {
  input: string
  forceRefresh?: boolean
  manualFill?: boolean
  manualPayload?: Record<string, unknown>
}) => request.post('/talents/resolve-profile', payload).then((res: any) => res.data as ResolveTalentProfileResponse);
export const syncTalentProfile = (id: string, forceRefresh = false) =>
  request.post(`/talents/${id}/sync-profile`, null, { params: { forceRefresh } }).then((res: any) => res.data as ResolveTalentProfileResponse);
export const createTalent = (data: any) => request.post('/talents', data);
export const updateTalent = (id: string, data: any) => request.put(`/talents/${id}`, data);
export const updateTalentTags = (id: string, tags: string[]) =>
  request.put(`/talents/${id}/tags`, { tags }).then((res: any) => res.data as string[]);
export const updateTalentShippingAddress = (
  id: string,
  payload: { recipientName?: string; recipientPhone?: string; recipientAddress?: string }
) => request.put(`/talents/${id}/shipping-address`, payload).then((res: any) => res.data);
export const getTalentShippingAddress = (id: string) =>
  request.get(`/talents/${id}/shipping-address`).then((res: any) => res.data as {
    recipientName?: string | null
    recipientPhone?: string | null
    recipientAddress?: string | null
  });
export const batchImportTalents = (accounts: string[]) =>
  request.post('/talents/batch-import', { accounts }).then((res: any) => res.data);
export const getPresetTalentTags = () =>
  request.get('/talents/preset-tags').then((res: any) => res.data as string[]);
export const deleteTalent = (id: string) => request.delete(`/talents/${id}`);
export const claimTalent = (id: string) => request.post(`/talents/${id}/claims`);
export const releaseTalent = (id: string) => request.post(`/talents/${id}/release`);
export const blacklistTalent = (id: string, data?: { reason?: string }) => request.post(`/talents/${id}/blacklist`, data || {});
export const unblacklistTalent = (id: string) => request.post(`/talents/${id}/unblacklist`);
export const refreshTalent = (id: string) => request.post(`/talents/${id}/refresh`);
export const refreshWeeklyTalents = () => request.post('/talents/refresh/weekly');
export const manualFillTalent = (id: string, data: any) => request.put(`/talents/${id}/manual-fill`, data);
export const getLatestEnrichTask = (id: string) => request.get(`/talents/${id}/enrich-task/latest`);
export const exclusiveCheck = (id: string) => request.get(`/talents/${id}/exclusive-status`);
