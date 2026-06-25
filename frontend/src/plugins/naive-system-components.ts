import type { App, Component } from 'vue'
import {
  NAlert,
  NCard,
  NDataTable,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NGi,
  NGrid,
  NInputNumber,
  NModal,
  NPopconfirm,
  NSelect,
  NSpace,
  NSpin,
  NStatistic,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  NTimeline,
  NTimelineItem,
  NTree,
  NTreeSelect
} from 'naive-ui'

const systemNaiveComponents: Record<string, Component> = {
  NAlert,
  NCard,
  NDataTable,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NGi,
  NGrid,
  NInputNumber,
  NModal,
  NPopconfirm,
  NSelect,
  NSpace,
  NSpin,
  NStatistic,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  NTimeline,
  NTimelineItem,
  NTree,
  NTreeSelect
}

export const installSystemNaiveComponents = {
  install(app: App) {
    Object.entries(systemNaiveComponents).forEach(([name, component]) => {
      app.component(name, component)
    })
  }
}
