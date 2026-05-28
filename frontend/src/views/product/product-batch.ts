/**
 * 商品批量操作工具模块
 *
 * 提供批量操作的 ID 规范化和结果消息格式化功能。
 * 使用场景：批量置顶、批量加入商品库、批量分配等商品批量操作。
 */

/** 单次批量操作最大商品数量限制 */
export const MAX_BATCH_PRODUCT_IDS = 100

/**
 * 批量操作结果类型
 * 用于描述批量操作（如批量加入商品库、批量置顶）的执行结果
 */
export type BatchActionResult = {
  /** 操作总数 */
  total?: number
  /** 成功数量 */
  succeeded?: number
  /** 失败数量 */
  failed?: number
  /** 失败明细列表，包含商品 ID 和失败原因 */
  failures?: Array<{ productId?: string; reason?: string }>
}

/**
 * 规范化批量商品 ID 列表
 * - 去除空白和重复项
 * - 截断到 MAX_BATCH_PRODUCT_IDS（100）上限
 *
 * @param ids 原始商品 ID 数组
 * @returns 去重、截断后的有效 ID 数组
 */
export function normalizeBatchProductIds(ids: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const raw of ids) {
    const id = String(raw || '').trim()
    if (!id || seen.has(id)) continue
    seen.add(id)
    result.push(id)
  }
  return result.slice(0, MAX_BATCH_PRODUCT_IDS)
}

/**
 * 格式化批量操作结果消息
 * - 全部成功时显示 "X/Y 成功"
 * - 存在失败时显示成功和失败数量，并附带第一个失败原因示例
 *
 * @param result 批量操作结果
 * @param actionLabel 操作描述标签（如 "批量置顶"、"批量加入商品库"）
 * @returns 格式化的结果消息字符串
 */
export function formatBatchResultMessage(result: BatchActionResult, actionLabel: string): string {
  const total = Number(result.total ?? 0)
  const succeeded = Number(result.succeeded ?? 0)
  const failed = Number(result.failed ?? 0)
  if (failed <= 0) {
    return `${actionLabel}完成：${succeeded}/${total} 成功`
  }
  const firstReason = result.failures?.find((item) => item?.reason)?.reason
  return `${actionLabel}完成：成功 ${succeeded}，失败 ${failed}${firstReason ? `（示例：${firstReason}）` : ''}`
}
