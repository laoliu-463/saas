export type RecentDaysOption = 'today' | 'yesterday' | '15d' | '30d'

export type TimePreset = 'recent' | 'week' | 'month' | 'custom'

export const recentDaysOptions: Array<{ label: string; value: RecentDaysOption }> = [
  { label: '今天', value: 'today' },
  { label: '昨天', value: 'yesterday' },
  { label: '15天', value: '15d' },
  { label: '30天', value: '30d' }
]

export const startOfDay = (date: Date) => {
  const copy = new Date(date)
  copy.setHours(0, 0, 0, 0)
  return copy
}

export const endOfDay = (date: Date) => {
  const copy = new Date(date)
  copy.setHours(23, 59, 59, 999)
  return copy
}

export const buildTodayRange = (baseDate = new Date()): [number, number] => {
  const start = startOfDay(baseDate)
  const end = endOfDay(baseDate)
  return [start.getTime(), end.getTime()]
}

export const buildYesterdayRange = (baseDate = new Date()): [number, number] => {
  const yesterday = new Date(baseDate)
  yesterday.setDate(baseDate.getDate() - 1)
  return buildTodayRange(yesterday)
}

export const buildLastNDaysRange = (days: number, baseDate = new Date()): [number, number] => {
  const end = endOfDay(baseDate)
  const start = startOfDay(baseDate)
  start.setDate(end.getDate() - (days - 1))
  return [start.getTime(), end.getTime()]
}

export const buildRecentRange = (option: RecentDaysOption, baseDate = new Date()): [number, number] => {
  switch (option) {
    case 'today':
      return buildTodayRange(baseDate)
    case 'yesterday':
      return buildYesterdayRange(baseDate)
    case '15d':
      return buildLastNDaysRange(15, baseDate)
    case '30d':
      return buildLastNDaysRange(30, baseDate)
  }
}

export const buildWeekRange = (baseDate = new Date()): [number, number] => {
  const day = baseDate.getDay() || 7
  const start = startOfDay(baseDate)
  start.setDate(baseDate.getDate() - day + 1)
  const end = endOfDay(start)
  end.setDate(start.getDate() + 6)
  return [start.getTime(), end.getTime()]
}

export const buildMonthRange = (baseDate = new Date()): [number, number] => {
  const start = new Date(baseDate.getFullYear(), baseDate.getMonth(), 1)
  const end = endOfDay(new Date(baseDate.getFullYear(), baseDate.getMonth() + 1, 0))
  return [start.getTime(), end.getTime()]
}

export const getRecentPresetLabel = (
  timePreset: TimePreset,
  recentDaysOption: RecentDaysOption
) => {
  if (timePreset !== 'recent') {
    return '近N天'
  }
  return recentDaysOptions.find((option) => option.value === recentDaysOption)?.label ?? '近N天'
}
