/**
 * 分页参数标准化工具模块
 *
 * 提供分页相关的常量定义和参数标准化函数，确保所有列表接口使用统一的分页规则。
 *
 * 分页规则：
 * - 默认页码：1
 * - 默认每页条数：20
 * - 最大每页条数：100
 * - 可选每页条数：[20, 50, 100]
 */

/** 默认页码 */
export const DEFAULT_PAGE = 1

/** 默认每页条数 */
export const DEFAULT_PAGE_SIZE = 20

/** 最大允许的每页条数，防止一次加载过多数据 */
export const MAX_PAGE_SIZE = 100

/** 可选的每页条数列表，用于 UI 分页选择器 */
export const PAGE_SIZE_OPTIONS = [20, 50, 100] as const

/**
 * 标准化页码值，确保返回合法的正整数页码。
 *
 * 校验规则：
 * - 非数字、非有限数（NaN、Infinity）、小于等于 0 的值 -> 返回 fallback
 * - 浮点数向下取整
 *
 * @param value - 原始页码值，可能为任意类型（如路由参数、用户输入）
 * @param fallback - 非法值时的回退值，默认为 DEFAULT_PAGE（1）
 * @returns 合法的正整数页码
 *
 * @example
 * ```ts
 * normalizePage(3)       // 3
 * normalizePage(0)       // 1（非法值，回退默认）
 * normalizePage('abc')   // 1（非数字，回退默认）
 * normalizePage(3.8)     // 3（向下取整）
 * ```
 */
export function normalizePage(value: unknown, fallback = DEFAULT_PAGE) {
  const page = Number(value)
  return Number.isFinite(page) && page > 0 ? Math.floor(page) : fallback
}

/**
 * 标准化每页条数，确保返回合法的正整数且不超过最大值。
 *
 * 校验规则：
 * - 非数字、非有限数、小于等于 0 的值 -> 返回 fallback
 * - 浮点数向下取整，超过 MAX_PAGE_SIZE 则截断到 MAX_PAGE_SIZE
 *
 * @param value - 原始每页条数值，可能为任意类型
 * @param fallback - 非法值时的回退值，默认为 DEFAULT_PAGE_SIZE（20）
 * @returns 合法且不超过最大值的每页条数
 *
 * @example
 * ```ts
 * normalizePageSize(50)     // 50
 * normalizePageSize(500)    // 100（截断到最大值）
 * normalizePageSize(0)      // 20（非法值，回退默认）
 * ```
 */
export function normalizePageSize(value: unknown, fallback = DEFAULT_PAGE_SIZE) {
  const pageSize = Number(value)
  if (!Number.isFinite(pageSize) || pageSize <= 0) return fallback
  return Math.min(Math.floor(pageSize), MAX_PAGE_SIZE)
}

/**
 * 创建初始分页状态对象，适用于 Naive UI 的 DataTable 等分页组件。
 *
 * @returns 包含默认分页配置的状态对象
 *
 * @example
 * ```ts
 * const pagination = reactive(createPaginationState())
 * // { page: 1, pageSize: 20, itemCount: 0, showSizePicker: true, pageSizes: [20, 50, 100] }
 * ```
 */
export function createPaginationState() {
  return {
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
    itemCount: 0,
    showSizePicker: true, // 允许用户选择每页条数
    pageSizes: [...PAGE_SIZE_OPTIONS] // 使用展开运算符创建独立副本
  }
}
