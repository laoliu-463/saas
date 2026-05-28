/**
 * 媒体资源安全处理工具模块
 *
 * 提供头像等外部图片 URL 的安全校验能力，防止加载被阻止的域名资源。
 *
 * 安全策略：
 * - 支持 data:（Base64 内联图片）、blob:（本地对象 URL）和相对路径（以 / 开头）
 * - 对外部 URL 进行域名黑名单校验，阻止已知不可用或恶意域名
 * - URL 解析失败时拒绝返回，避免使用无效链接
 */

/** 被阻止的域名集合，这些域名下的头像资源不会被使用 */
const BLOCKED_HOSTS = new Set(['test.local'])

/**
 * 检查给定 URL 是否属于被阻止的域名
 *
 * @param url - 已解析的 URL 对象
 * @returns 如果域名在阻止列表中返回 true
 */
function isBlockedAvatarUrl(url: URL) {
  return BLOCKED_HOSTS.has(url.hostname)
}

/**
 * 解析并校验头像 URL，返回安全可用的 URL 或 undefined。
 *
 * 校验规则：
 * 1. 空值、纯空白、字符串 "null"/"undefined" -> 返回 undefined
 * 2. data: / blob: / 相对路径（以 / 开头）-> 直接返回（无需进一步校验）
 * 3. 完整 URL -> 解析后检查域名是否在阻止列表中，阻止则返回 undefined
 * 4. URL 解析失败（无效格式）-> 返回 undefined
 *
 * @param value - 原始头像 URL 字符串，可能为 null/undefined
 * @returns 安全可用的头像 URL，或 undefined（表示不可用）
 *
 * @example
 * ```ts
 * resolveSafeAvatarUrl('/avatars/user1.png')          // '/avatars/user1.png'
 * resolveSafeAvatarUrl('data:image/png;base64,...')    // 'data:image/png;base64,...'
 * resolveSafeAvatarUrl('https://test.local/avatar.jpg') // undefined（被阻止）
 * resolveSafeAvatarUrl(null)                            // undefined
 * ```
 */
export function resolveSafeAvatarUrl(value?: string | null) {
  const raw = value?.trim()
  if (!raw) {
    return undefined
  }
  // data: 和 blob: 协议及相对路径直接放行，不做域名检查
  if (raw.startsWith('data:') || raw.startsWith('blob:') || raw.startsWith('/')) {
    return raw
  }
  try {
    const parsed = new URL(raw)
    // 校验域名是否在阻止列表中
    return isBlockedAvatarUrl(parsed) ? undefined : raw
  } catch {
    // URL 格式无效，拒绝使用
    return undefined
  }
}
