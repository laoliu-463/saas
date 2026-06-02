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

  it('hasActiveFilters detects active category filter', () => {
    const { filters, hasActiveFilters } = useTalentFilters()
    filters.category = '美妆'
    expect(hasActiveFilters.value).toBe(true)
  })

  it('hasActiveFilters detects active region filter', () => {
    const { filters, hasActiveFilters } = useTalentFilters()
    filters.region = '北京'
    expect(hasActiveFilters.value).toBe(true)
  })

  it('hasActiveFilters detects active keyword filter', () => {
    const { filters, hasActiveFilters } = useTalentFilters()
    filters.keyword = '达人'
    expect(hasActiveFilters.value).toBe(true)
  })

  it('hasActiveFilters detects active fans band filter', () => {
    const { filters, hasActiveFilters } = useTalentFilters()
    filters.fansBand = '10W~50W'
    expect(hasActiveFilters.value).toBe(true)
  })

  it('hasActiveFilters is false when all filters are default', () => {
    const { hasActiveFilters } = useTalentFilters()
    expect(hasActiveFilters.value).toBe(false)
  })

  it('resetFilters restores default values', () => {
    const { filters, resetFilters, hasActiveFilters } = useTalentFilters()
    filters.category = '美妆'
    filters.keyword = '达人'
    expect(hasActiveFilters.value).toBe(true)

    resetFilters()
    expect(filters.category).toBeNull()
    expect(filters.keyword).toBe('')
    expect(hasActiveFilters.value).toBe(false)
  })

  it('applyQuery parses query object correctly', () => {
    const { filters, applyQuery } = useTalentFilters()
    applyQuery({
      category: '食品饮料',
      fansBand: '5W~10W',
      level: 'LV3',
      region: '上海',
      douyinNo: 'DY123',
      nickname: 'test',
      keyword: 'keyword'
    })

    expect(filters.category).toBe('食品饮料')
    expect(filters.fansBand).toBe('5W~10W')
    expect(filters.level).toBe('LV3')
    expect(filters.region).toBe('上海')
    expect(filters.douyinNo).toBe('DY123')
    expect(filters.nickname).toBe('test')
    expect(filters.keyword).toBe('keyword')
  })

  it('applyQuery maps keyword to nickname when douyinNo and nickname are empty', () => {
    const { filters, applyQuery } = useTalentFilters()
    applyQuery({ keyword: 'fallback_nickname' })

    expect(filters.nickname).toBe('fallback_nickname')
  })

  it('applyQuery ignores non-string values', () => {
    const { filters, applyQuery } = useTalentFilters()
    applyQuery({
      category: 123,
      fansBand: null,
      region: undefined,
      keyword: { nested: 'object' }
    } as any)

    expect(filters.category).toBeNull()
    expect(filters.region).toBe('')
    expect(filters.keyword).toBe('')
  })

  it('toRequestParams maps all band filters', () => {
    const { filters, toRequestParams } = useTalentFilters()
    filters.liveSalesBand = '1W以下'
    filters.liveViewBand = '5千以下'
    filters.videoSalesBand = '1W~2.5W'
    filters.videoPlayBand = '5千~1W'
    filters.level = 'LV5'
    filters.contactStatus = 'HAS_CONTACT'

    const params = toRequestParams('MY_TALENTS')
    expect(params.liveSalesBand).toBe('1W以下')
    expect(params.videoSalesBand).toBe('1W~2.5W')
    expect(params.level).toBe('LV5')
    expect(params.contactStatus).toBe('HAS_CONTACT')
  })

  it('toRequestParams uses keyword when both nickname and douyinNo are empty', () => {
    const { filters, toRequestParams } = useTalentFilters()
    filters.keyword = 'only_keyword'

    const params = toRequestParams('TEAM_PUBLIC')
    expect(params.keyword).toBe('only_keyword')
  })

  it('toRequestParams resolves fansBand range for various bands', () => {
    const { filters, toRequestParams } = useTalentFilters()

    filters.fansBand = '1W以下'
    expect(toRequestParams('TEAM_PUBLIC').maxFans).toBe(9999)

    filters.fansBand = '5W~10W'
    expect(toRequestParams('TEAM_PUBLIC').minFans).toBe(50000)
    expect(toRequestParams('TEAM_PUBLIC').maxFans).toBe(99999)

    filters.fansBand = '100W以上'
    expect(toRequestParams('TEAM_PUBLIC').minFans).toBe(1000000)
  })

  it('toRequestParams maps claimStatus', () => {
    const { filters, toRequestParams } = useTalentFilters()
    filters.claimStatus = 'CLAIMED'

    const params = toRequestParams('TEAM_PRIVATE')
    expect(params.claimStatus).toBe('CLAIMED')
  })

  it('toRouteQuery returns all filters with undefined for empty values', () => {
    const { filters, toRouteQuery } = useTalentFilters()
    filters.category = '美妆'
    filters.fansBand = '10W~50W'
    filters.region = '广州'

    const query = toRouteQuery('MY_TALENTS')
    expect(query.view).toBe('MY_TALENTS')
    expect(query.category).toBe('美妆')
    expect(query.fansBand).toBe('10W~50W')
    expect(query.region).toBe('广州')
    expect(query.keyword).toBeUndefined()
  })

  it('toRouteQuery returns undefined for all empty filters', () => {
    const { toRouteQuery } = useTalentFilters()
    const query = toRouteQuery('TEAM_PUBLIC')

    expect(query.category).toBeUndefined()
    expect(query.fansBand).toBeUndefined()
    expect(query.region).toBeUndefined()
  })

  it('toRequestParams handles 50W~100W fans band', () => {
    const { filters, toRequestParams } = useTalentFilters()
    filters.fansBand = '50W~100W'

    const params = toRequestParams('TEAM_PUBLIC')
    expect(params.minFans).toBe(500000)
    expect(params.maxFans).toBe(999999)
  })

  it('toRequestParams handles 25W~50W liveSalesBand', () => {
    const { filters, toRequestParams } = useTalentFilters()
    filters.liveSalesBand = '25W~50W'

    const params = toRequestParams('TEAM_PUBLIC')
    expect(params.liveSalesBand).toBe('25W~50W')
  })
})
