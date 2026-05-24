import { ref } from 'vue'

import { shouldShowPermissionHint } from '../utils/requestError'

export const globalPermissionHint = ref('')

export function setGlobalPermissionHint(message: string) {
  const text = String(message || '').trim()
  if (!text || !shouldShowPermissionHint(text)) return
  globalPermissionHint.value = text
}

export function clearGlobalPermissionHint() {
  globalPermissionHint.value = ''
}
