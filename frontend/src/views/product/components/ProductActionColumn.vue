<template>
  <div class="product-action-column">
    <n-space :size="6" vertical>
      <n-button
        v-for="item in mainActions"
        :key="item.key"
        text
        size="small"
        :type="item.danger ? 'error' : 'primary'"
        :disabled="item.disabled"
        :data-testid="`product-action-${item.key}`"
        @click.stop="emitAction(item.key)"
      >
        {{ item.label }}
      </n-button>
      <n-dropdown
        v-if="mergedMoreOptions.length"
        trigger="click"
        placement="bottom-end"
        to="body"
        :options="mergedMoreOptions"
        @select="handleSelect"
      >
        <n-button text size="small" type="primary" data-testid="product-action-more" @click.stop>
          更多
        </n-button>
      </n-dropdown>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getProductActions } from '../product-actions'
import type { ProductAction, ProductActionKey, ProductManageRow } from '../../../types/productManage'

const props = withDefaults(defineProps<{
  row: ProductManageRow
  roles?: string[]
  isAdmin?: boolean
  /** 是否允许置顶（由父级根据角色 + row.selectedToLibrary 决定） */
  canPin?: boolean
  /** 由父级注入的额外更多菜单项（例：复制讲解+短链 / 置顶） */
  extraMoreItems?: ProductAction[]
}>(), {
  roles: () => [],
  isAdmin: false,
  canPin: false,
  extraMoreItems: () => []
})

const emit = defineEmits<{
  action: [payload: { action: ProductActionKey; row: ProductManageRow }]
}>()

const actions = computed(() => getProductActions(props.row, { roles: props.roles, isAdmin: props.isAdmin }))
const mainActions = computed(() => actions.value.filter((item) => item.section === 'main'))
const moreActions = computed(() => actions.value.filter((item) => item.section === 'more'))
const pinMoreItem = computed<ProductAction | null>(() => {
  if (!props.canPin) return null
  const pinned = Boolean(props.row.pinned)
  return {
    key: pinned ? 'unpin' : 'pin',
    label: pinned ? '取消置顶' : '置顶',
    section: 'more',
    visible: true
  }
})
const mergedMoreOptions = computed(() =>
  [...moreActions.value, pinMoreItem.value, ...props.extraMoreItems]
    .filter((item): item is ProductAction => Boolean(item))
    .map((item) => ({
      label: item.disabled && item.reason ? `${item.label}（${item.reason}）` : item.label,
      key: item.key,
      disabled: item.disabled
    }))
)

function emitAction(action: ProductActionKey) {
  emit('action', { action, row: props.row })
}

function handleSelect(key: string | number) {
  emitAction(String(key) as ProductActionKey)
}
</script>

<style scoped>
.product-action-column {
  min-width: 96px;
}
</style>
