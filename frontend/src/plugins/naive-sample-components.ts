import type { App, Component } from 'vue'
import {
  NAlert,
  NCard,
  NDataTable,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NFormItemGi,
  NGi,
  NGrid,
  NInputNumber,
  NModal,
  NScrollbar,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NUpload
} from 'naive-ui'

const sampleNaiveComponents: Record<string, Component> = {
  NAlert,
  NCard,
  NDataTable,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NFormItemGi,
  NGi,
  NGrid,
  NInputNumber,
  NModal,
  NScrollbar,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NUpload
}

export const installSampleNaiveComponents = {
  install(app: App) {
    Object.entries(sampleNaiveComponents).forEach(([name, component]) => {
      app.component(name, component)
    })
  }
}
