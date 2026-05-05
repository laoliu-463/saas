import { computed, reactive } from 'vue'
import { DEFAULT_TALENT_FILTERS } from '../constants'

export interface TalentFiltersState {
  keyword: string
  category: string | null
  claimStatus: string | null
  minFans: number | null
  maxFans: number | null
  region: string
}

const defaults = () => ({
  keyword: DEFAULT_TALENT_FILTERS.keyword,
  category: DEFAULT_TALENT_FILTERS.category,
  claimStatus: DEFAULT_TALENT_FILTERS.claimStatus,
  minFans: DEFAULT_TALENT_FILTERS.minFans,
  maxFans: DEFAULT_TALENT_FILTERS.maxFans,
  region: DEFAULT_TALENT_FILTERS.region
})

export function useTalentFilters() {
  const filters = reactive<TalentFiltersState>(defaults())

  const hasActiveFilters = computed(() =>
    Boolean(
      filters.keyword ||
      filters.category ||
      filters.claimStatus ||
      filters.region ||
      filters.minFans !== null ||
      filters.maxFans !== null
    )
  )

  function resetFilters() {
    Object.assign(filters, defaults())
  }

  function applyQuery(query: Record<string, unknown>) {
    filters.keyword = typeof query.keyword === 'string' ? query.keyword : ''
    filters.category = typeof query.category === 'string' ? query.category : null
    filters.claimStatus = typeof query.claimStatus === 'string' ? query.claimStatus : null
    filters.region = typeof query.region === 'string' ? query.region : ''
    filters.minFans = parseQueryNumber(query.minFans)
    filters.maxFans = parseQueryNumber(query.maxFans)
  }

  function toRequestParams(activeView: string) {
    const params: Record<string, string | number> = {
      view: activeView
    }
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    if (filters.category) params.category = filters.category
    if (filters.claimStatus) params.claimStatus = filters.claimStatus
    if (filters.region.trim()) params.region = filters.region.trim()
    if (filters.minFans !== null && filters.minFans > 0) params.minFans = filters.minFans
    if (filters.maxFans !== null && filters.maxFans > 0) params.maxFans = filters.maxFans
    return params
  }

  function toRouteQuery(activeView: string) {
    return {
      view: activeView,
      keyword: filters.keyword || undefined,
      category: filters.category || undefined,
      claimStatus: filters.claimStatus || undefined,
      region: filters.region || undefined,
      minFans: filters.minFans !== null && filters.minFans > 0 ? filters.minFans : undefined,
      maxFans: filters.maxFans !== null && filters.maxFans > 0 ? filters.maxFans : undefined
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

function parseQueryNumber(value: unknown) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value !== 'string' || !value.trim()) return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}
