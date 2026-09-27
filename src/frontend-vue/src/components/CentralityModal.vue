<template>
  <a-modal :open="open" title="中心度分析结果" width="800" @cancel="handleCancel">
    <a-tabs>
      <a-tab-pane key="degree" tab="度中心性">
        <a-table :columns="cols" :data-source="results.degree" row-key="id" />
      </a-tab-pane>
      <a-tab-pane key="closeness" tab="接近中心性">
        <a-table :columns="cols" :data-source="results.closeness" row-key="id" />
      </a-tab-pane>
      <a-tab-pane key="betweenness" tab="中介中心性">
        <a-table :columns="cols" :data-source="results.betweenness" row-key="id" />
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup>
import { defineEmits, defineProps, computed } from 'vue'

defineProps({ open: Boolean, results: Object })
const emit = defineEmits(['update:open'])

const cols = [
  { title: '节点ID', dataIndex: 'id' },
  { title: '名称', dataIndex: 'label' },
  { title: '中心度值', dataIndex: 'value', render: v => (v || 0).toFixed(4) }
]

function handleCancel() {
  emit('update:open', false)
}
</script>