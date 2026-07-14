import { describe, expect, it } from 'vitest'
import { createApp } from 'vue'
import { NDatePicker } from 'naive-ui'
import { installProductNaiveComponents } from './naive-product-components'

describe('product Naive UI components', () => {
  it('registers the date picker used by the product edit drawer', () => {
    const app = createApp({ template: '<div />' })

    app.use(installProductNaiveComponents)

    expect(app.component('NDatePicker')).toBe(NDatePicker)
  })
})
