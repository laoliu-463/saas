import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const pageSource = readFileSync(
  resolve(process.cwd(), 'src/views/system/CommissionRuleList.vue'),
  'utf8'
)
const routerSource = readFileSync(
  resolve(process.cwd(), 'src/router/index.ts'),
  'utf8'
)
const siderSource = readFileSync(
  resolve(process.cwd(), 'src/views/layout/Sider.vue'),
  'utf8'
)

describe('CommissionRuleList V2 rule center wiring', () => {
  it('exposes the permission-backed commission rule page in system navigation', () => {
    expect(routerSource).toContain("path: 'system/commission-rules'")
    expect(routerSource).toContain("meta: { title: '提成规则', permissions: [PERMISSION.COMMISSION_RULE_ACCESS] }")
    expect(siderSource).toContain("/system/commission-rules")
    expect(pageSource).toContain('data-testid="commission-rules-page"')
  })

  it('supports global activity product and user dimensions for recruiter and channel rules', () => {
    for (const dimension of ['global', 'activity', 'product', 'user']) {
      expect(pageSource).toContain(`value: '${dimension}'`)
    }
    expect(pageSource).toContain("value: 'recruiter'")
    expect(pageSource).toContain("value: 'channel'")
  })

  it('wires list create update and delete actions to commission rule APIs', () => {
    expect(pageSource).toContain('getCommissionRulePage')
    expect(pageSource).toContain('createCommissionRule')
    expect(pageSource).toContain('updateCommissionRule')
    expect(pageSource).toContain('deleteCommissionRule')
  })
})
