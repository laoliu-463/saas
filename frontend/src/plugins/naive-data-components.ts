import type { App, Component } from 'vue'
import {
  NAlert,
  NButtonGroup,
  NCard,
  NCheckbox,
  NCheckboxGroup,
  NDataTable,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NEmpty,
  NGi,
  NGrid,
  NList,
  NListItem,
  NModal,
  NPopover,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSkeleton,
  NSpace,
  NSpin,
  NStatistic,
  NTag,
  NThing,
  NTooltip
} from 'naive-ui'

const dataNaiveComponents: Record<string, Component> = {
  NAlert,
  NButtonGroup,
  NCard,
  NCheckbox,
  NCheckboxGroup,
  NDataTable,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NEmpty,
  NGi,
  NGrid,
  NList,
  NListItem,
  NModal,
  NPopover,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSkeleton,
  NSpace,
  NSpin,
  NStatistic,
  NTag,
  NThing,
  NTooltip
}

export const installDataNaiveComponents = {
  install(app: App) {
    Object.entries(dataNaiveComponents).forEach(([name, component]) => {
      app.component(name, component)
    })
  }
}
