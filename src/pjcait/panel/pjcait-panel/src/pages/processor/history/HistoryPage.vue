<script setup>
import { ref, watch } from 'vue'
import { agentedClient, handleResponse } from 'src/api/client.js'
import { apiUrls } from 'src/api/api-urls.js'
import { useInstanceStore } from 'stores/instance.js'
import dayjs from 'dayjs'
import { useRouter } from 'vue-router'

const tab = ref('custom')

const serviceName = 'cait-processor'
const instanceStore = useInstanceStore()

function getHeader() {
  return {
    'x-target-instance': instanceStore.selectedInstance[serviceName],
  }
}

const recordsView = ref([])

async function init() {
  await getHistory()
}

if (instanceStore.selectedInstance[serviceName]) {
  init()
}

watch(instanceStore.selectedInstance, async (newValue, oldValue) => {
  console.log('watch', newValue)
  if (newValue[serviceName]) {
    await init()
  }
})

async function getHistory() {
  const response = await agentedClient.get(apiUrls.agented.processor.history.list, {
    headers: getHeader(),
  })

  const resp = handleResponse(response)
  console.log('data', resp)

  recordsView.value = []
  for (const record of resp) {
    recordsView.value.push({
      id: record.id,
      taskId: record.task_id,
      summary: sliceText(record.content),
      createTime: dayjs(record.create_time).format('YYYY-MM-DD HH:mm:ss'),
    })
  }
}

const router = useRouter()

function jumpToDetail(id) {
  router.push(`record/${id}`)
}

function sliceText(text, maxLength) {
  if (!maxLength) {
    maxLength = 32
  }

  if (text.length < maxLength) {
    return text
  }

  return text.slice(0, maxLength) + '...'
}

</script>

<template>
  <div class="p-3 w-full text-base">
    <div class="flex flex-col gap-2">
      <h1 class="text-4xl font-medium">输入记录（手动）</h1>
      <div class="flex flex-col gap-2">
        <div>在这里可以查看先前手动输入的处理记录，点击一项进入详情页</div>
        <q-markup-table separator="cell" flat bordered>
          <thead>
            <tr>
              <th class="text-left">序号</th>
              <th class="text-left">ID</th>
              <th class="text-left">任务id</th>
              <th class="text-left">预览</th>
              <th class="text-left">创建时间</th>
            </tr>
          </thead>
          <tbody class="text-base">
            <tr
              v-for="(record, i) in recordsView"
              :key="record"
              @click="() => jumpToDetail(record.taskId)"
            >
              <td class="text-left">{{ i + 1 }}</td>
              <td class="text-left">{{ record.id }}</td>
              <td class="text-left">{{ record.taskId }}</td>
              <td class="text-left">{{ record.summary }}</td>
              <td class="text-left">{{ record.createTime }}</td>
            </tr>
          </tbody>
        </q-markup-table>
      </div>
    </div>
  </div>
</template>

<style scoped lang="sass"></style>
