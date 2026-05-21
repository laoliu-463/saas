export type FrontendTimingKind = 'api' | 'router' | 'list';

export type FrontendTimingPayload = Record<string, unknown> & {
  durationMs?: number;
};

export function nowMs() {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now();
  }
  return Date.now();
}

export function recordFrontendTiming(
  kind: FrontendTimingKind,
  payload: FrontendTimingPayload,
  options: { failed?: boolean } = {}
) {
  const failed = Boolean(options.failed);
  const logger = failed ? console.warn : console.info;
  logger(`[${kind} timing]`, payload);

  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') {
    return;
  }

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
