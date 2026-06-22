export const PRODUCT_MANAGE_PRODUCTS_PATH = '/product/manage/products'

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
