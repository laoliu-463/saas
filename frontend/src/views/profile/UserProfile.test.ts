import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const componentSource = () => readFileSync(
  resolve(process.cwd(), 'src/views/profile/UserProfile.vue'),
  'utf8'
)

const headerSource = () => readFileSync(
  resolve(process.cwd(), 'src/views/layout/Header.vue'),
  'utf8'
)

describe('user profile page wiring', () => {
  it('uses the current-user domain APIs for profile, data scope, permission check and password change', () => {
    const source = componentSource()

    expect(source).toContain('getCurrentUser')
    expect(source).toContain('getCurrentUserDataScope')
    expect(source).toContain('checkCurrentUserPermission')
    expect(source).toContain('changeCurrentUserPassword')
    expect(source).toContain('data-testid="profile-current-user"')
    expect(source).toContain('data-testid="profile-change-password"')
    expect(source).toContain('data-testid="profile-permission-check"')
  })

  it('exposes personal center entry from the user menu', () => {
    const source = headerSource()

    expect(source).toContain("label: '个人中心'")
    expect(source).toContain("key: 'profile'")
    expect(source).toContain("await safeNavigate('/profile')")
  })

  it('keeps an explicit return path from personal center to the role-resolved workspace', () => {
    const source = componentSource()

    expect(source).toContain('data-testid="profile-return-to-workspace"')
    expect(source).toContain('handleReturnToWorkspace')
    expect(source).toContain("await router.push('/')")
  })
})
