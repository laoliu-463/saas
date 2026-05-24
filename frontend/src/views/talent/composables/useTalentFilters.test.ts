import { describe, expect, it } from 'vitest'
import { useTalentFilters } from './useTalentFilters'

describe('useTalentFilters', () => {
  it('maps nickname and douyinNo to keyword for list query', () => {
    const { filters, toRequestParams } = useTalentFilters()
    filters.nickname = '食品达人'
    filters.douyinNo = 'dy001'

    expect(toRequestParams('TEAM_PUBLIC')).toMatchObject({
      view: 'TEAM_PUBLIC',
      nickname: '食品达人',
      douyinNo: 'dy001',
      keyword: '食品达人'
    })
  })

  it('maps fans band to min and max fans', () => {
    const { filters, toRequestParams } = useTalentFilters()
    filters.fansBand = '1W~5W'

    expect(toRequestParams('TEAM_PUBLIC')).toMatchObject({
      minFans: 10000,
      maxFans: 49999
    })
  })
})
