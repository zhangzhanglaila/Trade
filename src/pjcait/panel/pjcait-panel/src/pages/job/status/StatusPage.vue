<script setup>
import { reactive, ref, watch } from 'vue'
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
const pageSize = ref(10)
const totalPages = ref(1)
const totalResults = ref(0)

const recordsView = ref([])

const crawlTypes = {
  0: 'HTML直接请求',
  1: 'Selenium',
  2: 'Adaptor',
  3: 'EasySpider',
  4: '自定义爬取方式',
}

const jobStateEnum = {
  0: '就绪',// Ready
  1: '已发布',// Published
  2: '已接收',// Received
  3: '已完成',// Finished
  4: '失败',// Failed
}

function getJobStateEnum(state) {
  return jobStateEnum[state] ? jobStateEnum[state] : String(state)
}

async function init() {
  await getRecords()
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
  await getRecords()
})

async function getRecords() {
  const response = await agentedClient.get(apiUrls.agented.collector.status.crawlTask.list, {
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
    const additionalInfoText = []
    for (const info in record.additionalInfo) {
      additionalInfoText.push(`${info}=${record.additionalInfo[info]}`)
    }

    recordsView.value.push({
      id: record.id,
      crawlType: crawlTypes[record.crawlType],
      urls: record.urls.slice(0, 5).join('<br>'),
      additionalInfo: additionalInfoText.join('<br>'),
      createTime: dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss'),
      updateTime: dayjs(record.updateTime).format('YYYY-MM-DD HH:mm:ss'),
      status: getJobStateEnum(record.status),
      message: record.message,
    })
  }
}
</script>

<template>
  <div class="p-3 w-full text-base">
    <div class="flex flex-col gap-2">
      <h1 class="text-4xl font-medium">任务记录</h1>
      <div class="flex flex-col gap-2">
        <div>在这里可以查看数据抓取任务的记录</div>
        <q-markup-table separator="cell" flat bordered>
          <thead>
            <tr>
              <th class="text-left">序号</th>
              <th class="text-left">ID</th>
              <th class="text-left">抓取方式</th>
              <th class="text-left">URL列表</th>
              <th class="text-left">额外信息</th>
              <th class="text-left">创建时间</th>
              <th class="text-left">更新时间</th>
              <th class="text-left">状态</th>
              <th class="text-left">消息</th>
            </tr>
          </thead>
          <tbody class="text-base">
            <tr v-for="(record, i) in recordsView" :key="record">
              <td class="text-left">{{ i+1 }}</td>
              <td class="text-left">{{ record.id }}</td>
              <td class="text-left">{{ record.crawlType }}</td>
              <td class="text-left" v-html="record.urls"></td>
              <td class="text-left" v-html="record.additionalInfo"></td>
              <td class="text-left">{{ record.createTime }}</td>
              <td class="text-left">{{ record.updateTime }}</td>
              <td class="text-left">{{ record.status }}</td>
              <td class="text-left">
                <p class="text-wrap">
                  {{ record.message }}
                </p>
              </td>
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
  </div>
</template>

<style scoped></style>
