<template>
  <div class="filter-row" data-testid="product-category-filter">
    <div class="filter-row-label">商品类目</div>
    <div class="filter-options">
      <button
        type="button"
        class="filter-chip"
        :class="{ active: !modelValue.length }"
        data-testid="category-filter-all"
        @click="$emit('update:modelValue', [])"
      >
        全部
      </button>
      <button
        v-for="option in options"
        :key="option.value"
        type="button"
        class="filter-chip"
        :class="{ active: modelValue.includes(option.value) }"
        data-testid="category-filter-option"
        :data-filter-value="option.value"
        @click="toggleCategory(option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  modelValue: string[]
  options: { label: string; value: string }[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

function toggleCategory(value: string) {
  if (props.modelValue.includes(value)) {
    emit('update:modelValue', props.modelValue.filter((item) => item !== value))
    return
  }
  emit('update:modelValue', [...props.modelValue, value])
}
</script>

<style scoped>
.filter-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 14px;
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid rgba(232, 69, 85, 0.1);
}

.filter-row-label {
  color: #6b1f2b;
  font-size: 13px;
  font-weight: 700;
  line-height: 30px;
  white-space: nowrap;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.filter-chip {
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(232, 69, 85, 0.16);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.72);
  color: #475569;
  cursor: pointer;
  font-size: 13px;
  line-height: 28px;
  transition: all 0.15s ease;
}

.filter-chip:hover {
  border-color: rgba(232, 69, 85, 0.36);
  color: #cf3344;
}

.filter-chip.active {
  border-color: #e84555;
  background: #e84555;
  color: #fff;
}

@media (max-width: 720px) {
  .filter-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .filter-row-label {
    line-height: 1.4;
  }
}
</style>
