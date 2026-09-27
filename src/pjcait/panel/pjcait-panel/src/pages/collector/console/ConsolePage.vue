<script setup>
import { reactive, ref, watch } from 'vue'
import { apiUrls } from 'src/api/api-urls.js'
import { agentedClient, handleResponse } from 'src/api/client.js'
import { useInstanceStore } from 'stores/instance'
import dayjs from 'dayjs'
import { CronExpressionParser } from 'cron-parser'
import { useQuasar } from 'quasar'

const crawlTypesOptions = [
  { label: 'HTML直接请求', value: 0 },
  { label: 'Selenium', value: 1 },
  // {label: 'Adaptor', value: 2},
  { label: 'EasySpider', value: 3 },
  { label: '自定义爬取方式', value: 4 },
]

const loading = ref(true)
const serviceName = 'cait-collector'

const statusView = ref({})

const quasar = useQuasar()

const jobStateEnum = {
  0: '无',
  1: '正常',
  2: '已暂停',
  3: '完成',
  4: '错误',
  5: '锁定',
}

function getJobStateEnum(state) {
  return jobStateEnum[state] ? jobStateEnum[state] : String(state)
}

const instanceStore = useInstanceStore()

function getHeader() {
  return {
    'x-target-instance': instanceStore.selectedInstance[serviceName],
  }
}

const jobCron = ref('')

async function init() {

}

const cronEditDialogState = ref(false)

function openCronEditDialog() {
  cronEditDialogState.value = true
  generateExecPlan()
}

const cronExecPlan = ref([])

const cronOk = ref(false)

if (instanceStore.selectedInstance[serviceName]) {
  init()
}

watch(instanceStore.selectedInstance, async (newValue, oldValue) => {
  console.log('watch', newValue)
  if (newValue[serviceName]) {
    await init()
  }
})

watch(jobCron, () => {
  cronExecPlan.value = generateExecPlan()
})

function generateExecPlan() {
  try {
    const interval = CronExpressionParser.parse(jobCron.value)
    const execPlan = interval.take(4).map((date) => dayjs(date).format('YYYY-MM-DD HH:mm:ss'))
    cronOk.value = true
    return execPlan
  } catch (e) {
    cronOk.value = false
  }
}

function errorMsg2(e) {
  console.error(e)
  quasar.dialog({
    title: '啊哈？',
    message: e.message ? e.message : String(e),
    ok: true,
  })
}

function errorMsg(dialog, e) {
  console.error(e)
  dialog.update({
    title: '啊哈？',
    message: e.message,
    progress: false,
    ok: true,
  })
}

const inputContent = ref('')

async function putNews() {
  if (!inputContent.value) {
    errorMsg2('内容不能为空')
    return
  }

  const req = {
    contents: [inputContent.value],
  }

  console.log('req', req)

  const dialog = quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    message: '与服务器通信中...',
    progress: true,
    persistent: true,
    ok: false,
  })

  try {
    const response = await agentedClient.post(apiUrls.agented.collector.data.put, req, {
      headers: getHeader(),
    })
    const idList = handleResponse(response)
    dialog.update({
      title: '完成',
      message: `处理任务已发布，id: ${String(idList)}`,
      progress: false,
      ok: true,
    })
  } catch (e) {
    errorMsg(dialog, e)
  }
}
</script>

<template>
  <div class="p-3 w-full text-base">
    <div class="flex flex-col gap-6">
      <h1 class="text-4xl font-medium">控制台</h1>
      <div class="flex flex-col gap-2 w-1/2 min-w-96">
        <div class="text-2xl">新闻数据直接输入</div>
        <div class="flex flex-col gap-3">
          <div>在这里直接输入新闻内容，可立即将新闻入库并交由数据处理器处理</div>
          <div class="flex flex-col gap-1">
            <div>新闻内容</div>
            <q-input outlined v-model="inputContent" type="textarea" placeholder="新闻内容"></q-input>
          </div>
        </div>
        <q-btn unelevated color="primary" class="w-48" icon="post_add" @click="putNews()"
          >发送</q-btn
        >
      </div>
    </div>
  </div>
</template>

<style scoped></style>
