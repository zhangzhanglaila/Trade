<template>
  <a-modal :open="open" title="添加 / 编辑关系" @ok="handleOk" @cancel="handleCancel">
    <a-form :model="relation" layout="vertical">
      <a-form-item label="源节点">
        <a-select v-model:value="relation.sourceNodeId">
          <a-select-option v-for="n in nodes" :key="n.id" :value="n.id.toString()">{{ n.name }}</a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item label="目标节点">
        <a-select v-model:value="relation.targetNodeId">
          <a-select-option v-for="n in nodes" :key="n.id" :value="n.id.toString()">{{ n.name }}</a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item label="关系名称">
        <a-input v-model:value="relation.relationshipName" />
      </a-form-item>

      <a-form-item label="关系类型">
        <a-select v-model:value="relation.relationshipType">
          <a-select-option value="CLASS_RELATION">类属性关系</a-select-option>
          <a-select-option value="OBJECT_RELATION">对象属性关系</a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item label="描述">
        <a-textarea v-model:value="relation.description" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
/* 仅保留一次导入 */
import { defineEmits, defineProps } from 'vue'

const props = defineProps({
  open: Boolean,
  relation: Object,
  nodes: Array
})

const emit = defineEmits(['update:open', 'save'])

function handleOk() {
  emit('save')
  emit('update:open', false)
}

function handleCancel() {
  emit('update:open', false)
}
</script>