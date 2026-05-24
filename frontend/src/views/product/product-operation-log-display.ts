export interface ProductOperationLogRow {
  id?: string
  createTime?: string | null
  operationType?: string | null
  beforeStatus?: string | null
  afterStatus?: string | null
  success?: boolean | null
  errorMessage?: string | null
  operationRemark?: string | null
  operationPayload?: string | null
  operatorId?: string | null
}

export interface ProductOperationLogContext {
  assigneeName?: string | null
}

export interface ProductOperationLogView {
  id: string
  timeLabel: string
  eventLabel: string
  eventTagType: 'default' | 'info' | 'success' | 'warning' | 'error'
  summary: string
  detailLines: string[]
  statusFlow: string | null
  operatorLabel: string
  success: boolean
  failureReason: string | null
}

const BIZ_STATUS_LABELS: Record<string, string> = {
  PENDING_AUDIT: '待审核',
  APPROVED: '审核通过',
  REJECTED: '审核拒绝',
  BOUND: '历史已绑定',
  ASSIGNED: '已分配招商',
  LINKED: '已转链',
  FOLLOWING: '已转交达人 CRM'
}

const OPERATION_TYPE_LABELS: Record<string, string> = {
  LIBRARY_ENTRY: '加入商品库',
  SYNC: '同步商品',
  ASSIGN_AUDIT: '分配审核人',
  ASSIGN: '分配招商',
  AUDIT: '商品审核',
  DECISION: '推进判断',
  BIND_ACTIVITY: '绑定活动',
  LINK: '生成推广链接',
  PROMOTION_LINK: '生成推广链接',
  TALENT_FOLLOW: '达人跟进',
  TALENT_FOLLOW_APPEND: '追加达人跟进'
}

const DECISION_LEVEL_LABELS: Record<string, string> = {
  MAIN: '主推',
  SECONDARY: '次推',
  PAUSE: '暂缓',
  DROP: '放弃'
}

export function normalizeLogText(value?: string | null): string {
  if (!value) return ''
  const text = String(value).trim()
  return text && text !== 'null' && text !== 'undefined' ? text : ''
}

export function isUuidLike(value?: string | null): boolean {
  const text = normalizeLogText(value)
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(text)
}

export function getBizStatusLabel(status?: string | null): string {
  const key = normalizeLogText(status)
  return BIZ_STATUS_LABELS[key] || key || '未知状态'
}

export function getOperationTypeLabel(type?: string | null): string {
  const key = normalizeLogText(type)
  return OPERATION_TYPE_LABELS[key] || key || '其他操作'
}

export function getDecisionLevelLabel(level?: string | null): string {
  const key = normalizeLogText(level)
  return DECISION_LEVEL_LABELS[key] || key || '暂无判断'
}

export function parseOperationPayload(raw?: string | null): Record<string, string> {
  const text = normalizeLogText(raw)
  if (!text) return {}

  if (text.startsWith('{') && text.endsWith('}')) {
    try {
      const parsed = JSON.parse(text) as unknown
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return Object.fromEntries(
          Object.entries(parsed as Record<string, unknown>).map(([key, value]) => [key, value == null ? '' : String(value)])
        )
      }
    } catch {
      // fall through to Java Map#toString parsing
    }
    return parseJavaMapString(text.slice(1, -1))
  }

  return parseJavaMapString(text)
}

function parseJavaMapString(inner: string): Record<string, string> {
  const payload: Record<string, string> = {}
  if (!inner.trim()) return payload

  const pairs: string[] = []
  let current = ''
  for (let i = 0; i < inner.length; i += 1) {
    const ch = inner[i]
    if (ch === ',') {
      const rest = inner.slice(i + 1)
      if (/^\s*[^=\s]+=/.test(rest)) {
        pairs.push(current.trim())
        current = ''
        continue
      }
    }
    current += ch
  }
  if (current.trim()) pairs.push(current.trim())

  pairs.forEach((pair) => {
    const index = pair.indexOf('=')
    if (index <= 0) return
    const key = pair.slice(0, index).trim()
    const value = pair.slice(index + 1).trim()
    if (key) payload[key] = value
  })
  return payload
}

export function formatOperationTime(value?: string | null): string {
  const text = normalizeLogText(value)
  if (!text) return '—'
  const date = new Date(text)
  if (Number.isNaN(date.getTime())) return text
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatOperatorLabel(row: ProductOperationLogRow): string {
  const payload = parseOperationPayload(row.operationPayload)
  if (payload.operatorName) return payload.operatorName
  if (row.operationType === 'SYNC') return '系统同步'
  if (isUuidLike(row.operatorId)) return '历史操作人'
  return normalizeLogText(row.operatorId) || '—'
}

export function formatStatusFlow(
  beforeStatus?: string | null,
  afterStatus?: string | null
): string | null {
  const before = normalizeLogText(beforeStatus)
  const after = normalizeLogText(afterStatus)
  if (!before && !after) return null
  if (before === after) return null
  return `${getBizStatusLabel(before)} → ${getBizStatusLabel(after)}`
}

function resolveEventTagType(row: ProductOperationLogRow): ProductOperationLogView['eventTagType'] {
  const type = normalizeLogText(row.operationType)
  if (row.success === false) return 'error'
  if (type === 'LIBRARY_ENTRY' || type === 'PROMOTION_LINK' || type === 'LINK') return 'success'
  if (type === 'AUDIT') return row.afterStatus === 'REJECTED' ? 'error' : 'success'
  if (type === 'DECISION') {
    const level = parseOperationPayload(row.operationPayload).decisionLevel
    if (level === 'DROP') return 'error'
    if (level === 'PAUSE') return 'warning'
    return 'info'
  }
  if (type === 'ASSIGN' || type === 'ASSIGN_AUDIT' || type === 'TALENT_FOLLOW' || type === 'TALENT_FOLLOW_APPEND') {
    return 'info'
  }
  return 'default'
}

function buildDetailLines(row: ProductOperationLogRow, payload: Record<string, string>, context?: ProductOperationLogContext): string[] {
  const lines: string[] = []
  const type = normalizeLogText(row.operationType)
  const assigneeName = payload.assigneeName || context?.assigneeName || ''

  if (type === 'ASSIGN' || type === 'ASSIGN_AUDIT') {
    if (assigneeName) lines.push(`负责人：${assigneeName}`)
  }
  if (type === 'DECISION' && payload.decisionLevel) {
    lines.push(`判断结果：${payload.decisionLabel || getDecisionLevelLabel(payload.decisionLevel)}`)
  }
  if (type === 'BIND_ACTIVITY' && payload.boundActivityId) {
    lines.push(`绑定活动：${payload.boundActivityId}`)
  }
  if (type === 'LIBRARY_ENTRY' && payload.productTitle) {
    lines.push(`商品：${payload.productTitle}`)
  }
  if (payload.eventLabel && !lines.some((line) => line.includes(payload.eventLabel))) {
    lines.push(payload.eventLabel)
  }
  return lines.filter(Boolean)
}

export function buildOperationSummary(
  row: ProductOperationLogRow,
  context?: ProductOperationLogContext
): string {
  const payload = parseOperationPayload(row.operationPayload)
  const type = normalizeLogText(row.operationType)
  const remark = normalizeLogText(row.operationRemark)
  const assigneeName = payload.assigneeName || context?.assigneeName || '负责人'

  if (remark) {
    if (type === 'DECISION') {
      const levelLabel = payload.decisionLabel || getDecisionLevelLabel(payload.decisionLevel)
      return `${levelLabel}：${remark}`
    }
    return remark
  }

  switch (type) {
    case 'LIBRARY_ENTRY':
      return payload.productTitle
        ? `商品「${payload.productTitle}」已加入商品库`
        : '商品已加入商品库，对全员可见'
    case 'SYNC':
      return '活动商品已同步到本地商品池'
    case 'ASSIGN_AUDIT':
      return `已指定审核负责人：${assigneeName}`
    case 'ASSIGN':
      return `已分配给招商负责人：${assigneeName}`
    case 'AUDIT':
      return row.afterStatus === 'REJECTED' ? '审核未通过，商品未进入后续流程' : '审核通过，可继续分配与转链'
    case 'DECISION':
      return `${payload.decisionLabel || getDecisionLevelLabel(payload.decisionLevel)}：未填写原因`
    case 'BIND_ACTIVITY':
      return payload.boundActivityId
        ? `已绑定活动 ${payload.boundActivityId}`
        : '商品活动绑定已更新'
    case 'PROMOTION_LINK':
    case 'LINK':
      return '推广链接已生成，可继续分发或跟进达人'
    case 'TALENT_FOLLOW':
      return '商品已进入达人跟进流程'
    case 'TALENT_FOLLOW_APPEND':
      return '已追加达人跟进记录'
    default:
      return payload.eventLabel || '完成一次业务操作'
  }
}

export function mapProductOperationLogView(
  row: ProductOperationLogRow,
  index = 0,
  context?: ProductOperationLogContext
): ProductOperationLogView {
  const payload = parseOperationPayload(row.operationPayload)
  const type = normalizeLogText(row.operationType)
  const success = row.success !== false

  return {
    id: normalizeLogText(row.id) || `${type}-${index}`,
    timeLabel: formatOperationTime(row.createTime),
    eventLabel: getOperationTypeLabel(type),
    eventTagType: resolveEventTagType(row),
    summary: buildOperationSummary(row, context),
    detailLines: buildDetailLines(row, payload, context),
    statusFlow: formatStatusFlow(row.beforeStatus, row.afterStatus),
    operatorLabel: formatOperatorLabel(row),
    success,
    failureReason: success ? null : normalizeLogText(row.errorMessage) || '操作失败'
  }
}

export function mapProductOperationLogViews(
  rows: ProductOperationLogRow[],
  context?: ProductOperationLogContext
): ProductOperationLogView[] {
  return rows.map((row, index) => mapProductOperationLogView(row, index, context))
}
