import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = readFileSync(resolve(process.cwd(), 'src/views/sample/CooperationWorkbench.vue'), 'utf8')

describe('CooperationWorkbench action integration', () => {
  it('delegates the normal cooperation table to the server-driven action column', () => {
    expect(source).toContain('CooperationActionColumn')
    expect(source).toContain('row.actionAvailability')
    expect(source).toContain("action: 'APPROVED'")
    expect(source).toContain("action: 'REJECTED'")
    expect(source).toContain('getSamplePromotionCopy')
    expect(source).toContain('getSampleOrderCopy')
  })

  it('wires edit, progress, private note and manual copy', () => {
    expect(source).toContain('<SampleEditModal')
    expect(source).toContain('<PrivateNoteModal')
    expect(source).toContain('<ManualCopyModal')
  })
})
