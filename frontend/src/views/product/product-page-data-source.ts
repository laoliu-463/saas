export const PRODUCT_MANAGE_PRODUCTS_PATH = '/product/manage/products'

export type ProductManageActivityContextStatus = 'ready' | 'empty' | 'forbidden' | 'loading'

export interface AssignedActivityOption {
  label: string
  value: string
}

export interface ProductManageActivityContext {
  status: ProductManageActivityContextStatus
  activityId?: string
  activityName?: string
}

export function isProductManageProductsPath(path: string) {
  return String(path || '').trim() === PRODUCT_MANAGE_PRODUCTS_PATH
}

export function shouldLoadActivityProducts(routePath: string, hasExplicitActivityRoute: boolean) {
  return hasExplicitActivityRoute || isProductManageProductsPath(routePath)
}

export function normalizeActivityQueryId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  return String(raw || '').trim()
}

export function buildActivityProductListRoute(activityId: unknown) {
  const id = normalizeActivityQueryId(activityId)
  return {
    path: PRODUCT_MANAGE_PRODUCTS_PATH,
    query: id ? { activityId: id } : {}
  }
}

function activityNameFromOption(option: AssignedActivityOption | undefined) {
  return String(option?.label || '').replace(/\s*\([^)]*\)\s*$/, '').trim()
}

export function resolveActivityContextForManageProductsPath(
  route: { path?: string, query?: Record<string, unknown> },
  assignedOptions: AssignedActivityOption[],
  state: { loading?: boolean } = {}
): ProductManageActivityContext {
  if (!isProductManageProductsPath(String(route?.path || ''))) {
    return { status: 'ready' }
  }
  const activityId = normalizeActivityQueryId(route?.query?.activityId || route?.query?.recruitActivityId)
  if (!activityId) {
    return state.loading ? { status: 'loading' } : { status: 'empty' }
  }
  if (state.loading) {
    return { status: 'loading', activityId }
  }
  const matched = assignedOptions.find((option) => String(option.value) === activityId)
  if (!matched) {
    return { status: 'forbidden', activityId }
  }
  return {
    status: 'ready',
    activityId,
    activityName: activityNameFromOption(matched) || undefined
  }
}
