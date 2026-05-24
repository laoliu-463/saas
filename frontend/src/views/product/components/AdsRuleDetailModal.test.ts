import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdsRuleDetailModal from './AdsRuleDetailModal.vue'

describe('AdsRuleDetailModal', () => {
  it('shows fallback text when rule is empty', () => {
    const wrapper = mount(AdsRuleDetailModal, {
      props: { show: true, ruleText: '', loading: false },
      global: {
        stubs: {
          NModal: {
            props: ['show'],
            template: '<section v-if="show"><slot /></section>'
          },
          NSpin: { template: '<section><slot /></section>', props: ['show'] }
        }
      }
    })

    expect(wrapper.get('[data-testid="ads-rule-empty"]').text()).toBe('暂无投流规则')
  })

  it('renders rule text as plain text', () => {
    const wrapper = mount(AdsRuleDetailModal, {
      props: { show: true, ruleText: '比例 1:0.5\n保量 10 万', loading: false },
      global: {
        stubs: {
          NModal: { template: '<section><slot /></section>', props: ['show'] },
          NSpin: { template: '<section><slot /></section>', props: ['show'] }
        }
      }
    })

    const content = wrapper.get('[data-testid="ads-rule-content"]')
    expect(content.text()).toContain('比例 1:0.5')
    expect(content.element.tagName).toBe('P')
  })
})
