import { describe, expect, it } from 'vitest'

import { isProductRelationId, resolveProductRelationId } from './product-relation-id'

describe('product-relation-id', () => {
  it('accepts UUID relation IDs', () => {
    expect(isProductRelationId('550e8400-e29b-41d4-a716-446655440000')).toBe(true)
    expect(resolveProductRelationId({ relationId: '550e8400-e29b-41d4-a716-446655440000' }))
      .toBe('550e8400-e29b-41d4-a716-446655440000')
  })

  it('falls back to row id when relationId is absent', () => {
    expect(resolveProductRelationId({ id: '0d9fb14d-a1f0-4ce4-b0af-4df853388c5f' }))
      .toBe('0d9fb14d-a1f0-4ce4-b0af-4df853388c5f')
  })

  it('does not treat platform productId as relationId', () => {
    expect(resolveProductRelationId({ productId: '9001' } as any)).toBe('')
    expect(resolveProductRelationId({ relationId: '9001', productId: '9001' } as any)).toBe('')
  })
})
