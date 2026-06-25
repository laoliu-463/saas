import type { App } from 'vue'

type NaiveComponentGroup = 'admin' | 'product'

const installedGroups = new Set<NaiveComponentGroup>()

const resolveRouteGroup = (path: string): NaiveComponentGroup | null => {
  if (!path || path === '/login') return null
  return path.startsWith('/product') ? 'product' : 'admin'
}

export const installNaiveRouteComponents = async (app: App, path: string) => {
  const group = resolveRouteGroup(path)
  if (!group || installedGroups.has(group)) return

  if (group === 'product') {
    const { installProductNaiveComponents } = await import('./naive-product-components')
    app.use(installProductNaiveComponents)
  } else {
    const { installAdminNaiveComponents } = await import('./naive-admin-components')
    app.use(installAdminNaiveComponents)
  }

  installedGroups.add(group)
}
