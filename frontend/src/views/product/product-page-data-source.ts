/**
 * @module product-page-data-source
 * @description 商品管理页面的"路径 → 活动上下文"判定纯函数。
 *
 * 用于解决 [PRODUCT-FIX-001] /product/manage/products 无 query 时
 * fallback 到 assigned[0] 导致数据归属错位的 bug。
 *
 * 关键约束（ADR-007）：
 * - 活动列表与商品库入口路由统一为 query 参数
 * - 无 query 时必须显式空态，不允许静默 fallback
 */

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

/**
 * 活动上下文状态。
 *
 * - `ready`     : 用户显式选择了一个有权访问的活动，可以加载商品
 * - `empty`     : 没有 query，请用户先选择活动（不允许 fallback）
 * - `forbidden` : 有 query 但不在用户有权访问的 assigned 列表里
 * - `loading`   : 用户有合法 query 但 assigned options 还没加载完成（用于显示 loading）
 */
export type ActivityContextStatus = 'ready' | 'empty' | 'forbidden' | 'loading'

export type ActivityContext = {
  status: ActivityContextStatus
  activityId?: string
  activityName?: string
}

/**
 * 解析 /product/manage/products 路径的活动上下文。
 *
 * 这是 [PRODUCT-FIX-001] 的核心 seam：
 * - 无 query → 不再 fallback，返回 status='empty' 让页面渲染空态
 * - 有 query 且命中 assigned → status='ready'
 * - 有 query 但不命中 assigned → status='forbidden'
 * - 有 query 且 assigned 还在加载 → status='loading'
 *
 * @param routePath        - 当前路由 path
 * @param queryActivityId  - 当前路由 query.activityId
 * @param assignedOptions  - 当前用户被分配的活动列表（label/value 数组）
 * @param isAdmin          - 是否是 admin（admin 跳过 assigned 检查）
 * @param isOptionsLoading - assignedOptions 是否还在加载
 */
export function resolveActivityContextForManageProductsPath(args: {
  routePath: string
  queryActivityId: string
  assignedOptions: ReadonlyArray<{ label: string; value: string }>
  isAdmin: boolean
  isOptionsLoading: boolean
}): ActivityContext {
  const { routePath, queryActivityId, assignedOptions, isAdmin, isOptionsLoading } = args
  // 只对 /product/manage/products 路径生效
  if (!isProductManageProductsPath(routePath)) {
    return { status: 'empty' }
  }
  // 无 query → 强制空态，禁止 fallback
  if (!queryActivityId) {
    return { status: 'empty' }
  }
  // admin 跳过 assigned 检查（admin 可看任何活动）
  if (isAdmin) {
    return { status: 'ready', activityId: queryActivityId }
  }
  // assigned 还在加载
  if (isOptionsLoading) {
    return { status: 'loading', activityId: queryActivityId }
  }
  // 命中 assigned
  const matched = assignedOptions.find((opt) => opt.value === queryActivityId)
  if (matched) {
    return { status: 'ready', activityId: matched.value, activityName: matched.label }
  }
  // 不在 assigned 列表
  return { status: 'forbidden', activityId: queryActivityId }
}