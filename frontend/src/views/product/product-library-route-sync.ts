/**
 * 商品库活动筛选的路由 ↔ filters 同步逻辑（ADR-003）。
 *
 * 单一事实源：URL 的 `?activityId=xxx` query。
 * - 列表 / 路由重定向 / 外部 push 都会改 URL，本模块负责把 URL 状态映射成
 *   前端 `filters.activityId` 与"已按活动筛选"展示提示。
 * - 硬筛选语义：进入即生效，重置 / 清除时主动清 URL，保证 query 与 filters
 *   不会漂移。
 *
 * 把这一段抽成纯函数模块，目的是让单测可以不挂 vue-router / SFC mount
 * 就能覆盖"query 改变 → filters 同步"、"清除活动筛选时 query 怎么变"等
 * 关键行为。
 */

export type RouteQueryLike = Record<string, string | (string | null)[] | null | undefined>

const readSingleQueryValue = (value: string | (string | null)[] | null | undefined): string => {
  if (Array.isArray(value)) {
    for (const item of value) {
      if (item == null) continue
      const text = String(item).trim()
      if (text) return text
    }
    return ''
  }
  if (value == null) return ''
  return String(value).trim()
}

/**
 * 从 route.query 中读 activityId。
 * - 空 / null / 数组首项为空 → 返回空字符串
 * - 数组 → 取第一个非空项（与 vue-router 行为一致）
 */
export function resolveActivityIdFromQuery(query: RouteQueryLike): string {
  return readSingleQueryValue(query?.activityId)
}

/**
 * 构造"清除 activityId 之后"的新 query 副本。
 * 其它键原样保留（包括分页、排序等），仅删除 activityId 这一项。
 */
export function buildQueryWithoutActivityId(query: RouteQueryLike): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(query ?? {})) {
    if (key === 'activityId') continue
    if (value == null) continue
    if (Array.isArray(value)) {
      const first = value.find((item) => item != null && String(item).trim() !== '')
      if (first != null) result[key] = String(first)
    } else {
      const text = String(value).trim()
      if (text) result[key] = text
    }
  }
  return result
}

/**
 * 判断两个 activityId 是否"语义相等"（都视作已 trim 的字符串）。
 * 用于避免"同值 set"触发不必要的查询。
 */
export function isSameActivityId(a: string | null | undefined, b: string | null | undefined): boolean {
  const left = (a ?? '').trim() || null
  const right = (b ?? '').trim() || null
  return left === right
}
