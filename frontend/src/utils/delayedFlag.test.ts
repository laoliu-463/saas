import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

import { useDelayedFlag } from './delayedFlag'

describe('useDelayedFlag', () => {
  it('turns on only after the configured delay', async () => {
    vi.useFakeTimers()
    try {
      const source = ref(false)
      const delayed = useDelayedFlag(source, 200)

      source.value = true
      await nextTick()

      await vi.advanceTimersByTimeAsync(199)
      expect(delayed.value).toBe(false)

      await vi.advanceTimersByTimeAsync(1)
      expect(delayed.value).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })

  it('cancels the pending visible state when loading finishes quickly', async () => {
    vi.useFakeTimers()
    try {
      const source = ref(false)
      const delayed = useDelayedFlag(source, 200)

      source.value = true
      await nextTick()
      await vi.advanceTimersByTimeAsync(100)
      source.value = false
      await nextTick()
      await vi.advanceTimersByTimeAsync(200)

      expect(delayed.value).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })
})
