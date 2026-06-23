import { onBeforeUnmount } from 'vue'

import { useAuthStore } from '../stores/auth'

export type RealtimeListTopic = 'orders' | 'products' | string

export interface RealtimeUpdateMessage {
  topic: RealtimeListTopic
  reason?: string
  entityId?: string | null
  occurredAt?: number
}

type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

interface StartRealtimeListInvalidationOptions {
  topics: RealtimeListTopic[]
  onInvalidate: (message: RealtimeUpdateMessage) => void
  getToken: () => string | null | undefined
  endpoint?: string
  fetchImpl?: FetchLike
  reconnect?: boolean
  reconnectDelayMs?: number
}

export function parseRealtimeSseMessages(input: string): {
  messages: RealtimeUpdateMessage[]
  remaining: string
} {
  const normalized = input.replace(/\r\n/g, '\n')
  const blocks = normalized.split('\n\n')
  const remaining = normalized.endsWith('\n\n') ? '' : (blocks.pop() || '')
  const completeBlocks = normalized.endsWith('\n\n') ? blocks.filter(Boolean) : blocks
  const messages: RealtimeUpdateMessage[] = []

  for (const block of completeBlocks) {
    const data = block
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n')
    if (!data) continue
    try {
      const parsed = JSON.parse(data)
      if (parsed && typeof parsed.topic === 'string') {
        messages.push(parsed)
      }
    } catch (error) {
      console.warn('[realtime] ignore invalid SSE payload', error)
    }
  }

  return { messages, remaining }
}

export function startRealtimeListInvalidation(options: StartRealtimeListInvalidationOptions) {
  const endpoint = options.endpoint || '/api/realtime/updates'
  const fetchImpl = options.fetchImpl || globalThis.fetch?.bind(globalThis)
  if (import.meta.env.MODE === 'test' && !options.fetchImpl) {
    return () => {}
  }
  const reconnect = options.reconnect !== false
  const reconnectDelayMs = options.reconnectDelayMs ?? 1000
  const topicSet = new Set(options.topics)
  let stopped = false
  let abortController: AbortController | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  const scheduleReconnect = () => {
    if (stopped || !reconnect) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      void connect()
    }, reconnectDelayMs)
  }

  const handleMessage = (message: RealtimeUpdateMessage) => {
    if (topicSet.size && !topicSet.has(message.topic)) return
    options.onInvalidate(message)
  }

  const connect = async () => {
    if (stopped || !fetchImpl) return
    const token = String(options.getToken?.() || '').trim()
    if (!token) return

    abortController = new AbortController()
    try {
      const response = await fetchImpl(endpoint, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
          'Cache-Control': 'no-cache'
        },
        signal: abortController.signal
      })
      if (!response.ok || !response.body) {
        throw new Error(`Realtime stream failed: ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (!stopped) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const parsed = parseRealtimeSseMessages(buffer)
        buffer = parsed.remaining
        parsed.messages.forEach(handleMessage)
      }
    } catch (error: any) {
      if (!stopped && error?.name !== 'AbortError') {
        console.warn('[realtime] stream disconnected', error)
      }
    } finally {
      abortController = null
      scheduleReconnect()
    }
  }

  void connect()

  return () => {
    stopped = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    abortController?.abort()
    abortController = null
  }
}

export function useRealtimeListInvalidation(
  topics: RealtimeListTopic[],
  onInvalidate: (message: RealtimeUpdateMessage) => void
) {
  const authStore = useAuthStore()
  const stop = startRealtimeListInvalidation({
    topics,
    onInvalidate,
    getToken: () => authStore.token
  })
  onBeforeUnmount(stop)
  return stop
}
