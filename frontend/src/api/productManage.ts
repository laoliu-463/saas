import request from '../utils/request'

export const fetchProductManageList = (params: Record<string, unknown>) =>
  request.get('/products/manage', { params })

export const fetchProductFilterOptions = () =>
  request.get('/products/filter-options')

export interface ProductManageApprovePayload {
  remark?: string
  exclusivePriceAmount?: number
  exclusivePriceRemark?: string
  shippingInfo?: string
  sellingPoints?: string[]
  promotionScript?: string
  supportsAds?: boolean
  adsRule?: string
  rewardRemark?: string
  participationRequirements?: string
  campaignTimeRemark?: string
  materialFiles?: string[]
  goodsTags?: string[]
  productTags?: string[]
  sampleThresholdSales?: number
  sampleThresholdLevel?: number
  sampleThresholdRemark?: string
}

export const approveProduct = (relationId: string, data: ProductManageApprovePayload) =>
  request.post(`/products/manage/${relationId}/approve`, data)

export const rejectProduct = (relationId: string, data: Record<string, unknown>) =>
  request.post(`/products/manage/${relationId}/reject`, data)

export const fetchProductDetail = (relationId: string) =>
  request.get(`/products/${relationId}`)

export const updateProduct = (relationId: string, data: Record<string, unknown>) =>
  request.put(`/products/${relationId}`, data)

export const fetchCooperationSetting = (relationId: string) =>
  request.get(`/products/${relationId}/cooperation-setting`)

export const updateCooperationSetting = (relationId: string, data: Record<string, unknown>) =>
  request.put(`/products/${relationId}/cooperation-setting`, data)

export const fetchSampleSetting = (relationId: string) =>
  request.get(`/products/${relationId}/sample-setting`)

export const updateSampleSetting = (relationId: string, data: Record<string, unknown>) =>
  request.put(`/products/${relationId}/sample-setting`, data)

export const batchSupplementProducts = (data: Record<string, unknown>) =>
  request.post('/products/batch-supplement', data)

export const pauseProduct = (relationId: string) =>
  request.post(`/products/${relationId}/pause`)

export const resumeProduct = (relationId: string) =>
  request.post(`/products/${relationId}/resume`)

export const copyProductScript = (relationId: string) =>
  request.post(`/products/${relationId}/copy-script`)

export const copyProductLink = (relationId: string) =>
  request.post(`/products/${relationId}/copy-link`)

export const assignProduct = (relationId: string, data: Record<string, unknown>) =>
  request.put(`/products/${relationId}/assignee`, data)

export const extendPromotion = (relationId: string, data: Record<string, unknown>) =>
  request.put(`/products/${relationId}/promotion-extension`, data)

export const exportProducts = (params: Record<string, unknown>) =>
  request.get('/products/export', { params, responseType: 'blob' })
