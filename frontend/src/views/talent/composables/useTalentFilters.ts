import { computed, reactive } from 'vue'
import { DEFAULT_TALENT_FILTERS } from '../constants'
import { resolveFansBandRange, type TalentFansBand } from '../talent-filter-options'

export interface TalentFiltersState {
  category: string | null
  liveSalesBand: string | null
  liveViewBand: string | null
  liveGpmBand: string | null
  videoSalesBand: string | null
  videoPlayBand: string | null
  videoGpmBand: string | null
  level: string | null
  fansBand: string | null
  region: string
  douyinNo: string
  nickname: string
  contactStatus: string | null
  claimStatus: string | null
  keyword: string
}

const defaults = (): TalentFiltersState => ({
  category: DEFAULT_TALENT_FILTERS.category,
  liveSalesBand: DEFAULT_TALENT_FILTERS.liveSalesBand,
  liveViewBand: DEFAULT_TALENT_FILTERS.liveViewBand,
  liveGpmBand: DEFAULT_TALENT_FILTERS.liveGpmBand,
  videoSalesBand: DEFAULT_TALENT_FILTERS.videoSalesBand,
  videoPlayBand: DEFAULT_TALENT_FILTERS.videoPlayBand,
  videoGpmBand: DEFAULT_TALENT_FILTERS.videoGpmBand,
  level: DEFAULT_TALENT_FILTERS.level,
  fansBand: DEFAULT_TALENT_FILTERS.fansBand,
  region: DEFAULT_TALENT_FILTERS.region,
  douyinNo: DEFAULT_TALENT_FILTERS.douyinNo,
  nickname: DEFAULT_TALENT_FILTERS.nickname,
  contactStatus: DEFAULT_TALENT_FILTERS.contactStatus,
  claimStatus: DEFAULT_TALENT_FILTERS.claimStatus,
  keyword: DEFAULT_TALENT_FILTERS.keyword
})

export function useTalentFilters() {
  const filters = reactive<TalentFiltersState>(defaults())

  const hasActiveFilters = computed(() =>
    Boolean(
      filters.category ||
      filters.liveSalesBand ||
      filters.liveViewBand ||
      filters.liveGpmBand ||
      filters.videoSalesBand ||
      filters.videoPlayBand ||
      filters.videoGpmBand ||
      filters.level ||
      filters.fansBand ||
      filters.region.trim() ||
      filters.douyinNo.trim() ||
      filters.nickname.trim() ||
      filters.contactStatus ||
      filters.claimStatus ||
      filters.keyword.trim()
    )
  )

  function resetFilters() {
    Object.assign(filters, defaults())
  }

  function applyQuery(query: Record<string, unknown>) {
    filters.category = typeof query.category === 'string' ? query.category : null
    filters.liveSalesBand = parseNullableString(query.liveSalesBand)
    filters.liveViewBand = parseNullableString(query.liveViewBand)
    filters.liveGpmBand = parseNullableString(query.liveGpmBand)
    filters.videoSalesBand = parseNullableString(query.videoSalesBand)
    filters.videoPlayBand = parseNullableString(query.videoPlayBand)
    filters.videoGpmBand = parseNullableString(query.videoGpmBand)
    filters.level = parseNullableString(query.level)
    filters.fansBand = parseNullableString(query.fansBand)
    filters.claimStatus = parseNullableString(query.claimStatus)
    filters.contactStatus = parseNullableString(query.contactStatus)
    filters.region = typeof query.region === 'string' ? query.region : ''
    filters.douyinNo = typeof query.douyinNo === 'string' ? query.douyinNo : ''
    filters.nickname = typeof query.nickname === 'string' ? query.nickname : ''
    filters.keyword = typeof query.keyword === 'string' ? query.keyword : ''
    if (!filters.douyinNo && !filters.nickname && filters.keyword) {
      filters.nickname = filters.keyword
    }
  }

  function toRequestParams(activeView: string) {
    const params: Record<string, string | number> = { view: activeView }
    if (filters.category) params.category = filters.category
    if (filters.claimStatus) params.claimStatus = filters.claimStatus
    if (filters.region.trim()) params.region = filters.region.trim()
    const douyinNo = filters.douyinNo.trim()
    const nickname = filters.nickname.trim()
    if (douyinNo) params.douyinNo = douyinNo
    if (nickname) params.nickname = nickname
    if (douyinNo || nickname) {
      params.keyword = nickname || douyinNo
    } else if (filters.keyword.trim()) {
      params.keyword = filters.keyword.trim()
    }
    if (filters.liveSalesBand) params.liveSalesBand = filters.liveSalesBand
    if (filters.liveViewBand) params.liveViewBand = filters.liveViewBand
    if (filters.liveGpmBand) params.liveGpmBand = filters.liveGpmBand
    if (filters.videoSalesBand) params.videoSalesBand = filters.videoSalesBand
    if (filters.videoPlayBand) params.videoPlayBand = filters.videoPlayBand
    if (filters.videoGpmBand) params.videoGpmBand = filters.videoGpmBand
    if (filters.level) params.level = filters.level
    if (filters.contactStatus) params.contactStatus = filters.contactStatus

    const fansRange = resolveFansBandRange(filters.fansBand as TalentFansBand)
    if (fansRange.minFans !== undefined) params.minFans = fansRange.minFans
    if (fansRange.maxFans !== undefined) params.maxFans = fansRange.maxFans

    return params
  }

  function toRouteQuery(activeView: string) {
    return {
      view: activeView,
      category: filters.category || undefined,
      liveSalesBand: filters.liveSalesBand || undefined,
      liveViewBand: filters.liveViewBand || undefined,
      liveGpmBand: filters.liveGpmBand || undefined,
      videoSalesBand: filters.videoSalesBand || undefined,
      videoPlayBand: filters.videoPlayBand || undefined,
      videoGpmBand: filters.videoGpmBand || undefined,
      level: filters.level || undefined,
      fansBand: filters.fansBand || undefined,
      contactStatus: filters.contactStatus || undefined,
      claimStatus: filters.claimStatus || undefined,
      region: filters.region || undefined,
      douyinNo: filters.douyinNo || undefined,
      nickname: filters.nickname || undefined,
      keyword: filters.keyword || undefined
    }
  }

  return {
    filters,
    hasActiveFilters,
    resetFilters,
    applyQuery,
    toRequestParams,
    toRouteQuery
  }
}

function parseNullableString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : null
}
