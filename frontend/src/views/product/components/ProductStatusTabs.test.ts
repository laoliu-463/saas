import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProductStatusTabs from './ProductStatusTabs.vue'

describe('ProductStatusTabs', () => {
  it('shows full backend status counts and caps counts at 99+', () => {
    const wrapper = mount(ProductStatusTabs, {
      props: {
        officialStatus: null,
        statusCounts: {
          PENDING_REVIEW: 10,
          PROMOTING: 726,
          REJECTED: 486,
          TERMINATED: 46,
          EXPIRED: 6
        }
      }
    })

    expect(wrapper.get('[data-testid="official-status-PENDING_REVIEW"]').text()).toContain('10')
    expect(wrapper.get('[data-testid="official-status-PROMOTING"]').text()).toContain('99+')
    expect(wrapper.get('[data-testid="official-status-REJECTED"]').text()).toContain('99+')
    expect(wrapper.get('[data-testid="official-status-TERMINATED"]').text()).toContain('46')
    expect(wrapper.get('[data-testid="official-status-EXPIRED"]').text()).toContain('6')
  })

  it('does not show misleading page-local counts when backend status counts are absent', () => {
    const wrapper = mount(ProductStatusTabs, {
      props: {
        officialStatus: null
      }
    })

    expect(wrapper.find('[data-testid="official-status-count-PROMOTING"]').exists()).toBe(false)
  })
})
