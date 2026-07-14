import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { updateProduct } from '../../../api/productManage'
import type { ProductManageRow } from '../../../types/productManage'
import ProductEditModal from './ProductEditModal.vue'

vi.mock('../../../api/productManage', () => ({
  updateProduct: vi.fn()
}))

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => ({
      success: vi.fn(),
      warning: vi.fn()
    })
  }
})

describe('ProductEditModal', () => {
  it('renders as a right-side drawer instead of a centered modal', () => {
    const wrapper = mount(ProductEditModal, {
      props: { show: true, row: null },
      global: {
        stubs: {
          NDrawer: {
            props: ['show', 'width', 'placement'],
            template: '<aside data-testid="drawer" :data-width="width" :data-placement="placement"><slot /></aside>'
          },
          NDrawerContent: { template: '<section><slot name="header" /><slot /><slot name="footer" /></section>' },
          NForm: { template: '<form><slot /></form>' },
          NFormItem: { props: ['label'], template: '<div class="form-item"><label>{{ label }}</label><slot /></div>' },
          NInputNumber: { props: ['value'], template: '<input v-bind="$attrs" :value="value" />' },
          NInput: { props: ['value', 'readonly'], template: '<input v-bind="$attrs" :value="value" :readonly="readonly" />' },
          NDatePicker: { props: ['formattedValue'], emits: ['update:formattedValue'], template: '<input v-bind="$attrs" :value="formattedValue" @input="$emit(\'update:formattedValue\', $event.target.value)" />' },
          NCheckbox: { props: ['checked', 'disabled'], template: '<label><input type="checkbox" :checked="checked" :disabled="disabled" /><slot /></label>' },
          NSpace: { template: '<div><slot /></div>' },
          NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })

    const drawer = wrapper.get('[data-testid="product-edit-drawer"]')
    expect(drawer.attributes('data-placement')).toBe('right')
    expect(drawer.attributes('data-width')).toBe('640')
    expect(wrapper.text()).toContain('编辑商品')
    expect(wrapper.findComponent({ name: 'NModal' }).exists()).toBe(false)
  })

  it('keeps the requested fields and removes hand-card editing', () => {
    const row: ProductManageRow = {
      relationId: '11111111-1111-1111-1111-111111111111',
      priceText: '¥129.00',
      auditSupplement: {
        exclusivePriceAmount: 99.5,
        exclusivePriceRemark: '直播间专属价 129 元',
        supportsAds: true,
        rewardRemark: '达标额外返 2 个点',
        participationRequirements: '近 30 天同类目有成交'
      },
      promotionStartTime: '2026-07-01 00:00:00',
      promotionEndTime: '2026-07-31 23:59:59'
    }
    const wrapper = mount(ProductEditModal, {
      props: { show: true, row },
      global: {
        stubs: {
          NDrawer: { props: ['show', 'width', 'placement'], template: '<aside><slot /></aside>' },
          NDrawerContent: { template: '<section><slot name="header" /><slot /><slot name="footer" /></section>' },
          NForm: { template: '<form><slot /></form>' },
          NFormItem: { props: ['label'], template: '<div class="form-item"><label>{{ label }}</label><slot /></div>' },
          NInputNumber: { props: ['value'], template: '<input v-bind="$attrs" :value="value" />' },
          NInput: { props: ['value', 'readonly'], template: '<input v-bind="$attrs" :value="value" :readonly="readonly" />' },
          NDatePicker: { props: ['formattedValue'], emits: ['update:formattedValue'], template: '<input v-bind="$attrs" :value="formattedValue" @input="$emit(\'update:formattedValue\', $event.target.value)" />' },
          NCheckbox: { props: ['checked', 'disabled'], template: '<label><input type="checkbox" :checked="checked" :disabled="disabled" /><slot /></label>' },
          NSpace: { template: '<div><slot /></div>' },
          NButton: { template: '<button><slot /></button>' }
        }
      }
    })

    expect(wrapper.text()).toEqual(expect.stringContaining('专属价'))
    expect(wrapper.text()).toEqual(expect.stringContaining('专属价说明'))
    expect(wrapper.text()).toEqual(expect.stringContaining('是否支持投流'))
    expect(wrapper.text()).toEqual(expect.stringContaining('奖励说明'))
    expect(wrapper.text()).toEqual(expect.stringContaining('参与要求'))
    expect(wrapper.text()).toEqual(expect.stringContaining('开始时间'))
    expect(wrapper.text()).toEqual(expect.stringContaining('结束时间'))
    expect(wrapper.text()).not.toContain('手卡')
    expect((wrapper.find('[data-testid="product-edit-exclusive-price"]').element as HTMLInputElement).value).toBe('99.5')
    expect((wrapper.get('[data-testid="product-edit-start-time"]').element as HTMLInputElement).value).toBe('2026-07-01 00:00:00')
    expect((wrapper.get('[data-testid="product-edit-end-time"]').element as HTMLInputElement).value).toBe('2026-07-31 23:59:59')
  })

  it('submits only editable fields from the refactored drawer', async () => {
    vi.mocked(updateProduct).mockResolvedValueOnce({ data: { id: 'updated' } } as never)
    const row: ProductManageRow = {
      relationId: '11111111-1111-1111-1111-111111111111',
      auditSupplement: {
        exclusivePriceAmount: 129,
        exclusivePriceRemark: '原专属价说明',
        supportsAds: true,
        rewardRemark: '原奖励说明',
        participationRequirements: '原参与要求'
      },
      promotionStartTime: '2026-07-01',
      promotionEndTime: '2026-07-31'
    }
    const wrapper = mount(ProductEditModal, {
      props: { show: true, row },
      global: {
        stubs: {
          NDrawer: { template: '<aside><slot /></aside>' },
          NDrawerContent: { template: '<section><slot name="header" /><slot /><slot name="footer" /></section>' },
          NForm: { template: '<form><slot /></form>' },
          NFormItem: { props: ['label'], template: '<div><slot /></div>' },
          NInputNumber: { props: ['value'], template: '<input v-bind="$attrs" :value="value" />' },
          NInput: { props: ['value'], template: '<input v-bind="$attrs" :value="value" />' },
          NDatePicker: { props: ['formattedValue'], emits: ['update:formattedValue'], template: '<input v-bind="$attrs" :value="formattedValue" @input="$emit(\'update:formattedValue\', $event.target.value)" />' },
          NCheckbox: { props: ['checked', 'disabled'], template: '<input type="checkbox" />' },
          NSpace: { template: '<div><slot /></div>' },
          NButton: { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })

    const state = wrapper.vm as typeof wrapper.vm & {
      form: {
        exclusivePriceAmount: number | null
        exclusivePriceRemark: string
        supportsAds: boolean
        rewardRemark: string
        participationRequirements: string
        startTime: string | null
        endTime: string | null
      }
    }
    state.form.exclusivePriceAmount = 99.9
    state.form.exclusivePriceRemark = '新专属价说明'
    state.form.supportsAds = false
    state.form.rewardRemark = '新奖励说明'
    state.form.participationRequirements = '新参与要求'
    await wrapper.get('[data-testid="product-edit-start-time"]').setValue('2026-07-02 08:00:00')
    await wrapper.get('[data-testid="product-edit-end-time"]').setValue('2026-08-01 20:00:00')

    await wrapper.get('button:last-of-type').trigger('click')

    expect(updateProduct).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
      exclusivePriceAmount: 99.9,
      exclusivePriceRemark: '新专属价说明',
      supportsAds: false,
      rewardRemark: '新奖励说明',
      participationRequirements: '新参与要求',
      promotionStartTime: '2026-07-02 08:00:00',
      promotionEndTime: '2026-08-01 20:00:00'
    })
  })
})
