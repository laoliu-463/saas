import { getCurrentInstance, onBeforeUnmount, ref, watch, type Ref } from 'vue'

export function useDelayedFlag(source: Ref<boolean>, delayMs = 200) {
  const visible = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  watch(
    source,
    (value) => {
      clearTimer()
      if (!value) {
        visible.value = false
        return
      }
      timer = setTimeout(() => {
        visible.value = true
        timer = null
      }, delayMs)
    },
    { immediate: true }
  )

  if (getCurrentInstance()) {
    onBeforeUnmount(clearTimer)
  }

  return visible
}
