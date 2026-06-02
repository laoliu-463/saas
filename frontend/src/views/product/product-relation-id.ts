const UUID_PATTERN = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/

export function isProductRelationId(value: unknown): boolean {
  return UUID_PATTERN.test(String(value ?? '').trim())
}

export function resolveProductRelationId(row: { relationId?: unknown; id?: unknown } | null | undefined): string {
  const relationId = String(row?.relationId ?? '').trim()
  if (isProductRelationId(relationId)) return relationId

  const id = String(row?.id ?? '').trim()
  if (isProductRelationId(id)) return id

  return ''
}
