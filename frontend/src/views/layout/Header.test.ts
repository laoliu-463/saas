import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../stores/auth'
import Header from './Header.vue'

const routerPush = vi.fn()
const routerReplace = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: routerPush,
    replace: routerReplace
  }),
  useRoute: () => ({
    path: '/dashboard'
  })
}))

const messageApi = {
  warning: vi.fn()
}

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => messageApi
  }
})

const globalStubs = {
  NAvatar: { template: '<span><slot /></span>' },
  NDropdown: { template: '<div><slot /></div>' },
  NIcon: { template: '<span><slot /></span>' }
}

const mountHeader = async () => {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ data: {} })
  })
  vi.stubGlobal('fetch', fetchMock)

  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore()
  authStore.setUserInfo({ username: 'alice', roleCodes: ['admin'] })
  authStore.updateTokens({ token: '', refreshToken: 'refresh-token' })

  const wrapper = mount(Header, {
    global: {
      plugins: [pinia],
      stubs: globalStubs
    }
  })
  await flushPromises()
  fetchMock.mockClear()
  return { wrapper, fetchMock }
}

describe('layout Header logout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('revokes refresh token even when access token is missing', async () => {
    const { wrapper, fetchMock } = await mountHeader()

    await (wrapper.vm as any).handleUserMenu('logout')

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ refreshToken: 'refresh-token' })
    }))
    expect(routerReplace).toHaveBeenCalledWith('/login')
  })
})
