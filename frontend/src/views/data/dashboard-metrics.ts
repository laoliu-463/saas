/**
 * 仪表盘指标轨道类型
 * 用于承载单条轨道（settle / estimate）下的所有指标键值对
 */
export type DashboardMetricsTrack = Record<string, unknown>

/**
 * 双轨指标响应体
 * 后端 /dashboard/metrics 接口返回的 data 层结构
 * - settle: 按结算时间口径统计的指标
 * - estimate: 按下单时间口径统计的指标
 */
export interface DualTrackMetricsBody {
  settle?: DashboardMetricsTrack
  estimate?: DashboardMetricsTrack
}

/**
 * 解包 API 信封
 * 后端接口统一返回 { code, data, message } 信封格式，此函数提取 data 层
 * 兼容 payload 本身就是数据（无信封）的情况
 * @param payload 原始接口响应
 * @returns 解包后的数据对象
 */
export function unwrapApiEnvelope<T>(payload: unknown): T {
  const body = payload as { data?: T } | T | null | undefined
  if (body && typeof body === 'object' && 'data' in body && body.data !== undefined) {
    return body.data as T
  }
  return (body ?? {}) as T
}

/**
 * 解析双轨指标
 * 从 API 信封中提取 settle 和 estimate 两条轨道的指标数据
 * 若某条轨道缺失则回退为空对象，避免下游空指针
 * @param payload /dashboard/metrics 接口原始响应
 * @returns 包含 estimate 和 settle 的标准化指标对象
 */
export function resolveDualTrackMetrics(payload: unknown): {
  estimate: DashboardMetricsTrack
  settle: DashboardMetricsTrack
} {
  const body = unwrapApiEnvelope<DualTrackMetricsBody>(payload)
  return {
    estimate: (body.estimate ?? {}) as DashboardMetricsTrack,
    settle: (body.settle ?? {}) as DashboardMetricsTrack
  }
}

/**
 * 根据时间字段选取对应的指标轨道
 * - settleTime → settle 轨道（结算时间口径）
 * - createTime → estimate 轨道（下单时间口径）
 * @param payload /dashboard/metrics 接口原始响应
 * @param timeField 当前选择的时间维度
 * @returns 对应轨道的指标数据
 */
export function pickDashboardTrack(
  payload: unknown,
  timeField: 'createTime' | 'settleTime'
): DashboardMetricsTrack {
  const { estimate, settle } = resolveDualTrackMetrics(payload)
  return timeField === 'settleTime' ? settle : estimate
}
