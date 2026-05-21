import type { GlobalThemeOverrides } from 'naive-ui'
import { brand } from './brand'

export const naiveThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: brand.primary,
    primaryColorHover: brand.primaryHover,
    primaryColorPressed: brand.primaryPressed,
    primaryColorSuppl: brand.primaryHover,
    borderRadius: '10px',
    borderRadiusSmall: '6px',
    fontSize: '14px',
    fontSizeMedium: '14px',
    textColor1: brand.textPrimary,
    textColor2: brand.textSecondary,
    textColor3: brand.textTertiary,
    bodyColor: brand.body,
    cardColor: brand.card,
    modalColor: brand.card,
    popoverColor: brand.card,
    dividerColor: brand.border,
    borderColor: brand.border,
    boxShadow1: brand.shadow1,
    boxShadow2: brand.shadow2,
    boxShadow3: brand.shadow3
  },
  Button: {
    borderRadiusMedium: '8px',
    borderRadiusSmall: '6px',
    heightMedium: '36px',
    heightSmall: '30px',
    fontWeight: '600'
  },
  Card: {
    borderRadius: '14px',
    color: brand.card,
    colorModal: brand.card,
    boxShadow: brand.shadow2,
    borderColor: brand.border,
    titleFontWeight: '600'
  },
  Tag: {
    borderRadius: '6px'
  },
  Menu: {
    borderRadius: '8px',
    itemHeight: '42px',
    itemColorActive: brand.primaryLight,
    itemTextColorActive: brand.primary,
    itemTextColorActiveHover: brand.primaryPressed,
    itemIconColorActive: brand.primary,
    itemIconColorActiveHover: brand.primaryPressed
  },
  DataTable: {
    borderRadius: '10px',
    thColor: brand.cardMuted,
    thFontWeight: '600',
    tdColor: brand.card,
    tdColorStriped: '#fafbfc'
  },
  Tabs: {
    tabFontWeight: '500',
    tabFontWeightActive: '600'
  },
  Input: {
    borderRadius: '8px',
    heightMedium: '36px'
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '8px',
        heightMedium: '36px'
      }
    }
  },
  Modal: {
    borderRadius: '14px',
    boxShadow: brand.shadowModal
  },
  Dialog: {
    borderRadius: '14px'
  }
}
