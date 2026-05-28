/**
 * 延迟标志工具模块
 *
 * 提供延迟显示标志的 Vue 组合式函数，用于避免加载状态的"闪烁"问题。
 *
 * 典型使用场景：
 * - 短暂加载时延迟显示 loading 骨架屏，避免快速操作时 UI 闪烁
 * - 网络请求中延迟展示加载指示器，提升用户体验
 *
 * 工作原理：
 * 当 source 变为 true 时，延迟 delayMs 毫秒后才将 visible 设为 true；
 * 如果在延迟期内 source 变回 false（如加载完成），则取消延迟，visible 保持 false。
 */

import { getCurrentInstance, onBeforeUnmount, ref, watch, type Ref } from 'vue'

/**
 * 创建一个延迟激活的布尔标志。
 *
 * 当 `source` 从 false 变为 true 时，经过 `delayMs` 毫秒后 `visible` 才变为 true。
 * 如果在延迟期内 `source` 变回 false，则 `visible` 立即为 false，不会出现延迟后闪烁。
 *
 * 自动管理定时器生命周期：在 Vue 组件卸载前清除未完成的定时器。
 *
 * @param source - 源响应式布尔值，通常绑定 loading 状态
 * @param delayMs - 延迟激活的毫秒数，默认 200ms
 * @returns 延迟后的响应式布尔值，可直接用于模板渲染
 *
 * @example
 * ```vue
 * <script setup>
 * const loading = ref(false)
 * const showSpinner = useDelayedFlag(loading, 300)
 * </script>
 * <template>
 *   <!-- 仅在加载超过 300ms 时才显示加载动画，避免短暂闪烁 -->
 *   <div v-if="showSpinner">加载中...</div>
 * </template>
 * ```
 */
export function useDelayedFlag(source: Ref<boolean>, delayMs = 200) {
  /** 延迟后的可见状态，只有经过延迟后才变为 true */
  const visible = ref(false)
  /** 当前活跃的定时器 ID */
  let timer: ReturnType<typeof setTimeout> | null = null

  /**
   * 清除当前活跃的定时器
   */
  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  // 监听 source 的变化，根据新值决定启动延迟或立即关闭
  watch(
    source,
    (value) => {
      clearTimer()
      if (!value) {
        // source 变为 false（如加载完成），立即隐藏并取消延迟
        visible.value = false
        return
      }
      // source 变为 true（如开始加载），启动延迟定时器
      timer = setTimeout(() => {
        visible.value = true
        timer = null
      }, delayMs)
    },
    { immediate: true } // 立即执行一次，处理 source 初始值为 true 的情况
  )

  // 如果在 Vue 组件 setup 中使用，自动在卸载前清除定时器
  if (getCurrentInstance()) {
    onBeforeUnmount(clearTimer)
  }

  return visible
}
