/**
 * 防抖工具模块
 *
 * 提供 Vue 组合式函数级别的防抖能力。
 * 在 Vue 组件 setup 上下文中使用时，会自动在组件卸载前清除未执行的定时器，防止内存泄漏。
 *
 * 典型使用场景：
 * - 搜索框输入延迟请求
 * - 窗口 resize 事件防抖
 * - 按钮点击防重复提交
 */

import { getCurrentInstance, onBeforeUnmount } from 'vue'

/**
 * 防抖函数类型，扩展了普通函数签名，额外暴露 cancel 方法用于取消待执行的防抖调用。
 *
 * @typeParam T - 被防抖的原始函数类型
 */
export type DebouncedFunction<T extends (...args: any[]) => void> = ((...args: Parameters<T>) => void) & {
  /** 取消当前排队等待执行的防抖调用 */
  cancel: () => void
}

/**
 * 创建一个防抖函数，在连续调用时只执行最后一次。
 *
 * 如果在 Vue 组件的 setup 上下文中调用（即 `getCurrentInstance()` 存在），
 * 会自动注册 `onBeforeUnmount` 生命周期钩子，在组件卸载前清除定时器。
 *
 * @typeParam T - 被防抖函数的类型
 * @param fn - 需要防抖的目标函数
 * @param delayMs - 防抖延迟毫秒数，默认 250ms
 * @returns 带有 cancel 方法的防抖包装函数
 *
 * @example
 * ```ts
 * // 在 Vue 组件中使用
 * const handleSearch = useDebouncedFn((keyword: string) => {
 *   fetchResults(keyword)
 * }, 300)
 *
 * // 输入变化时调用
 * handleSearch(inputValue)
 *
 * // 取消防抖
 * handleSearch.cancel()
 * ```
 */
export function useDebouncedFn<T extends (...args: any[]) => void>(fn: T, delayMs = 250): DebouncedFunction<T> {
  /** 当前等待执行的定时器 ID，null 表示无待执行任务 */
  let timer: ReturnType<typeof setTimeout> | null = null

  /**
   * 取消当前排队的防抖调用，清除定时器
   */
  const cancel = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  /**
   * 包装后的防抖函数：每次调用时重置定时器，仅在最后一次调用后经过 delayMs 毫秒才执行原始函数
   */
  const debounced = ((...args: Parameters<T>) => {
    cancel() // 清除上一次未执行的定时器
    timer = setTimeout(() => {
      timer = null
      fn(...args) // 延迟结束后执行原始函数，传递最后一次调用的参数
    }, delayMs)
  }) as DebouncedFunction<T>

  // 将 cancel 方法挂载到返回的函数上
  debounced.cancel = cancel

  // 如果当前在 Vue 组件 setup 上下文中，自动注册组件卸载前的清理逻辑
  if (getCurrentInstance()) {
    onBeforeUnmount(cancel)
  }

  return debounced
}
