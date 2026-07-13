import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import TalentPage from './index.vue'
import { getTalentPage } from '../../api/talent'

const routeState = reactive({
  fullPath: '/talent?view=MY_TALENTS',
  query: { view: 'MY_TALENTS' } as Record<string, string>
})

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
  useRouter: () => ({
    replace: vi.fn().mockResolvedValue(undefined),
    push: vi.fn()
  })
}))

vi.mock('naive-ui', () => ({
  NAvatar: { template: '<span />' },
  NButton: { template: '<button><slot /></button>' },
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
  TalentCreateModal: { template: '<div />' },
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
    vi.mocked(getTalentPage).mockResolvedValue({
      data: { records: [], total: 0 }
    } as any)
  })

  it('shows the empty message instead of keeping the data table spinner for an empty result', async () => {
    const wrapper = mount(TalentPage, { global: { stubs } })

    await flushPromises()

    expect(wrapper.get('[data-testid="talent-empty"]').text()).toBe('目前暂无达人')
    expect(wrapper.find('[data-testid="talent-table"]').exists()).toBe(false)
  })
})
