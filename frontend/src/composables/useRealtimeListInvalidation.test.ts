import { describe, expect, it, vi } from 'vitest'

import { startRealtimeListInvalidation } from './useRealtimeListInvalidation'

function streamFromChunks(chunks: string[]) {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      const encoder = new TextEncoder()
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    }
  })
}

async function waitUntil(predicate: () => boolean) {
  const startedAt = Date.now()
  while (!predicate()) {
    if (Date.now() - startedAt > 1000) {
      throw new Error('timeout waiting for predicate')
    }
    await new Promise((resolve) => setTimeout(resolve, 10))
  }
}

describe('startRealtimeListInvalidation', () => {
  it('uses bearer token and calls callback for subscribed topic from SSE stream', async () => {
    const received: unknown[] = []
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      body: streamFromChunks([
        'data: {"topic":"products","reason":"ACTIVITY_SYNC_COMPLETED","entityId":"ACT-001"',
        ',"occurredAt":123}\n\n'
      ])
    })

    const stop = startRealtimeListInvalidation({
      topics: ['products'],
      getToken: () => 'TOKEN-001',
      fetchImpl: fetchImpl as any,
      reconnect: false,
      onInvalidate: (message) => received.push(message)
    })

    await waitUntil(() => received.length === 1)

    expect(fetchImpl).toHaveBeenCalledWith('/api/realtime/updates', expect.objectContaining({
      headers: expect.objectContaining({
        Accept: 'text/event-stream',
        Authorization: 'Bearer TOKEN-001'
      })
    }))
    expect(received[0]).toMatchObject({
      topic: 'products',
      reason: 'ACTIVITY_SYNC_COMPLETED',
      entityId: 'ACT-001'
    })

    stop()
  })

  it('ignores events for unsubscribed topics', async () => {
    const onInvalidate = vi.fn()
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      body: streamFromChunks([
        'data: {"topic":"orders","reason":"ORDER_SYNCED","entityId":"ORDER-001","occurredAt":123}\n\n'
      ])
    })

    const stop = startRealtimeListInvalidation({
      topics: ['products'],
      getToken: () => 'TOKEN-001',
      fetchImpl: fetchImpl as any,
      reconnect: false,
      onInvalidate
    })

    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(onInvalidate).not.toHaveBeenCalled()
    stop()
  })
})
