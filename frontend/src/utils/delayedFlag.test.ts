/**
 * useDelayedFlag 单元测试
 *
 * 测试延迟标志的两个核心场景：
 * 1. 加载超过延迟时间后，标志正确变为 true
 * 2. 快速完成的加载（短于延迟时间），标志保持 false 不闪烁
 */

import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

import { useDelayedFlag } from './delayedFlag'

describe('useDelayedFlag', () => {
  // 验证：source 为 true 后经过指定延迟，visible 才变为 true
  it('turns on only after the configured delay', async () => {
    vi.useFakeTimers()
    try {
      const source = ref(false) // 模拟 loading 状态
      const delayed = useDelayedFlag(source, 200)

      source.value = true // 触发加载开始
      await nextTick()

      await vi.advanceTimersByTimeAsync(199) // 未满 200ms
      expect(delayed.value).toBe(false) // 仍然隐藏

      await vi.advanceTimersByTimeAsync(1) // 满 200ms
      expect(delayed.value).toBe(true) // 延迟后显示
    } finally {
      vi.useRealTimers()
    }
  })

  // 验证：加载在延迟期内完成时，visible 永远不会变为 true（避免闪烁）
  it('cancels the pending visible state when loading finishes quickly', async () => {
    vi.useFakeTimers()
    try {
      const source = ref(false) // 模拟 loading 状态
      const delayed = useDelayedFlag(source, 200)

      source.value = true // 开始加载
      await nextTick()
      await vi.advanceTimersByTimeAsync(100) // 仅过了 100ms
      source.value = false // 加载完成（快于 200ms 延迟）
      await nextTick()
      await vi.advanceTimersByTimeAsync(200) // 即使再等 200ms

      expect(delayed.value).toBe(false) // 标志始终为 false，无闪烁
    } finally {
      vi.useRealTimers()
    }
  })
})
