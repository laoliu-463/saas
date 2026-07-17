import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import TalentPage from './index.vue'
import { getTalentPage } from '../../api/talent'

const routeState = reactive({
  fullPath: '/talent?view=MY_TALENTS',
  query: { view: 'MY_TALENTS' } as Record<string, string>
})

const routerMocks = vi.hoisted(() => ({
  replace: vi.fn(),
  push: vi.fn()
}))

const TalentCreateModalStub = {
  name: 'TalentCreateModalStub',
  template: '<div data-testid="talent-create-modal" />'
}

function normalizeRouterQuery(query: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}

vi.mock('../../api/talent', () => ({
  blacklistTalent: vi.fn(),
  claimTalent: vi.fn(),
  getTalentPage: vi.fn(),
  refreshWeeklyTalents: vi.fn(),
  releaseTalent: vi.fn(),
  unblacklistTalent: vi.fn()
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({
    roleCodes: ['channel_staff'],
    isAdmin: false,
    userInfo: { id: 'user-1' }
  })
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => routerMocks
}))

vi.mock('naive-ui', () => ({
  NAvatar: { template: '<span />' },
  NButton: { inheritAttrs: false, template: '<button v-bind="$attrs"><slot /></button>' },
  NSpace: { template: '<div><slot /></div>' },
  NTag: { template: '<span><slot /></span>' },
  useDialog: () => ({ warning: vi.fn(), error: vi.fn() }),
  useMessage: () => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() })
}))

const stubs = {
  PageHeader: { template: '<header><slot name="actions" /></header>' },
  PageEmpty: {
    props: ['title'],
    template: '<section data-testid="talent-empty">{{ title }}</section>'
  },
  TalentMetricFilters: { template: '<section />' },
  TalentDetailModal: { template: '<div />' },
  TalentCreateModal: TalentCreateModalStub,
  TalentBatchImportModal: { template: '<div />' },
  TalentStatusActions: { template: '<div />' },
  NCard: { template: '<section><slot /></section>' },
  NDataTable: {
    props: ['loading'],
    template: '<div data-testid="talent-table" :data-loading="String(loading)" />'
  }
}

describe('TalentPage empty state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.fullPath = '/talent?view=MY_TALENTS'
    routeState.query = { view: 'MY_TALENTS' }
    routerMocks.replace.mockResolvedValue(undefined)
    vi.mocked(getTalentPage).mockResolvedValue({
      data: { records: [], total: 0 }
    } as any)
  })

  it('shows the empty message instead of keeping the data table spinner for an empty result', async () => {
    const wrapper = mount(TalentPage, { global: { stubs } })

    await flushPromises()

    expect(wrapper.get('[data-testid="talent-empty"]').text()).toBe('目前暂无达人')
    expect(wrapper.find('[data-testid="talent-table"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('does not refetch after the page normalizes its own route query', async () => {
    routerMocks.replace.mockImplementation(async ({ query }: { query: Record<string, string> }) => {
      const normalizedQuery = normalizeRouterQuery(query)
      routeState.query = normalizedQuery as Record<string, string>
      routeState.fullPath = `/talent?${new URLSearchParams(normalizedQuery as Record<string, string>).toString()}`
    })

    const wrapper = mount(TalentPage, { global: { stubs } })

    await flushPromises()

    expect(getTalentPage).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('deduplicates refreshes while the same list request is pending', async () => {
    let resolveRequest!: (value: any) => void
    const pendingRequest = new Promise((resolve) => {
      resolveRequest = resolve
    })
    vi.mocked(getTalentPage).mockReturnValue(pendingRequest as any)

    const wrapper = mount(TalentPage, { global: { stubs } })

    await flushPromises()
    expect(getTalentPage).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="talent-refresh"]').trigger('click')
    resolveRequest({ data: { records: [], total: 0 } })
    await flushPromises()
    expect(getTalentPage).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('does not refetch when route normalization updates after router.replace resolves', async () => {
    let replaceCalls = 0
    routerMocks.replace.mockImplementation(({ query }: { query: Record<string, string> }) => {
      replaceCalls += 1
      if (replaceCalls === 1) {
        setTimeout(() => {
          const normalizedQuery = normalizeRouterQuery(query)
          routeState.query = normalizedQuery as Record<string, string>
          routeState.fullPath = `/talent?${new URLSearchParams(normalizedQuery as Record<string, string>).toString()}`
        }, 0)
      }
      return Promise.resolve()
    })

    const wrapper = mount(TalentPage, { global: { stubs } })

    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()

    expect(getTalentPage).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('switches to my talents after creating an owner-claimed talent', async () => {
    const wrapper = mount(TalentPage, { global: { stubs } })

    await flushPromises()
    vi.mocked(getTalentPage).mockClear()

    wrapper.findComponent(TalentCreateModalStub).vm.$emit('success')
    await flushPromises()

    expect(getTalentPage).toHaveBeenCalledWith(expect.objectContaining({ view: 'MY_TALENTS' }))
    wrapper.unmount()
  })

  it('shows the create action for channel staff', async () => {
    const wrapper = mount(TalentPage, { global: { stubs } })

    await flushPromises()

    expect(wrapper.find('[data-testid="talent-create"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
