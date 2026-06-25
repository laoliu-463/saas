import type { App, Component } from 'vue'
import {
  NAlert,
  NCard,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NText
} from 'naive-ui'

const talentNaiveComponents: Record<string, Component> = {
  NAlert,
  NCard,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NText
}

export const installTalentNaiveComponents = {
  install(app: App) {
    Object.entries(talentNaiveComponents).forEach(([name, component]) => {
      app.component(name, component)
    })
  }
}
