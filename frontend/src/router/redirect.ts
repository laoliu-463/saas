/**
 * 路由重定向安全工具
 *
 * 职责：
 * - 构建登录页重定向目标（将受保护路径编码到 redirect 查询参数）
 * - 解析登录后安全的重定向目标（防止开放重定向漏洞）
 * - 规范化内部路径（确保以 / 开头，不允许协议相对 URL 和外部域名）
 *
 * 安全约束：
 * - 只允许以单个 / 开头的内部路径
 * - 拒绝 // 开头的协议相对 URL
 * - 拒绝包含外部域名的绝对 URL
 * - 拒绝重定向到 /login 页面自身（防止循环）
 */

/** 登录后默认重定向路径 */
const DEFAULT_POST_LOGIN_REDIRECT = '/data'

/**
 * 构建登录页重定向目标
 * 将当前受保护路径编码为 redirect 查询参数，拼接到 /login 路径后
 *
 * @param target - 当前尝试访问的受保护路径
 * @returns 登录页完整路径（含 redirect 参数），目标无效时返回 '/login'
 *
 * @example
 * buildLoginRedirectTarget('/system/douyin?oauth=success')
 * // => '/login?redirect=%2Fsystem%2Fdouyin%3Foauth%3Dsuccess'
 */
export function buildLoginRedirectTarget(target: string | null | undefined): string {
  const safeTarget = normalizeInternalRedirectTarget(target)
  if (!safeTarget) {
    return '/login'
  }
  return `/login?redirect=${encodeURIComponent(safeTarget)}`
}

/**
 * 解析登录后安全的重定向目标
 * 从 redirect 参数中提取目标路径，经过安全校验后返回
 *
 * @param redirect - 原始 redirect 参数值（可能为字符串数组或 null）
 * @param fallback - 默认回退路径，默认为 '/data'
 * @returns 安全的重定向路径
 *
 * @example
 * resolveSafePostLoginRedirect('/system/douyin?oauth=success', '/data')
 * // => '/system/douyin?oauth=success'
 * resolveSafePostLoginRedirect('https://evil.example/phish', '/data')
 * // => '/data'  （外部 URL 被拦截，回退到默认路径）
 */
export function resolveSafePostLoginRedirect(
  redirect: string | null | Array<string | null> | undefined,
  fallback = DEFAULT_POST_LOGIN_REDIRECT
): string {
  // 处理数组形式的 redirect 参数（某些框架可能传入数组）
  const rawRedirect = Array.isArray(redirect) ? redirect[0] : redirect
  const safeRedirect = normalizeInternalRedirectTarget(rawRedirect)
  if (safeRedirect) {
    return safeRedirect
  }
  // redirect 无效时回退到 fallback，fallback 也无效则使用硬编码默认值
  return normalizeInternalRedirectTarget(fallback) || DEFAULT_POST_LOGIN_REDIRECT
}

/**
 * 规范化内部重定向目标
 * 安全校验规则：
 * 1. 必须以 / 开头（相对路径）
 * 2. 不允许 // 开头（协议相对 URL，如 //evil.example）
 * 3. 通过 URL 解析后 origin 必须为内部域名
 * 4. 不允许重定向到 /login 页面本身（防止登录循环）
 *
 * @param target - 原始目标路径
 * @returns 安全的规范化路径，不安全时返回空字符串
 */
function normalizeInternalRedirectTarget(target: string | null | undefined): string {
  const value = String(target || '').trim()
  // 基本校验：非空、以 / 开头、非协议相对 URL
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return ''
  }
  try {
    // 使用内部假域名解析，确保不会泄露到外部
    const url = new URL(value, 'http://saas.local')
    if (url.origin !== 'http://saas.local') {
      return ''
    }
    const normalized = `${url.pathname}${url.search}${url.hash}`
    // 防止重定向到登录页造成循环
    if (normalizeRoutePath(normalized) === '/login') {
      return ''
    }
    return normalized
  } catch {
    return ''
  }
}

/**
 * 规范化路由路径
 * 去除查询参数和 hash，确保以 / 开头，去除末尾斜杠
 *
 * @param path - 原始路径
 * @returns 规范化后的路径
 */
function normalizeRoutePath(path: string): string {
  const clean = String(path || '/').split(/[?#]/)[0]?.trim() || '/'
  const withSlash = clean.startsWith('/') ? clean : `/${clean}`
  if (withSlash === '/') return withSlash
  return withSlash.replace(/\/+$/, '')
}
