import { describe, expect, it, vi } from 'vitest'

import { useDebouncedFn } from './debounce'

describe('useDebouncedFn', () => {
  it('runs only the latest call after the debounce delay', async () => {
    vi.useFakeTimers()
    try {
      const fn = vi.fn()
      const debounced = useDebouncedFn(fn, 250)

      debounced('a')
      debounced('ab')
      await vi.advanceTimersByTimeAsync(249)
      expect(fn).not.toHaveBeenCalled()

      await vi.advanceTimersByTimeAsync(1)
      expect(fn).toHaveBeenCalledOnce()
      expect(fn).toHaveBeenCalledWith('ab')
    } finally {
      vi.useRealTimers()
    }
  })

  it('cancels a pending call', async () => {
    vi.useFakeTimers()
    try {
      const fn = vi.fn()
      const debounced = useDebouncedFn(fn, 250)

      debounced('keyword')
      debounced.cancel()
      await vi.advanceTimersByTimeAsync(250)

      expect(fn).not.toHaveBeenCalled()
    } finally {
      vi.useRealTimers()
    }
  })
})
