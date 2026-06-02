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
  /**
   * 渠道负责人用户 ID 列表（多选）。
   * 改用数组后与数据权限范围（self/group/all）AND 叠加；空数组等同于不过滤。
   */
  channelUserIds: string[]
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

/**
 * 将多选渠道数组拆为后端接受的多次同名 channelUserIds 参数。
 * 配合 axios 的 paramsSerializer 默认行为会展开成 ?channelUserIds=...&channelUserIds=...
 * Spring MVC 的 @RequestParam List<UUID> 接收即可。
 */
export function buildCooperationSampleFilterParams(filters: CooperationWorkbenchFilters, activeStatus: string) {
  const params: Record<string, any> = {
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
    recruiterUserId: filters.recruiterUserId || undefined,
    applyStartTime: toSampleLocalDateTime(filters.applyRange?.[0]),
    applyEndTime: toSampleLocalDateTime(filters.applyRange?.[1]),
    homeworkStartTime: toSampleLocalDateTime(filters.homeworkRange?.[0]),
    homeworkEndTime: toSampleLocalDateTime(filters.homeworkRange?.[1]),
    logisticsCompany: filters.logisticsCompany || undefined
  }
  // 多选渠道：空数组不过滤，非空时透传数组（axios 默认展开为多次同名参数）
  if (filters.channelUserIds && filters.channelUserIds.length > 0) {
    params.channelUserIds = filters.channelUserIds.filter((id) => Boolean(id))
  }
  return params
}
