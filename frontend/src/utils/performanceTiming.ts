/**
 * 前端性能计时工具模块
 *
 * 提供前端性能数据的采集和事件派发能力，用于监控 API 请求耗时、路由切换耗时等。
 *
 * 计时数据流向：
 * 1. 通过 console.info / console.warn 输出到浏览器控制台（便于开发调试）
 * 2. 通过 CustomEvent('frontend:timing') 派发到 window，供外部监听器（如性能监控面板）消费
 *
 * 支持的计时类型（FrontendTimingKind）：
 * - 'api'：API 请求计时
 * - 'router'：路由切换计时
 * - 'list'：列表加载计时
 */

/**
 * 前端计时事件类型枚举
 *
 * - `'api'`：HTTP API 请求
 * - `'router'`：前端路由切换
 * - `'list'`：列表/数据加载
 */
export type FrontendTimingKind = 'api' | 'router' | 'list'

/**
 * 前端计时事件负载类型。
 * 允许携带任意键值对，可选包含 durationMs 字段表示耗时（毫秒）。
 *
 * @property durationMs - 操作耗时，单位毫秒
 */
export type FrontendTimingPayload = Record<string, unknown> & {
  durationMs?: number;
}

/**
 * 获取当前高精度时间戳（毫秒）。
 *
 * 优先使用 performance.now()（亚毫秒级精度，不受系统时间调整影响），
 * 浏览器不支持时回退到 Date.now()。
 *
 * @returns 当前时间戳，单位毫秒
 */
export function nowMs() {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
}

/**
 * 记录前端计时数据。
 *
 * 同时输出到控制台（开发调试）和派发 CustomEvent（性能监控消费）。
 * 成功操作使用 console.info，失败操作使用 console.warn。
 *
 * @param kind - 计时事件类型
 * @param payload - 计时数据负载，可包含任意业务字段
 * @param options - 可选配置
 * @param options.failed - 是否为失败操作，默认 false
 *
 * @example
 * ```ts
 * // 记录 API 请求耗时
 * recordFrontendTiming('api', {
 *   method: 'GET',
 *   url: '/api/orders',
 *   status: 200,
 *   durationMs: 150
 * })
 *
 * // 记录失败的路由切换
 * recordFrontendTiming('router', { to: '/dashboard', durationMs: 3000 }, { failed: true })
 * ```
 */
export function recordFrontendTiming(
  kind: FrontendTimingKind,
  payload: FrontendTimingPayload,
  options: { failed?: boolean } = {}
) {
  const failed = Boolean(options.failed);
  // 失败操作用 warn 级别，成功用 info 级别，便于在控制台快速区分
  const logger = failed ? console.warn : console.info;
  logger(`[${kind} timing]`, payload);

  // SSR 环境安全检查：非浏览器环境下不派发事件
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') {
    return;
  }

  // 派发自定义事件，外部监听器可通过 window.addEventListener('frontend:timing', ...) 接收
  window.dispatchEvent(
    new CustomEvent('frontend:timing', {
      detail: {
        kind,
        failed,
        ...payload
      }
    })
  );
}
