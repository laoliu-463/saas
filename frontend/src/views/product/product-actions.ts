import { hasAccess } from '../../constants/rbac'
import type {
  ProductAction,
  ProductActionKey,
  ProductManageRow,
  ProductOfficialStatus,
  ProductPublishStatus,
  ProductReviewStatus
} from '../../types/productManage'

export interface ProductActionContext {
  roles?: string[]
  isAdmin?: boolean
}

const officialStatusValues = new Set<ProductOfficialStatus>([
  'PENDING_REVIEW',
  'PROMOTING',
  'REJECTED',
  'TERMINATED',
  'CANCELED',
  'EXPIRED'
])

const publishStatusValues = new Set<ProductPublishStatus>([
  'ALL',
  'UNPUBLISHED',
  'PUBLISHED',
  'PAUSED'
])

const reviewStatusValues = new Set<ProductReviewStatus>([
  'PENDING',
  'APPROVED',
  'REJECTED'
])

const normalizeText = (value?: string | number | null) => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  return text === 'null' || text === 'undefined' ? '' : text
}

const explicitOfficialStatus = (value?: string | null): ProductOfficialStatus | null => {
  const status = normalizeText(value) as ProductOfficialStatus
  return officialStatusValues.has(status) ? status : null
}

const explicitPublishStatus = (value?: string | null): ProductPublishStatus | null => {
  const status = normalizeText(value) as ProductPublishStatus
  return publishStatusValues.has(status) ? status : null
}

const explicitReviewStatus = (value?: string | null): ProductReviewStatus | null => {
  const status = normalizeText(value) as ProductReviewStatus
  return reviewStatusValues.has(status) ? status : null
}

export function resolveOfficialStatus(row: ProductManageRow): ProductOfficialStatus {
  const direct = explicitOfficialStatus(row.officialStatus)
  if (direct) return direct

  const statusCode = normalizeText(row.status)
  if (statusCode === '0') return 'PENDING_REVIEW'
  if (statusCode === '1') return 'PROMOTING'
  if (statusCode === '2') return 'REJECTED'
  if (statusCode === '3') return 'TERMINATED'
  if (statusCode === '4') return 'CANCELED'
  if (statusCode === '6') return 'EXPIRED'

  const statusText = normalizeText(row.statusText || row.allianceStatusText)
  if (statusText.includes('待审核') || statusText.includes('审核中')) return 'PENDING_REVIEW'
  if (statusText.includes('未通过') || statusText.includes('拒绝')) return 'REJECTED'
  if (statusText.includes('合作前取消') || statusText.includes('取消')) return 'CANCELED'
  if (statusText.includes('终止')) return 'TERMINATED'
  if (statusText.includes('到期') || statusText.includes('过期')) return 'EXPIRED'
  if (statusText.includes('推广')) return 'PROMOTING'

  const bizStatus = normalizeText(row.bizStatus)
  if (bizStatus === 'PENDING_AUDIT') return 'PENDING_REVIEW'
  if (bizStatus === 'REJECTED') return 'REJECTED'
  if (['APPROVED', 'BOUND', 'ASSIGNED', 'LINKED', 'FOLLOWING'].includes(bizStatus)) return 'PROMOTING'

  return 'PENDING_REVIEW'
}

export function resolvePublishStatus(row: ProductManageRow): ProductPublishStatus {
  const direct = explicitPublishStatus(row.publishStatus)
  if (direct && direct !== 'ALL') return direct

  const bizStatus = normalizeText(row.bizStatus)
  if (row.manualDisabled === true || bizStatus === 'PAUSED' || row.latestDecisionLevel === 'PAUSE') return 'PAUSED'
  if (row.selectedToLibrary || normalizeText(row.promotionLink)) return 'PUBLISHED'
  return 'UNPUBLISHED'
}

export function resolveReviewStatus(row: ProductManageRow): ProductReviewStatus {
  const direct = explicitReviewStatus(row.reviewStatus)
  if (direct) return direct

  const auditStatus = normalizeText(row.auditStatus)
  if (auditStatus === '1' || auditStatus === 'PENDING') return 'PENDING'
  if (auditStatus === '2' || auditStatus === 'APPROVED') return 'APPROVED'
  if (auditStatus === '3' || auditStatus === 'REJECTED') return 'REJECTED'

  const bizStatus = normalizeText(row.bizStatus)
  if (bizStatus === 'PENDING_AUDIT') return 'PENDING'
  if (bizStatus === 'REJECTED') return 'REJECTED'
  if (['APPROVED', 'BOUND', 'ASSIGNED', 'LINKED', 'FOLLOWING'].includes(bizStatus)) return 'APPROVED'

  const officialStatus = resolveOfficialStatus(row)
  if (officialStatus === 'PENDING_REVIEW') return 'PENDING'
  if (officialStatus === 'REJECTED') return 'REJECTED'
  return 'APPROVED'
}

const hasRole = (context: ProductActionContext, roles: string[]) =>
  Boolean(context.isAdmin) || hasAccess(context.roles || [], roles)

const canByRow = (
  row: ProductManageRow,
  field: keyof Pick<
    ProductManageRow,
    | 'canApprove'
    | 'canReject'
    | 'canEdit'
    | 'canPause'
    | 'canResume'
    | 'canAssign'
    | 'canExtendPromotion'
    | 'canCopyScript'
    | 'canCopyLink'
    | 'canDownloadHandCard'
  >,
  fallback: boolean
) => {
  const value = row[field]
  return typeof value === 'boolean' ? value : fallback
}

const action = (
  key: ProductActionKey,
  label: string,
  visible: boolean,
  section: ProductAction['section'],
  extra: Partial<Omit<ProductAction, 'key' | 'label' | 'visible' | 'section'>> = {}
): ProductAction => ({ key, label, visible, section, ...extra })

export function getProductActions(row: ProductManageRow, context: ProductActionContext = {}): ProductAction[] {
  const officialStatus = resolveOfficialStatus(row)
  const reviewStatus = resolveReviewStatus(row)
  const publishStatus = resolvePublishStatus(row)
  const isLocalRejected = reviewStatus === 'REJECTED'
  const isPendingReview = officialStatus === 'PENDING_REVIEW' && reviewStatus !== 'APPROVED'
  const isUpstreamPromoting = officialStatus === 'PROMOTING'
  const canOperateProduct = hasRole(context, ['biz_leader', 'biz_staff'])
  const canAssign = canByRow(row, 'canAssign', hasRole(context, ['biz_leader']))
  const canApprove = canByRow(row, 'canApprove', hasRole(context, ['biz_staff']))
  const canReject = canByRow(row, 'canReject', hasRole(context, ['biz_staff']))
  const canEdit = canByRow(row, 'canEdit', canOperateProduct)
  const canPause = canByRow(row, 'canPause', canOperateProduct)
  const canResume = canByRow(row, 'canResume', canOperateProduct)
  const canExtendPromotion = canByRow(row, 'canExtendPromotion', canOperateProduct)
  const canCopyScript = canByRow(row, 'canCopyScript', hasRole(context, ['biz_leader', 'biz_staff', 'channel_leader', 'channel_staff']))
  const canCopyLink = canByRow(row, 'canCopyLink', hasRole(context, ['biz_leader', 'biz_staff', 'channel_leader', 'channel_staff']))
  const canDownloadHandCard = canByRow(row, 'canDownloadHandCard', true)

  if (officialStatus === 'REJECTED' || (isLocalRejected && !isUpstreamPromoting)) {
    return [
      action('detail', '查看详情', true, 'main')
    ].filter((item) => item.visible)
  }

  if (isPendingReview) {
    return [
      action('approve', '通过', canApprove, 'main'),
      action('reject', '拒绝', canReject, 'main', { danger: true }),
      action('detail', '查看详情', true, 'main')
    ].filter((item) => item.visible)
  }

  if (isUpstreamPromoting && publishStatus === 'PAUSED') {
    return [
      action('resume', '恢复发布', canResume, 'main'),
      action('edit', '编辑商品', canEdit, 'main'),
      action('cooperationSetting', '合作设置', canEdit, 'main'),
      action('sampleSetting', '寄样设置', canEdit, 'main'),
      action('detail', '查看详情', true, 'main')
    ].filter((item) => item.visible)
  }

  if (isUpstreamPromoting) {
    return [
      action('pause', '暂停发布', canPause, 'main'),
      action('edit', '编辑商品', canEdit, 'main'),
      action('cooperationSetting', '合作设置', canEdit, 'main'),
      action('sampleSetting', '寄样设置', canEdit, 'main'),
      action('copyScript', '复制话术', canCopyScript, 'more'),
      action('downloadHandCard', '下载手卡', canDownloadHandCard, 'more', {
        disabled: !row.hasHandCard && !row.handCardUrl,
        reason: '暂无手卡'
      }),
      action('detail', '查看详情', true, 'more'),
      action('copyLink', '复制链接', canCopyLink, 'more'),
      action('assign', '分配', canAssign, 'more'),
      action('extendPromotion', '延期推广', canExtendPromotion, 'more'),
      action('viewOrders', '查看出单', true, 'more', {
        disabled: row.hasOrders === false,
        reason: '暂无出单'
      }),
      action('openBaiying', '前往百应', true, 'more', {
        disabled: !row.baiyingUrl && !row.detailUrl,
        reason: '暂无百应链接'
      })
    ].filter((item) => item.visible)
  }

  if (officialStatus === 'TERMINATED' || officialStatus === 'CANCELED') {
    return [
      action('detail', '查看详情', true, 'main'),
      action('viewOrders', '查看出单', row.hasOrders === true, 'main')
    ].filter((item) => item.visible)
  }

  if (officialStatus === 'PENDING_REVIEW') {
    return [
      action('detail', '查看详情', true, 'main')
    ]
  }

  return [
    action('detail', '查看详情', true, 'main'),
    action('extendPromotion', '延期推广', canExtendPromotion, 'main'),
    action('viewOrders', '查看出单', row.hasOrders === true, 'main')
  ].filter((item) => item.visible)
}
