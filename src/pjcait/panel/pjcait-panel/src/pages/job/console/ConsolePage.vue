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

async function fetchNodeJobState() {
  try {
    const response = await agentedClient.get(apiUrls.agented.collector.timer.state, {
      headers: getHeader(),
    })
    const data = handleResponse(response)
    console.log('data', data)

    const startTime = dayjs(data.startTime).format('YYYY-MM-DD HH:mm:ss')
    const endTime = dayjs(data.endTime).format('YYYY-MM-DD HH:mm:ss')
    const finalFireTime = dayjs(data.finalFireTime).format('YYYY-MM-DD HH:mm:ss')
    const nextFireTime = dayjs(data.nextFireTime).format('YYYY-MM-DD HH:mm:ss')
    const previousFireTime = dayjs(data.previousFireTime).format('YYYY-MM-DD HH:mm:ss')

    statusView.value = {
      state: getJobStateEnum(data.state),
      startTime: data.startTime ? startTime : '无数据',
      endTime: data.endTime ? endTime : '无数据',
      finalFireTime: data.finalFireTime ? finalFireTime : '无数据',
      nextFireTime: data.nextFireTime ? nextFireTime : '无数据',
      previousFireTime: data.previousFireTime ? previousFireTime : '无数据',
    }
  } catch (e) {
    errorMsg2(e)
  }
}

const jobCron = ref('')

async function fetchCron() {
  try {
    const response = await agentedClient.get(apiUrls.agented.collector.timer.cron, {
      headers: getHeader(),
    })
    jobCron.value = handleResponse(response)
  } catch (e) {
    errorMsg2(e)
  }
}

async function init() {
  loading.value = true
  await Promise.all([fetchNodeJobState(), fetchCron()])
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
    message: e.message,
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

async function updateCron() {
  if (!cronOk.value) {
    return
  }

  const dialog = quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    message: '与服务器通信中...',
    progress: true,
    persistent: true,
    ok: false,
  })

  try {
    const req = {
      cron: jobCron.value,
    }

    const response = await agentedClient.post(apiUrls.agented.collector.timer.update, req, {
      headers: getHeader(),
    })
    handleResponse(response)
    dialog.hide()
    cronEditDialogState.value = false
  } catch (e) {
    errorMsg(dialog, e)
  }

  await Promise.all([fetchNodeJobState(), fetchCron()])
}

async function executeNow() {
  const dialog = quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    message: '与服务器通信中...',
    progress: true,
    persistent: true,
    ok: false,
  })

  try {
    const response = await agentedClient.post(
      apiUrls.agented.collector.timer.executeNow,
      undefined,
      {
        headers: getHeader(),
      },
    )
    handleResponse(response)
    dialog.hide()
  } catch (e) {
    errorMsg(dialog, e)
  }

  await Promise.all([fetchNodeJobState(), fetchCron()])
}

async function pause() {
  const dialog = quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    message: '与服务器通信中...',
    progress: true,
    persistent: true,
    ok: false,
  })

  try {
    const response = await agentedClient.post(apiUrls.agented.collector.timer.pause, undefined, {
      headers: getHeader(),
    })
    handleResponse(response)
    dialog.hide()
  } catch (e) {
    errorMsg(dialog, e)
  }

  await Promise.all([fetchNodeJobState()])
}

async function resume() {
  const dialog = quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    message: '与服务器通信中...',
    progress: true,
    persistent: true,
    ok: false,
  })

  try {
    const response = await agentedClient.post(apiUrls.agented.collector.timer.resume, undefined, {
      headers: getHeader(),
    })
    handleResponse(response)
    dialog.hide()
  } catch (e) {
    errorMsg(dialog, e)
  }

  await Promise.all([fetchNodeJobState()])
}

const crawlTaskAddFormRef = reactive({
  urls: '',
  crawlType: 0,
  commitPublish: false,
  cleanHtml: true,
  additionalInfo: '',
})

async function addCrawlTask() {
  const urls = []
  const lines = crawlTaskAddFormRef.urls.split('\n')
  lines.forEach((line) => {
    if (line) urls.push(line)
  })
  if (urls.length < 1) {
    return
  }

  const additionalInfo = {}
  const infoLines = crawlTaskAddFormRef.additionalInfo.split('\n')
  infoLines.forEach((line) => {
    if (line) {
      const kv = line.split('=')
      if (kv.length > 1) {
        additionalInfo[kv[0].trim()] = kv[1].trim()
      }
    }
  })

  const req = {
    urls: urls,
    crawlType: crawlTaskAddFormRef.crawlType,
    cleanHtml: crawlTaskAddFormRef.cleanHtml,
    commitPublish: crawlTaskAddFormRef.commitPublish,
    additionalInfo: additionalInfo,
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
    const response = await agentedClient.post(apiUrls.agented.collector.collector.putUrls, req, {
      headers: getHeader(),
    })
    const idList = handleResponse(response)
    dialog.update({
      title: '完成',
      message: `数据抓取任务已发布，id: ${JSON.stringify(idList)}`,
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
      <div class="flex flex-row gap-6">
        <div class="flex flex-col gap-1">
          <div class="text-2xl">更新任务定时器状态：{{ statusView.state }}</div>
          <table class="border-spacing-1 border-separate">
            <tbody>
              <tr>
                <td>计时器开始时间：</td>
                <td>{{ statusView.startTime }}</td>
                <td>计时器结束时间：</td>
                <td>{{ statusView.endTime }}</td>
              </tr>
              <tr>
                <td>上次触发时间：</td>
                <td>{{ statusView.finalFireTime }}</td>
                <td>上次执行时间：</td>
                <td>{{ statusView.previousFireTime }}</td>
              </tr>
              <tr>
                <td>预计下次执行时间：</td>
                <td>{{ statusView.nextFireTime }}</td>
              </tr>
            </tbody>
          </table>
          <q-btn unelevated color="primary" class="w-48" icon="refresh" @click="fetchNodeJobState()"
            >更新
          </q-btn>
        </div>
        <q-separator vertical></q-separator>
        <div class="flex flex-col gap-1">
          <p class="text-2xl">执行动作</p>
          <div class="flex flex-col gap-2 justify-start">
            <div class="flex flex-row gap-4">
              <q-btn unelevated class="bg-amber-500 text-white w-32" icon="pause" @click="pause()"
                >暂停
              </q-btn>
              <q-btn
                unelevated
                class="bg-green-700 text-white w-32"
                icon="play_arrow"
                @click="resume()"
                >恢复
              </q-btn>
              <q-btn
                unelevated
                class="bg-indigo-400 text-white w-32"
                icon="terminal"
                @click="executeNow()"
                >立即执行
              </q-btn>
            </div>
            <q-btn
              unelevated
              class="bg-blue-600 text-white w-32"
              icon="edit"
              @click="openCronEditDialog()"
              >修改计划
            </q-btn>
          </div>
        </div>
      </div>
      <q-separator></q-separator>
      <div class="flex flex-col gap-2 w-1/2 min-w-96">
        <div class="text-2xl">数据收集任务发布</div>
        <div class="flex flex-col gap-3">
          <q-toggle
            v-model="crawlTaskAddFormRef.commitPublish"
            label="提交数据处理器处理"
          ></q-toggle>
          <q-toggle
            v-if="crawlTaskAddFormRef.crawlType !== 3 || crawlTaskAddFormRef.crawlType !== 4"
            v-model="crawlTaskAddFormRef.cleanHtml"
            label="清洗HTML"
          ></q-toggle>
          <q-select
            outlined
            v-model="crawlTaskAddFormRef.crawlType"
            :options="crawlTypesOptions"
            label="数据抓取类型"
            map-options
            emit-value
          />
          <q-separator></q-separator>
          <div class="flex flex-col gap-1">
            <div>待处理url列表，一行一个：</div>
            <q-input outlined v-model="crawlTaskAddFormRef.urls" type="textarea"></q-input>
          </div>
          <div class="flex flex-col gap-1">
            <div>
              额外参数，一行一项，以 'Key=Value' 格式填写，<br />
              如使用自定义爬取方式时，可以指定 crawler_id=102
            </div>
            <q-input
              outlined
              v-model="crawlTaskAddFormRef.additionalInfo"
              type="textarea"
            ></q-input>
          </div>
        </div>
        <q-btn unelevated color="primary" class="w-48" icon="post_add" @click="addCrawlTask()"
          >发送
        </q-btn>
      </div>
    </div>

    <q-dialog v-model="cronEditDialogState" no-shake transition-show="fade" transition-hide="fade">
      <q-card>
        <q-card-section>
          <div class="text-h6 select-none">修改任务Cron表达式</div>
        </q-card-section>

        <q-card-section class="q-pt-none flex flex-col gap-2">
          <div>在下方输入cron表达式以修改任务定时器</div>
          <div v-if="cronOk">
            <div>执行计划：</div>
            <p v-for="plan in cronExecPlan" :key="plan">{{ plan }}</p>
          </div>
          <div v-else class="text-red-600">表达式错误：{{ jobCron }}</div>
          <q-input outlined v-model="jobCron"></q-input>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="取消" color="primary" v-close-popup />
          <q-btn flat label="OK" color="primary" @click="updateCron()" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<style scoped></style>
