export const MAX_BATCH_PRODUCT_IDS = 100

export type BatchActionResult = {
  total?: number
  succeeded?: number
  failed?: number
  failures?: Array<{ productId?: string; reason?: string }>
}

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
