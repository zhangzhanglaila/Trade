<template>
  <a-modal
    :open="open"
    :title="title"
    @cancel="handleCancel"
    :footer="null"
  >
    <!-- 1. 节点详情 -->
    <div v-if="type === 'node' && node?.id">
      <a-descriptions :column="1" bordered>
        <a-descriptions-item label="ID">{{ node.id }}</a-descriptions-item>
        <a-descriptions-item label="名称">{{ node.name }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ node.nodeType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="IRI">{{ node.iri || '-' }}</a-descriptions-item>
        <a-descriptions-item label="描述">{{ node.description || '-' }}</a-descriptions-item>
      </a-descriptions>

      <!-- 节点操作 -->
      <div style="margin-top: 16px; text-align: right">
        <a-button @click="handleEditNode">编辑</a-button>
        <a-button danger style="margin-left: 8px" @click="handleDeleteNode">删除</a-button>
      </div>
    </div>

    <!-- 2. 关系详情 -->
    <div v-else-if="type === 'relationship' && relationship?.id">
      <a-descriptions :column="1" bordered>
        <a-descriptions-item label="ID">{{ relationship.id }}</a-descriptions-item>
        <a-descriptions-item label="名称">{{ relationship.relationshipName }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ relationship.relationshipType }}</a-descriptions-item>
        <a-descriptions-item label="源节点">{{ getNodeName(relationship.sourceNodeId) }}</a-descriptions-item>
        <a-descriptions-item label="目标节点">{{ getNodeName(relationship.targetNodeId) }}</a-descriptions-item>
        <a-descriptions-item label="描述">{{ relationship.description || '-' }}</a-descriptions-item>
      </a-descriptions>

      <!-- 关系操作 -->
      <div style="margin-top: 16px; text-align: right">
        <a-button @click="handleEditRelation">编辑</a-button>
        <a-button danger style="margin-left: 8px" @click="handleDeleteRelation">删除</a-button>
      </div>
    </div>

    <!-- 3. 路径详情 -->
    <div v-else-if="type === 'path' && path.length">
      <p>路径长度：{{ path.length - 1 }}</p>
      <div class="path-nodes">
        <div v-for="(n, i) in path" :key="i" class="path-node">
          <span>{{ i + 1 }}. {{ n.name || n.label }}</span>
          <span v-if="i < path.length - 1" class="path-arrow"> → </span>
        </div>
      </div>
    </div>

    <!-- 4. 空状态 -->
    <div v-else>
      <a-empty description="暂无详情" />
    </div>
  </a-modal>
</template>

<script setup>
import { defineEmits, defineProps } from 'vue'

const props = defineProps({
  open: Boolean,
  title: String,
  type: String,
  node: Object,
  relationship: Object,
  path: Array,
  nodes: Array
})

const emit = defineEmits([
  'update:open',
  'editNode',
  'editRelation',
  'deleteNode',
  'deleteRelation'
])

function handleCancel() {
  emit('update:open', false)
}

function handleEditNode() {
  emit('editNode', props.node)
}

function handleEditRelation() {
  emit('editRelation', props.relationship)
}

function handleDeleteNode() {
  emit('deleteNode', props.node.id)
}

function handleDeleteRelation() {
  emit('deleteRelation', props.relationship.id)
}

function getNodeName(id) {
  const n = props.nodes.find(item => item.id.toString() === id.toString())
  return n ? n.name : id
}
</script>

<style scoped>
.path-nodes {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.path-arrow {
  margin: 0 4px;
  color: #999;
}
</style>