import { describe, expect, it } from 'vitest'

import {
  PRODUCT_LIBRARY_CARD_HEIGHT,
  PRODUCT_LIBRARY_GRID_GAP,
  PRODUCT_LIBRARY_ROW_HEIGHT
} from './product-library-layout'

describe('product-library-layout', () => {
  it('uses compact spacing and a shared row height', () => {
    expect(PRODUCT_LIBRARY_GRID_GAP).toBe(8)
    expect(PRODUCT_LIBRARY_CARD_HEIGHT).toBe(492)
    expect(PRODUCT_LIBRARY_ROW_HEIGHT).toBe(500)
  })
})
