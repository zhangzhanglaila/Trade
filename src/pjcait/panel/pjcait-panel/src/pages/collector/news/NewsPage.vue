<script setup>
import { ref, watch } from 'vue'
import { agentedClient, handleResponse } from 'src/api/client.js'
import { apiUrls } from 'src/api/api-urls.js'
import { useInstanceStore } from 'stores/instance.js'
import dayjs from 'dayjs'

const tab = ref('custom')

const serviceName = 'cait-collector'
const instanceStore = useInstanceStore()

function getHeader() {
  return {
    'x-target-instance': instanceStore.selectedInstance[serviceName],
  }
}

const page = ref(1)
const pageSize = ref(15)
const totalPages = ref(1)
const totalResults = ref(0)

const recordsView = ref([])

async function init() {
  await getNews()
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

watch(page, async () => {
  await getNews()
})

async function getNews() {
  const response = await agentedClient.get(apiUrls.agented.collector.data.list, {
    headers: getHeader(),
    params: { page: page.value, pageSize: pageSize.value },
  })

  const resp = handleResponse(response)
  console.log('data', resp)

  pageSize.value = resp.pageSize
  totalPages.value = resp.totalPage
  totalResults.value = resp.totalResult

  recordsView.value = []
  for (const record of resp.data) {
    recordsView.value.push({
      id: record.id,
      contentId: record.contentId,
      taskId: record.taskId,
      title: record.title,
      content: record.content,
      createTime: dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss'),
    })
  }
}

function HTMLEncode(html) {
  const temp = document.createElement('div')
  temp.textContent != null ? (temp.textContent = html) : (temp.innerText = html)
  return temp.innerHTML
}

function sliceText(text, maxLength) {
  if (text.length < maxLength) {
    return text
  }

  return text.slice(0, maxLength) + '...'
}

const fullContent = ref('')
const contentPanelStatus = ref(false)

function openContentPanel(content) {
  fullContent.value = HTMLEncode(content)
  contentPanelStatus.value = true
}
</script>

<template>
  <div class="p-3 w-full text-base">
    <div class="flex flex-col gap-2">
      <h1 class="text-4xl font-medium">新闻数据</h1>
      <div class="flex flex-col gap-2">
        <div>在这里可以查看已经存档的新闻数据</div>
        <q-markup-table separator="cell" flat bordered>
          <thead>
            <tr>
              <th class="text-left">序号</th>
              <th class="text-left">ID</th>
              <th class="text-left">内容ID</th>
              <th class="text-left">所属任务ID</th>
              <th class="text-left">内容</th>
              <th class="text-left">创建时间</th>
            </tr>
          </thead>
          <tbody class="text-base">
            <tr v-for="(record, i) in recordsView" :key="record">
              <td class="text-left">{{ i+1 }}</td>
              <td class="text-left">{{ record.id }}</td>
              <td class="text-left">
                <p class="text-wrap break-all">
                  {{ record.contentId }}
                </p>
              </td>
              <td class="text-left">{{ record.taskId }}</td>
              <td class="text-left">
                <p class="text-wrap" @click="openContentPanel(record.content)">
                  {{ sliceText(record.content, 100) }}
                </p>
              </td>
              <td class="text-left">{{ record.createTime }}</td>
            </tr>
          </tbody>
        </q-markup-table>
        <q-pagination
          v-model="page"
          :max="totalPages"
          direction-links
          outline
          color="primary"
          active-design="unelevated"
        />
      </div>
    </div>

    <q-dialog v-model="contentPanelStatus" no-shake transition-show="fade" transition-hide="fade">
      <q-card class="newsContent w-fit">
        <q-card-section class="sticky top-0 z-10 bg-white">
          <div class="text-h6 select-none">完整内容</div>
        </q-card-section>

        <q-card-section class="q-pt-none flex flex-col">
          <div class="p-2 bg-blue-50 rounded">
            <p class="" v-html="fullContent.replaceAll('\n', '<br>')"></p>
          </div>
        </q-card-section>

        <q-card-actions align="right" class="sticky bottom-0 z-10 bg-white">
          <q-btn flat label="OK" color="primary" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<style scoped lang="sass">
.newsContent
  max-width: 65vw
</style>
