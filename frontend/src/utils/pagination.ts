export const DEFAULT_PAGE = 1
export const DEFAULT_PAGE_SIZE = 20
export const MAX_PAGE_SIZE = 100
export const PAGE_SIZE_OPTIONS = [20, 50, 100] as const

export function normalizePage(value: unknown, fallback = DEFAULT_PAGE) {
  const page = Number(value)
  return Number.isFinite(page) && page > 0 ? Math.floor(page) : fallback
}

export function normalizePageSize(value: unknown, fallback = DEFAULT_PAGE_SIZE) {
  const pageSize = Number(value)
  if (!Number.isFinite(pageSize) || pageSize <= 0) return fallback
  return Math.min(Math.floor(pageSize), MAX_PAGE_SIZE)
}

export function createPaginationState() {
  return {
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
    itemCount: 0,
    showSizePicker: true,
    pageSizes: [...PAGE_SIZE_OPTIONS]
  }
}
