export type ProductOfficialStatus =
  | 'PENDING_REVIEW'
  | 'PROMOTING'
  | 'REJECTED'
  | 'TERMINATED'
  | 'EXPIRED'

export type ProductPublishStatus =
  | 'ALL'
  | 'UNPUBLISHED'
  | 'PUBLISHED'
  | 'PAUSED'

export type ProductReviewStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'

export type ProductDisplayStatus =
  | 'PENDING'
  | 'DISPLAYING'
  | 'HIDDEN'

export type ProductActionKey =
  | 'approve'
  | 'reject'
  | 'detail'
  | 'pause'
  | 'resume'
  | 'edit'
  | 'cooperationSetting'
  | 'sampleSetting'
  | 'copyScript'
  | 'downloadHandCard'
  | 'copyLink'
  | 'assign'
  | 'extendPromotion'
  | 'viewOrders'
  | 'openBaiying'
  | 'pin'
  | 'unpin'

export interface ProductCooperationSetting {
  cooperationType?: string
  sampleStandard?: string
  salesRequirement30d?: string | number
  talentLevelRequirement?: string | number
  supportsReport?: boolean
  supportsCollection?: boolean
  reportLimitExceeded?: boolean
  manualTagsEnabled?: boolean
  specialCommissionEnabled?: boolean
  remark?: string
}

export interface ProductSampleSetting {
  allowSample?: boolean
  sampleType?: string
  responsibleParty?: string
  requirement?: string
  salesRequirement30d?: string | number
  talentLevelRequirement?: string | number
  specOptions?: string[]
  remark?: string
}

export interface ProductManageRow {
  relationId?: string
  productId?: string
  productName?: string
  title?: string
  name?: string
  mainImage?: string
  cover?: string

  shopId?: string
  shopName?: string
  price?: number | string
  priceText?: string
  stock?: number | string
  stockText?: string
  productStock?: number | string
  category?: string
  categoryName?: string

  officialStatus?: ProductOfficialStatus
  publishStatus?: ProductPublishStatus
  reviewStatus?: ProductReviewStatus
  displayStatus?: ProductDisplayStatus
  bizStatus?: string
  auditStatus?: string | number
  status?: string | number
  statusText?: string
  allianceStatusText?: string
  latestDecisionLevel?: string

  commissionRate?: string | number
  dailyCommissionRate?: string | number
  promotionCommissionRate?: string | number
  serviceFeeRate?: string | number
  dailyServiceFeeRate?: string | number

  goodsTags?: string[]
  productTags?: string[]
  otherTags?: string[]
  systemTags?: string[]
  alertTags?: string[]

  recruiterUserId?: string
  recruiterName?: string
  assigneeName?: string

  cooperationSetting?: ProductCooperationSetting
  sampleSetting?: ProductSampleSetting
  auditSupplement?: Record<string, unknown>
  promotionMaterialPack?: Record<string, unknown>

  activityId?: string
  sourceActivityId?: string
  activityName?: string
  activityStartTime?: string
  activityEndTime?: string
  promotionStartTime?: string
  promotionEndTime?: string

  baiyingUrl?: string
  detailUrl?: string
  hasHandCard?: boolean
  handCardUrl?: string
  hasOrders?: boolean
  pinned?: boolean
  selectedToLibrary?: boolean
  promotionLink?: string

  canApprove?: boolean
  canReject?: boolean
  canEdit?: boolean
  canPause?: boolean
  canResume?: boolean
  canAssign?: boolean
  canExtendPromotion?: boolean
  canCopyScript?: boolean
  canCopyLink?: boolean
  canDownloadHandCard?: boolean
}

export interface ProductAction {
  key: ProductActionKey
  label: string
  visible: boolean
  disabled?: boolean
  reason?: string
  danger?: boolean
  section: 'main' | 'more'
}

export const officialStatusOptions: { label: string; value: ProductOfficialStatus }[] = [
  { label: '待审核', value: 'PENDING_REVIEW' },
  { label: '推广中', value: 'PROMOTING' },
  { label: '申请未通过', value: 'REJECTED' },
  { label: '合作已终止', value: 'TERMINATED' },
  { label: '合作已到期', value: 'EXPIRED' }
]

export const publishStatusOptions: { label: string; value: ProductPublishStatus }[] = [
  { label: '全部', value: 'ALL' },
  { label: '未发布', value: 'UNPUBLISHED' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '暂停发布', value: 'PAUSED' }
]

export const cooperationTypeOptions = [
  { label: '公开合作', value: 'PUBLIC' },
  { label: '定向合作', value: 'DIRECT' },
  { label: '专属合作', value: 'EXCLUSIVE' }
]

export const merchantStatusOptions = [
  { label: '正常', value: 'NORMAL' },
  { label: '待确认', value: 'PENDING' },
  { label: '异常', value: 'ABNORMAL' }
]

export const productMechanismOptions = [
  { label: '普通机制', value: 'STANDARD' },
  { label: '专属价', value: 'EXCLUSIVE_PRICE' },
  { label: '双佣金', value: 'DOUBLE_COMMISSION' },
  { label: '投流品', value: 'ADS' }
]
