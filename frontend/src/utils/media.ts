const BLOCKED_HOSTS = new Set(['test.local'])

function isBlockedAvatarUrl(url: URL) {
  return BLOCKED_HOSTS.has(url.hostname)
}

export function resolveSafeAvatarUrl(value?: string | null) {
  const raw = value?.trim()
  if (!raw) {
    return undefined
  }
  if (raw.startsWith('data:') || raw.startsWith('blob:') || raw.startsWith('/')) {
    return raw
  }
  try {
    const parsed = new URL(raw)
    return isBlockedAvatarUrl(parsed) ? undefined : raw
  } catch {
    return undefined
  }
}
