import { describe, expect, it } from 'vitest'

import { COOPERATION_ACTION_KEYS, resolveCooperationActions } from './cooperation-actions'

describe('cooperation action mapping', () => {
  it('always returns the seven actions in the approved order', () => {
    const actions = resolveCooperationActions({
      EDIT: { enabled: true, disabledReason: null }
    })

    expect(actions.map((item) => item.key)).toEqual(COOPERATION_ACTION_KEYS)
    expect(actions.map((item) => item.label)).toEqual([
      '通过',
      '拒绝',
      '修改订单',
      '查看进度',
      '复制链接',
      '复制订单',
      '备注'
    ])
  })

  it('keeps missing actions clickable while honoring backend capability decisions', () => {
    const actions = resolveCooperationActions({
      APPROVE: { enabled: false, disabledReason: '仅待审核合作单可通过' },
      PROGRESS: { enabled: true, disabledReason: null }
    })

    expect(actions.find((item) => item.key === 'APPROVE')).toMatchObject({
      enabled: false,
      disabledReason: '仅待审核合作单可通过'
    })
    expect(actions.find((item) => item.key === 'PROGRESS')).toMatchObject({ enabled: true })
    expect(actions.find((item) => item.key === 'EDIT')).toMatchObject({
      enabled: true,
      disabledReason: null
    })
  })
})
