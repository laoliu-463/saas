const TRACE_BODY_KEYS = ['traceId', 'trace_id', 'requestId', 'request_id']
const TRACE_HEADER_KEYS = ['x-trace-id', 'x-request-id', 'trace-id', 'request-id']

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
