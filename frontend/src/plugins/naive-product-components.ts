import type { App, Component } from 'vue'
import {
  NAlert,
  NCard,
  NCheckbox,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NDatePicker,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NGi,
  NGrid,
  NImage,
  NInputNumber,
  NLog,
  NModal,
  NRadio,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSpin,
  NStatistic,
  NStep,
  NSteps,
  NTabPane,
  NTabs,
  NTag
} from 'naive-ui'

const productNaiveComponents: Record<string, Component> = {
  NAlert,
  NCard,
  NCheckbox,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NDatePicker,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NGi,
  NGrid,
  NImage,
  NInputNumber,
  NLog,
  NModal,
  NRadio,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSpin,
  NStatistic,
  NStep,
  NSteps,
  NTabPane,
  NTabs,
  NTag
}

export const installProductNaiveComponents = {
  install(app: App) {
    Object.entries(productNaiveComponents).forEach(([name, component]) => {
      app.component(name, component)
    })
  }
}
