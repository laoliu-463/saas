import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'

import App from './App.vue'
import router from './router'
import { hasAccess } from './constants/rbac'
import { useAuthStore } from './stores/auth'
import './styles/tokens.css'
import './styles/global.css'

const app = createApp(App)

app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue Error]', err, info, instance)
}

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(naive)

const authStore = useAuthStore()
authStore.setupCrossTabSync(() => {
  const currentRoute = router.currentRoute.value
  if (currentRoute.path === '/login') return
  const requiredRoles = currentRoute.matched[currentRoute.matched.length - 1]?.meta?.roles as string[] | undefined
  if (!authStore.isLoggedIn || !hasAccess(authStore.roleCodes, requiredRoles)) {
    router.replace('/')
  }
})

app.mount('#app')

const dismissBootLoading = () => {
  window.requestAnimationFrame(() => {
    document.body.classList.add('app-ready')
    window.setTimeout(() => {
      document.getElementById('boot-loading')?.remove()
    }, 220)
  })
}

router.isReady().finally(dismissBootLoading)
// Docker + Windows 卷挂载时 Vite 首包可能极慢，避免 boot 层无限遮挡
window.setTimeout(dismissBootLoading, 45_000)
