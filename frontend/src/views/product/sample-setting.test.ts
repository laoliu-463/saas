import { describe, expect, it } from 'vitest'
import { DEFAULT_SAMPLE_SETTING, normalizeSampleSetting, toSampleSettingPayload } from './sample-setting'

describe('sample-setting', () => {
  it('uses the requested default sample settings', () => {
    expect(normalizeSampleSetting()).toEqual(DEFAULT_SAMPLE_SETTING)
  })

  it('reads legacy product audit fields into the new form', () => {
    expect(normalizeSampleSetting({
      sampleType: 'PAID',
      sampleThresholdSales: 30000,
      sampleThresholdLevel: 3,
      sampleBoxes: 2,
      quantity: 5
    })).toMatchObject({
      supportFreeSample: false,
      minSales30d: 30000,
      minTalentLevel: 3,
      sampleBoxCount: 2,
      sampleQuantity: 5
    })
  })

  it('keeps existing compatibility fields when submitting the new form', () => {
    const payload = toSampleSettingPayload({
      supportFreeSample: true,
      hasSampleThreshold: true,
      minWindowSales30d: null,
      minSales30d: 50000,
      minFans: null,
      minTalentLevel: 1,
      sampleBoxCount: 4,
      sampleQuantity: 1
    })

    expect(payload).toMatchObject({
      sampleType: 'FREE',
      allowSample: true,
      sampleThresholdSales: 50000,
      sampleThresholdLevel: 1,
      sampleBoxCount: 4,
      sampleQuantity: 1
    })
  })
})
