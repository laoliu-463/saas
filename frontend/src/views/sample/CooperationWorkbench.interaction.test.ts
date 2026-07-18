import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { actionSample, getSampleOrderCopy, getSamplePage } from '../../api/sample'
import { tryCopyText } from '../../utils/clipboard'
import CooperationWorkbench from './CooperationWorkbench.vue'

const messageApi = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }))
const dialogApi = vi.hoisted(() => ({ warning: vi.fn() }))

vi.mock('../../api/sample', () => ({
  actionSample: vi.fn(),
  batchApproveSamples: vi.fn(),
  batchRejectSamples: vi.fn(),
  batchShipSamples: vi.fn(),
  exportSamples: vi.fn(),
  getSampleFilterOptions: vi.fn().mockResolvedValue({ data: {} }),
  getSampleOrderCopy: vi.fn(),
  getSamplePage: vi.fn(),
  getSamplePromotionCopy: vi.fn()
}))
vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ roleCodes: ['admin'], isAdmin: true })
}))
vi.mock('./sample-user-filter-options', () => ({
  loadSampleChannelOptions: vi.fn().mockResolvedValue([]),
  loadSampleRecruiterOptions: vi.fn().mockResolvedValue([]),
  mapFilterOptionItems: vi.fn().mockReturnValue([])
}))
vi.mock('../../utils/clipboard', () => ({ tryCopyText: vi.fn() }))
vi.mock('../../utils/requestError', () => ({
  notifyApiFailure: vi.fn(),
  notifyClientPermission: vi.fn()
}))
vi.mock('naive-ui', async (importOriginal) => ({
  ...(await importOriginal<typeof import('naive-ui')>()),
  useMessage: () => messageApi,
  useDialog: () => dialogApi
}))

const row = {
  id: 'sample-1',
  talentId: 'talent-1',
  productId: 'product-1',
  quantity: 1,
  status: 'PENDING_AUDIT',
  createTime: '2026-07-18T10:00:00',
  actionAvailability: Object.fromEntries(
    ['APPROVE', 'REJECT', 'EDIT', 'PROGRESS', 'COPY_LINK', 'COPY_ORDER', 'NOTE']
      .map((key) => [key, { enabled: true, disabledReason: null }])
  )
}

const DataTableStub = defineComponent({
  props: ['columns', 'data'],
  setup(props) {
    return () => h('div', { 'data-testid': 'table-stub' }, (props.data || []).map((item: any) => {
      const actionColumn = (props.columns || []).find((column: any) => column.key === 'actions')
      return actionColumn?.render?.(item)
    }))
  }
})

const mountWorkbench = () => mount(CooperationWorkbench, {
  global: {
    stubs: {
      PageHeader: { template: '<div><slot name="actions"/></div>' },
      NDataTable: DataTableStub,
      NInput: { inheritAttrs: false, template: '<input />' },
      NSelect: { inheritAttrs: false, template: '<div />' },
      NDatePicker: { inheritAttrs: false, template: '<div />' },
      NButton: {
        props: ['disabled'],
        emits: ['click'],
        template: '<button :disabled="disabled" v-bind="$attrs" @click="$emit(\'click\')"><slot/></button>'
      },
      NTooltip: { template: '<div><slot name="trigger"/><slot/></div>' },
      NSpace: { template: '<div><slot/></div>' },
      NCard: { template: '<div><slot/></div>' },
      NAlert: { template: '<div><slot/></div>' },
      SampleDetail: true,
      SampleEditModal: true,
      PrivateNoteModal: true,
      ManualCopyModal: { props: ['show', 'content'], template: '<pre v-if="show">{{ content }}</pre>' },
      SampleLogisticsImportModal: true,
      SampleBatchRejectModal: true
    }
  }
})

describe('CooperationWorkbench action interactions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getSamplePage).mockResolvedValue({ data: { records: [row], total: 1 } } as any)
    vi.mocked(actionSample).mockResolvedValue({ data: {} } as any)
    vi.mocked(getSampleOrderCopy).mockResolvedValue({ data: { text: '完整订单文本' } } as any)
  })

  it('confirms approval and sends the backend status action', async () => {
    const wrapper = mountWorkbench()
    await flushPromises()

    await wrapper.get('[data-testid="cooperation-action-APPROVE"]').trigger('click')
    const config = dialogApi.warning.mock.calls.at(-1)?.[0]
    expect(config?.title).toBe('通过合作单')
    await config.onPositiveClick()

    expect(actionSample).toHaveBeenCalledWith('sample-1', { action: 'APPROVED' })
  })

  it('keeps the complete order text when clipboard writing fails', async () => {
    vi.mocked(tryCopyText).mockResolvedValue(false)
    const wrapper = mountWorkbench()
    await flushPromises()

    await wrapper.get('[data-testid="cooperation-action-COPY_ORDER"]').trigger('click')
    await flushPromises()

    expect(getSampleOrderCopy).toHaveBeenCalledWith('sample-1')
    expect(wrapper.text()).toContain('完整订单文本')
  })
})
