export const PRODUCT_MANAGE_PRODUCTS_PATH = '/product/manage/products'

export function isProductManageProductsPath(path: string) {
  return String(path || '').trim() === PRODUCT_MANAGE_PRODUCTS_PATH
}

export function shouldLoadActivityProducts(routePath: string, hasExplicitActivityRoute: boolean) {
  return hasExplicitActivityRoute || isProductManageProductsPath(routePath)
}
