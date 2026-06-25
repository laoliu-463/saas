import type { App } from 'vue'

type NaiveComponentGroup = 'data' | 'product' | 'sample' | 'system' | 'talent'

const installedGroups = new Set<NaiveComponentGroup>()

const resolveRouteGroup = (path: string): NaiveComponentGroup | null => {
  if (!path || path === '/login') return null

  if (path.startsWith('/product')) return 'product'
  if (path.startsWith('/sample')) return 'sample'
  if (path.startsWith('/talent')) return 'talent'
  if (path.startsWith('/system') || path.startsWith('/ops') || path.startsWith('/profile')) {
    return 'system'
  }

  return 'data'
}

export const installNaiveRouteComponents = async (app: App, path: string) => {
  const group = resolveRouteGroup(path)
  if (!group || installedGroups.has(group)) return

  if (group === 'product') {
    const { installProductNaiveComponents } = await import('./naive-product-components')
    app.use(installProductNaiveComponents)
  } else if (group === 'sample') {
    const { installSampleNaiveComponents } = await import('./naive-sample-components')
    app.use(installSampleNaiveComponents)
  } else if (group === 'system') {
    const { installSystemNaiveComponents } = await import('./naive-system-components')
    app.use(installSystemNaiveComponents)
  } else if (group === 'talent') {
    const { installTalentNaiveComponents } = await import('./naive-talent-components')
    app.use(installTalentNaiveComponents)
  } else {
    const { installDataNaiveComponents } = await import('./naive-data-components')
    app.use(installDataNaiveComponents)
  }

  installedGroups.add(group)
}
