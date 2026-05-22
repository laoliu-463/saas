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
