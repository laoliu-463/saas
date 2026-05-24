import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PromotionTemplateEditor from './PromotionTemplateEditor.vue'

describe('PromotionTemplateEditor', () => {
  it('renders preview with placeholder substitution', () => {
    const wrapper = mount(PromotionTemplateEditor, {
      props: {
        modelValue: '商品：{商品名称} 佣金 {佣金率}',
        editable: true
      },
      global: {
        stubs: {
          NCard: { template: '<div><slot /></div>', props: ['title'] },
          NAlert: true,
          NInput: true,
          NDivider: true
        }
      }
    })

    expect(wrapper.find('[data-testid="promotion-template-preview"]').text()).toContain('示例商品')
    expect(wrapper.find('[data-testid="promotion-template-preview"]').text()).toContain('25%')
  })
})
