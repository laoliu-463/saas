import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const ruleCenterSource = readFileSync(
  resolve(process.cwd(), 'src/views/system/rule-center/index.vue'),
  'utf8'
)
const configListSource = readFileSync(
  resolve(process.cwd(), 'src/views/system/ConfigList.vue'),
  'utf8'
)

describe('system rule center and advanced config alignment', () => {
  it('wires rule center change logs to event status lookup', () => {
    expect(ruleCenterSource).toContain('getRuleCenterEventStatus')
    expect(ruleCenterSource).toContain('查看事件状态')
    expect(ruleCenterSource).toContain('eventStatus')
  })

  it('keeps advanced config filters aligned with actual config groups', () => {
    expect(configListSource).toContain("value: 'promotion'")
    expect(configListSource).toContain("value: 'security'")
    expect(configListSource).toContain("value: 'auth'")
  })
})
