import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const componentSource = readFileSync(
  resolve(process.cwd(), 'src/views/talent/components/TalentDetailModal.vue'),
  'utf8'
)
const talentApiSource = readFileSync(
  resolve(process.cwd(), 'src/api/talent.ts'),
  'utf8'
)

describe('TalentDetailModal sensitive identifiers', () => {
  it('does not render or type secUid in the talent detail surface', () => {
    expect(componentSource).not.toContain('sec_uid')
    expect(componentSource).not.toContain('secUid')
    expect(talentApiSource).not.toContain('secUid?: string | null')
  })
})

describe('TalentDetailModal collaboration editing surface', () => {
  it('wires talent tags and shipping address edit actions to the existing talent APIs', () => {
    expect(componentSource).toContain('updateTalentTags')
    expect(componentSource).toContain('updateTalentShippingAddress')
    expect(componentSource).toContain('data-testid="talent-edit-tags"')
    expect(componentSource).toContain('data-testid="talent-save-tags"')
    expect(componentSource).toContain('data-testid="talent-edit-shipping"')
    expect(componentSource).toContain('data-testid="talent-save-shipping"')
  })

  it('uses the preset talent tag library when it is configured', () => {
    expect(talentApiSource).toContain('getPresetTalentTags')
    expect(talentApiSource).toContain("request.get('/talents/preset-tags')")
    expect(componentSource).toContain('getPresetTalentTags')
    expect(componentSource).toContain('data-testid="talent-preset-tags-select"')
    expect(componentSource).toContain('selectedPresetTags')
  })
})
