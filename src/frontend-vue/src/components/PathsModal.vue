<template>
  <a-modal :open="open" title="路径列表" width="700" @cancel="handleCancel">
    <a-table :columns="columns" :data-source="paths" row-key="id" :pagination="false">
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'nodes'">
          <div class="path-nodes-short">
            <span v-for="(n, i) in record.nodes" :key="i">
              {{ n.name || n.label }}<span v-if="i < record.nodes.length - 1"> → </span>
            </span>
          </div>
        </template>
        <template v-if="column.dataIndex === 'actions'">
          <a-button size="small" @click="handleShowPath(record)">查看详情</a-button>
        </template>
      </template>
    </a-table>
  </a-modal>
</template>

<script setup>
import { defineEmits, defineProps } from 'vue'

defineProps({ open: Boolean, paths: Array })
const emit = defineEmits(['update:open', 'showPath'])

const columns = [
  { title: '路径ID', dataIndex: 'id' },
  { title: '长度', dataIndex: 'length' },
  { title: '节点序列', dataIndex: 'nodes' },
  { title: '操作', dataIndex: 'actions' }
]

function handleCancel() {
  emit('update:open', false)
}
function handleShowPath(record) {
  emit('showPath', record)
}
</script>

<style scoped>
.path-nodes-short { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>