import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

const nginxTemplate = readFileSync(join(process.cwd(), 'nginx/default.conf.template'), 'utf8')

describe('real-pre nginx CSP', () => {
  it('allows remote product image hosts used by Douyin product covers', () => {
    expect(nginxTemplate).toContain('Content-Security-Policy')
    expect(nginxTemplate).toContain('img-src')
    expect(nginxTemplate).toContain('https://*.ecombdimg.com')
    expect(nginxTemplate).toContain('https:')
  })

  it('allows fetching remote product images for rich clipboard copy', () => {
    expect(nginxTemplate).toContain("connect-src 'self' https://*.ecombdimg.com")
  })
})
