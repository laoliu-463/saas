export type DashboardMetricsTrack = Record<string, unknown>

export interface DualTrackMetricsBody {
  settle?: DashboardMetricsTrack
  estimate?: DashboardMetricsTrack
}

export function unwrapApiEnvelope<T>(payload: unknown): T {
  const body = payload as { data?: T } | T | null | undefined
  if (body && typeof body === 'object' && 'data' in body && body.data !== undefined) {
    return body.data as T
  }
  return (body ?? {}) as T
}

/** `/dashboard/metrics` returns `{ settle, estimate }` under the API envelope. */
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

export function pickDashboardTrack(
  payload: unknown,
  timeField: 'createTime' | 'settleTime'
): DashboardMetricsTrack {
  const { estimate, settle } = resolveDualTrackMetrics(payload)
  return timeField === 'settleTime' ? settle : estimate
}
