import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ActivityList from './ActivityList.vue'
import { getColonelActivityPage } from '../../api/activity'
import { getDouyinInstitutionInfo } from '../../api/douyin'

const authState = vi.hoisted(() => ({
  isAdmin: false,
  roleCodes: ['biz_leader'] as string[]
}))

const messageMock = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn()
}))

vi.mock('../../api/activity', () => ({
  assignColonelActivity: vi.fn(),
  getColonelActivityPage: vi.fn()
}))

vi.mock('../../api/activityProduct', () => ({
  getActivityProducts: vi.fn()
}))

vi.mock('../../api/douyin', () => ({
  getDouyinInstitutionInfo: vi.fn()
}))

vi.mock('../../api/data', () => ({
  exportActivities: vi.fn()
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => authState
}))

vi.mock('../../composables/useRuntimeEnvironment', () => ({
  useRuntimeEnvironment: () => ({
    activityDataSourceHint: 'real-pre',
    activityAlertType: 'info'
  })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn()
  })
}))

vi.mock('naive-ui', () => ({
  createDiscreteApi: () => ({
    loadingBar: {
      start: vi.fn(),
      finish: vi.fn(),
      error: vi.fn()
    },
    message: messageMock
  }),
  useMessage: () => messageMock,
  NButton: { template: '<button><slot /></button>' },
  NModal: { template: '<div><slot /><slot name="footer" /></div>' },
  NForm: { template: '<form><slot /></form>' },
  NFormItem: { template: '<label><slot /></label>' },
  NSelect: { template: '<div><slot /></div>' },
  NSpace: { template: '<div><slot /></div>' }
}))

const stubs = {
  PageHeader: { template: '<header><slot name="actions" /></header>' },
  PageEmpty: { template: '<section data-testid="empty"></section>' },
  NAlert: { template: '<section><slot /></section>' },
  NInput: { template: '<input />' },
  NSelect: { template: '<div><slot /></div>' },
  NDropdown: { template: '<div><slot /></div>' },
  NDataTable: { template: '<table />' },
  NModal: { template: '<div><slot /><slot name="footer" /></div>' },
  NForm: { template: '<form><slot /></form>' },
  NFormItem: { template: '<label><slot /></label>' },
  NButton: { template: '<button><slot /></button>' },
  NSpace: { template: '<div><slot /></div>' }
}

function mountActivityList() {
  return mount(ActivityList, {
    global: { stubs }
  })
}

describe('ActivityList role scoped requests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.isAdmin = false
    authState.roleCodes = ['biz_leader']
    vi.mocked(getColonelActivityPage).mockResolvedValue({
      data: {
        activityList: [],
        total: 0
      }
    } as any)
    vi.mocked(getDouyinInstitutionInfo).mockResolvedValue({
      status: 'success',
      remoteResponse: {}
    } as any)
  })

  it('does not call admin-only institution info for non-admin activity users', async () => {
    mountActivityList()
    await flushPromises()

    expect(getColonelActivityPage).toHaveBeenCalled()
    expect(getDouyinInstitutionInfo).not.toHaveBeenCalled()
  })

  it('keeps loading institution info for admin activity users', async () => {
    authState.isAdmin = true
    authState.roleCodes = ['admin']

    mountActivityList()
    await flushPromises()

    expect(getColonelActivityPage).toHaveBeenCalled()
    expect(getDouyinInstitutionInfo).toHaveBeenCalledTimes(1)
  })
})
