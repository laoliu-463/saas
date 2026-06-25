import type { App, Component } from 'vue'
import {
  NAvatar,
  NButton,
  NConfigProvider,
  NDialogProvider,
  NDropdown,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NLayout,
  NLayoutContent,
  NMenu,
  NMessageProvider
} from 'naive-ui'

const coreNaiveComponents: Record<string, Component> = {
  NAvatar,
  NButton,
  NConfigProvider,
  NDialogProvider,
  NDropdown,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NLayout,
  NLayoutContent,
  NMenu,
  NMessageProvider
}

export const installNaiveComponents = {
  install(app: App) {
    Object.entries(coreNaiveComponents).forEach(([name, component]) => {
      app.component(name, component)
    })
  }
}
