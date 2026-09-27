<template>
  <a-modal :open="open" title="添加 / 编辑实体" @ok="handleOk" @cancel="handleCancel">
    <a-form :model="entity" layout="vertical">
      <a-form-item label="名称">
        <a-input v-model:value="entity.name" />
      </a-form-item>
      <a-form-item label="类型">
       <a-select v-model:value="entity.nodeType">
  <a-select-option value="CLASS">CLASS（类型）</a-select-option>
  <a-select-option value="INDIVIDUAL">INDIVIDUAL（实例）</a-select-option>
</a-select>
      </a-form-item>
      <a-form-item label="IRI">
        <a-input v-model:value="entity.iri" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="entity.description" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { defineEmits, defineProps, watch } from 'vue'

const props = defineProps({ open: Boolean, entity: Object })
const emit = defineEmits(['update:open', 'save'])

function handleOk() {
  emit('save')
  emit('update:open', false)
}
function handleCancel() {
  emit('update:open', false)
}
</script>