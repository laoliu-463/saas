import type { CooperationActionKey, SampleActionAvailabilityMap } from '../../types'

export const COOPERATION_ACTION_KEYS: CooperationActionKey[] = [
  'APPROVE',
  'REJECT',
  'EDIT',
  'PROGRESS',
  'COPY_LINK',
  'COPY_ORDER',
  'NOTE'
]

const LABELS: Record<CooperationActionKey, string> = {
  APPROVE: '通过',
  REJECT: '拒绝',
  EDIT: '修改订单',
  PROGRESS: '查看进度',
  COPY_LINK: '复制链接',
  COPY_ORDER: '复制订单',
  NOTE: '备注'
}

export type CooperationActionItem = {
  key: CooperationActionKey
  label: string
  enabled: boolean
  disabledReason: string | null
}

export function resolveCooperationActions(
  availability: SampleActionAvailabilityMap | null | undefined
): CooperationActionItem[] {
  return COOPERATION_ACTION_KEYS.map((key) => {
    const capability = availability?.[key]
    if (!capability) {
      return {
        key,
        label: LABELS[key],
        enabled: false,
        disabledReason: '服务端未返回该操作能力'
      }
    }
    return {
      key,
      label: LABELS[key],
      enabled: capability.enabled,
      disabledReason: capability.enabled
        ? null
        : capability.disabledReason?.trim() || '当前操作不可用'
    }
  })
}
