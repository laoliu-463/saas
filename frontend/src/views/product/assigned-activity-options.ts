import { getColonelActivityPage } from '../../api/activity'
import type { ActivityRow } from './activity-list-display'

export type AssignedActivityOption = {
  label: string
  value: string
}

/** 与 GET /colonel/activities 的 pageSize 上限一致 */
export const ASSIGNED_ACTIVITY_PAGE_SIZE = 20

/** 防止异常 total 导致无限请求 */
const ASSIGNED_ACTIVITY_MAX_PAGES = 50

/** 将活动列表行映射为招商活动筛选项 */
export function mapActivityRowsToAssignedOptions(rows: ActivityRow[]): AssignedActivityOption[] {
  const seen = new Set<string>()
  const options: AssignedActivityOption[] = []
  for (const row of rows) {
    const value = String(row.activityId ?? '').trim()
    if (!value || seen.has(value)) continue
    seen.add(value)
    const name = String(row.activityName ?? '').trim()
    options.push({
      label: name ? `${name} (${value})` : value,
      value
    })
  }
  return options
}

/** 加载当前用户可同步的活动（admin=全部活动，招商=分配给我） */
export async function loadAssignedActivityOptions(isAdmin: boolean): Promise<AssignedActivityOption[]> {
  const assignmentFilter = isAdmin ? 'all' : 'mine'
  const allRows: ActivityRow[] = []
  let page = 1
  let total = Number.POSITIVE_INFINITY

  while (allRows.length < total && page <= ASSIGNED_ACTIVITY_MAX_PAGES) {
    const res: any = await getColonelActivityPage({
      page,
      pageSize: ASSIGNED_ACTIVITY_PAGE_SIZE,
      status: 0,
      searchType: 0,
      sortType: 1,
      assignmentFilter
    })
    const rows = (res?.data?.activityList || []) as ActivityRow[]
    if (!rows.length) break
    total = Number(res?.data?.total ?? allRows.length + rows.length)
    allRows.push(...rows)
    if (allRows.length >= total || rows.length < ASSIGNED_ACTIVITY_PAGE_SIZE) break
    page += 1
  }

  return mapActivityRowsToAssignedOptions(allRows)
}
