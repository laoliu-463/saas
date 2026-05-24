import axios from 'axios'

const TRACE_BODY_KEYS = ['traceId', 'trace_id', 'requestId', 'request_id']
const TRACE_HEADER_KEYS = ['x-trace-id', 'x-request-id', 'trace-id', 'request-id']

export const FORBIDDEN_BUSINESS_CODE = 403

const shownPermissionHints = new Set<string>()

const cleanText = (value: unknown) => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  return text === 'null' || text === 'undefined' ? '' : text
}

const readHeader = (headers: any, key: string) => {
  if (!headers) return ''
  const direct = cleanText(headers[key])
  if (direct) return direct
  const matchedKey = Object.keys(headers).find((item) => item.toLowerCase() === key)
  return matchedKey ? cleanText(headers[matchedKey]) : ''
}

export function extractTraceId(input: any) {
  const data = input?.response?.data ?? input?.data ?? input
  for (const key of TRACE_BODY_KEYS) {
    const value = cleanText(data?.[key])
    if (value) return value
  }

  const headers = input?.response?.headers ?? input?.headers
  for (const key of TRACE_HEADER_KEYS) {
    const value = readHeader(headers, key)
    if (value) return value
  }

  return ''
}

export function resetPermissionHints() {
  shownPermissionHints.clear()
}

export function shouldShowPermissionHint(message: string): boolean {
  const key = cleanText(message)
  if (!key || shownPermissionHints.has(key)) return false
  shownPermissionHints.add(key)
  return true
}

export function isPermissionDeniedError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const payload = error as {
    code?: unknown
    __permissionDenied?: unknown
    response?: { status?: number; data?: { code?: unknown } }
  }
  if (payload.__permissionDenied === true) return true
  if (payload.code === FORBIDDEN_BUSINESS_CODE) return true
  if (payload.response?.status === FORBIDDEN_BUSINESS_CODE) return true
  if (payload.response?.data?.code === FORBIDDEN_BUSINESS_CODE) return true
  return false
}

export function extractRequestErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (!error || typeof error !== 'object') return fallback
  const payload = error as { msg?: unknown; message?: unknown; response?: { data?: { msg?: unknown } } }
  const raw = payload.msg ?? payload.response?.data?.msg ?? payload.message
  return cleanText(raw) || fallback
}

export function applyPermissionHint(
  error: unknown,
  setHint: (message: string) => void,
  fallback = '当前角色无权执行此操作'
): boolean {
  if (!isPermissionDeniedError(error)) return false
  const message = extractRequestErrorMessage(error, fallback)
  if (shouldShowPermissionHint(message)) {
    setHint(message)
  }
  return true
}

/** True when the global axios interceptor already handled the error (toast or silent permission). */
export function isRequestErrorNotified(error: unknown): boolean {
  if (axios.isCancel(error)) return true
  if (isPermissionDeniedError(error)) return true
  if (!error || typeof error !== 'object') return false
  const payload = error as { code?: unknown; response?: { status?: number } }
  if (typeof payload.code === 'number' && payload.code !== 200) return true
  return typeof payload.response?.status === 'number'
}

export function handleApiFailure(
  error: unknown,
  options: {
    onPermissionHint?: (message: string) => void
    permissionFallback?: string
    onFallback?: (message: string) => void
    fallbackMessage?: string
  }
): void {
  if (applyPermissionHint(error, options.onPermissionHint ?? (() => {}), options.permissionFallback)) {
    return
  }
  if (isRequestErrorNotified(error)) return
  options.onFallback?.(extractRequestErrorMessage(error, options.fallbackMessage))
}
