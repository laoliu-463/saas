export function extractPickSourceFromUrl(url: string | null | undefined): string | null {
  if (!url) return null
  try {
    return new URL(url).searchParams.get('pick_source')
  } catch {
    return null
  }
}
