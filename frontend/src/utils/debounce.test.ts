/**
 * useDebouncedFn 单元测试
 *
 * 测试防抖函数的两个核心行为：
 * 1. 延迟期内只执行最后一次调用
 * 2. cancel 方法能正确取消待执行的调用
 */

import { describe, expect, it, vi } from 'vitest'

import { useDebouncedFn } from './debounce'

describe('useDebouncedFn', () => {
  // 验证在防抖延迟结束前连续调用多次，只有最后一次调用会被执行
  it('runs only the latest call after the debounce delay', async () => {
    vi.useFakeTimers()
    try {
      const fn = vi.fn()
      const debounced = useDebouncedFn(fn, 250)

      debounced('a')
      debounced('ab')
      await vi.advanceTimersByTimeAsync(249) // 延迟 249ms，函数尚未执行
      expect(fn).not.toHaveBeenCalled()

      await vi.advanceTimersByTimeAsync(1) // 再过 1ms，总计 250ms，触发执行
      expect(fn).toHaveBeenCalledOnce()
      expect(fn).toHaveBeenCalledWith('ab') // 应该使用最后一次调用的参数
    } finally {
      vi.useRealTimers()
    }
  })

  // 验证 cancel 方法能正确取消排队等待执行的防抖调用
  it('cancels a pending call', async () => {
    vi.useFakeTimers()
    try {
      const fn = vi.fn()
      const debounced = useDebouncedFn(fn, 250)

      debounced('keyword')
      debounced.cancel() // 立即取消
      await vi.advanceTimersByTimeAsync(250) // 即使延迟结束也不会执行

      expect(fn).not.toHaveBeenCalled()
    } finally {
      vi.useRealTimers()
    }
  })
})
