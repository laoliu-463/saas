import { getCurrentInstance, onBeforeUnmount } from 'vue'

export type DebouncedFunction<T extends (...args: any[]) => void> = ((...args: Parameters<T>) => void) & {
  cancel: () => void
}

export function useDebouncedFn<T extends (...args: any[]) => void>(fn: T, delayMs = 250): DebouncedFunction<T> {
  let timer: ReturnType<typeof setTimeout> | null = null

  const cancel = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  const debounced = ((...args: Parameters<T>) => {
    cancel()
    timer = setTimeout(() => {
      timer = null
      fn(...args)
    }, delayMs)
  }) as DebouncedFunction<T>

  debounced.cancel = cancel

  if (getCurrentInstance()) {
    onBeforeUnmount(cancel)
  }

  return debounced
}
