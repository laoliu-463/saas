import { ref } from 'vue'

export const globalPermissionHint = ref('')

export function clearGlobalPermissionHint() {
  globalPermissionHint.value = ''
}
