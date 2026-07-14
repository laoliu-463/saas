import { describe, expect, it } from 'vitest'
import { DEFAULT_SAMPLE_SETTING, normalizeSampleSetting, toSampleSettingPayload } from './sample-setting'

describe('sample-setting', () => {
  it('uses the requested default sample settings', () => {
    expect(normalizeSampleSetting()).toMatchObject({
      supportFreeSample: true,
      hasSampleThreshold: true,
      minSales30d: 50000,
      minTalentLevel: 1
    })
    expect(normalizeSampleSetting()).not.toHaveProperty('sampleBoxCount')
    expect(normalizeSampleSetting()).not.toHaveProperty('sampleQuantity')
    expect(DEFAULT_SAMPLE_SETTING).not.toHaveProperty('sampleBoxCount')
    expect(DEFAULT_SAMPLE_SETTING).not.toHaveProperty('sampleQuantity')
  })

  it('reads legacy product audit fields into the new form', () => {
    expect(normalizeSampleSetting({
      sampleType: 'PAID',
      sampleThresholdSales: 30000,
      sampleThresholdLevel: 3
    })).toMatchObject({
      supportFreeSample: false,
      minSales30d: 30000,
      minTalentLevel: 3
    })
  })

  it('keeps existing compatibility fields when submitting the new form', () => {
    const payload = toSampleSettingPayload({
      supportFreeSample: true,
      hasSampleThreshold: true,
      minWindowSales30d: null,
      minSales30d: 50000,
      minFans: null,
      minTalentLevel: 1
    })

    expect(payload).toMatchObject({
      sampleType: 'FREE',
      allowSample: true,
      sampleThresholdSales: 50000,
      sampleThresholdLevel: 1
    })
  })

  it('normalizes LV values from the API into the numeric form state', () => {
    expect(normalizeSampleSetting({ sampleThresholdLevel: 'LV3' }).minTalentLevel).toBe(3)
    expect(normalizeSampleSetting({ minTalentLevel: '7' }).minTalentLevel).toBe(7)
  })

  it('clears threshold values when the threshold switch is off', () => {
    expect(toSampleSettingPayload({
      supportFreeSample: false,
      hasSampleThreshold: false,
      minWindowSales30d: 100,
      minSales30d: 50000,
      minFans: 1000,
      minTalentLevel: 1
    })).toMatchObject({
      minWindowSales30d: null,
      minSales30d: null,
      minFans: null,
      minTalentLevel: null,
      sampleThresholdSales: null,
      sampleThresholdLevel: null
    })
  })
})
