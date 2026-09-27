<template>
  <a-modal :open="open" title="模式匹配结果" width="700" @cancel="handleCancel">
    <a-table :columns="columns" :data-source="results" row-key="id">
      <template #bodyCell="{ record }">
        <td>
          <div v-if="record.nodes && record.relationships">
            <div v-for="(n, i) in record.nodes" :key="i" class="pattern-result-item">
              <strong>节点{{ i + 1 }}:</strong> {{ n.name || n.label }}({{ n.type }})
              <div v-if="i < record.relationships.length" class="pattern-result-relation">
                <strong>关系{{ i + 1 }}:</strong> {{ record.relationships[i].relationshipName }}({{ record.relationships[i].relationshipType }})
              </div>
            </div>
          </div>
        </td>
      </template>
    </a-table>
  </a-modal>
</template>

<script setup>
import { defineEmits, defineProps } from 'vue'

defineProps({ open: Boolean, results: Array })
const emit = defineEmits(['update:open'])

const columns = [
  { title: '匹配ID', dataIndex: 'id' },
  { title: '匹配详情', dataIndex: 'detail' }
]

function handleCancel() {
  emit('update:open', false)
}
</script>

<style scoped>
.pattern-result-item { margin-bottom: 8px; }
.pattern-result-relation { padding-left: 16px; color: #666; }
</style>