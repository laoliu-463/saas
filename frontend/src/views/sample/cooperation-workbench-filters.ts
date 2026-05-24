export interface CooperationWorkbenchFilters {
  productKeyword: string
  shopKeyword: string
  trackingNo: string
  requestNo: string
  talentKeyword: string
  cooperationType: string | null
  sampleOwnerType: string | null
  homeworkType: string | null
  recipientName: string
  recipientPhone: string
  channelUserId: string | null
  recruiterUserId: string | null
  applyRange: [number, number] | null
  homeworkRange: [number, number] | null
  logisticsCompany: string | null
}

export function toSampleLocalDateTime(value?: number) {
  if (!value) return undefined
  const date = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function buildCooperationSampleFilterParams(filters: CooperationWorkbenchFilters, activeStatus: string) {
  return {
    status: activeStatus || undefined,
    productKeyword: filters.productKeyword || undefined,
    shopKeyword: filters.shopKeyword || undefined,
    trackingNo: filters.trackingNo || undefined,
    requestNo: filters.requestNo || undefined,
    talentKeyword: filters.talentKeyword || undefined,
    cooperationType: filters.cooperationType || undefined,
    sampleOwnerType: filters.sampleOwnerType || undefined,
    homeworkType: filters.homeworkType || undefined,
    recipientName: filters.recipientName || undefined,
    recipientPhone: filters.recipientPhone || undefined,
    channelUserId: filters.channelUserId || undefined,
    recruiterUserId: filters.recruiterUserId || undefined,
    applyStartTime: toSampleLocalDateTime(filters.applyRange?.[0]),
    applyEndTime: toSampleLocalDateTime(filters.applyRange?.[1]),
    homeworkStartTime: toSampleLocalDateTime(filters.homeworkRange?.[0]),
    homeworkEndTime: toSampleLocalDateTime(filters.homeworkRange?.[1]),
    logisticsCompany: filters.logisticsCompany || undefined
  }
}
