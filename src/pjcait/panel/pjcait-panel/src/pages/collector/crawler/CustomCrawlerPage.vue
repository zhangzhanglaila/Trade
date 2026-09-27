<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { agentedClient, handleResponse } from 'src/api/client.js'
import { apiUrls } from 'src/api/api-urls.js'
import { useInstanceStore } from 'stores/instance.js'
import dayjs from 'dayjs'
import { useQuasar } from 'quasar'

const serviceName = 'cait-collector'
const instanceStore = useInstanceStore()

function getHeader() {
  return {
    'x-target-instance': instanceStore.selectedInstance[serviceName],
  }
}

const crawlerView = ref([])

const crawlTypes = {
  0: 'HTML直接请求',
  1: 'Selenium',
  2: 'Adaptor',
  3: 'EasySpider',
  4: '自定义爬取方式',
}

const crawlTypesOptions = [
  { label: 'HTML直接请求', value: 0 },
  { label: 'Selenium', value: 1 },
  // {label: 'Adaptor', value: 2},
  { label: 'EasySpider', value: 3 },
  { label: '自定义爬取方式', value: 4 },
]

const status = {
  Enable: 0,
  Disable: 1,
}

const statusEnum = {
  0: '启用',
  1: '禁用',
}

const customCrawlers = defineModel('crawlers')

async function init() {
  refreshCrawlersView(customCrawlers.value)
}

onMounted(() => {
  if (customCrawlers.value) {
    init()
  }
})

watch(customCrawlers, async (newValue, oldValue) => {
  refreshCrawlersView(newValue)
})

function refreshCrawlersView(newValue) {
  crawlerView.value = []
  for (let i = 0; i < newValue.length; i++) {
    const crawler = newValue[i]
    const params = []
    try {
      const p = JSON.parse(crawler.params)
      for (const k in p) {
        params.push(`${k}=${p[k]}`)
      }
    } catch (e) {
      console.log(e)
    }

    crawlerView.value.push({
      idx: i,
      id: String(crawler.id),
      name: crawler.name,
      reqUrl: crawler.reqUrl,
      params: params.join('<br>'),
      createTime: dayjs(crawler.createTime).format('YYYY-MM-DD HH:mm:ss'),
      updateTime: dayjs(crawler.updateTime).format('YYYY-MM-DD HH:mm:ss'),
      status: crawler.status,
    })
  }
}

defineExpose({
  refreshCrawlersView,
})

const crawlerFormRef = reactive({
  id: '',
  name: '',
  reqUrl: '',
  params: '',
  status: 0,
})
const editDialogState = ref(false)
const editMode = ref('edit')

const func = defineEmits(['onUpdate'])

function openEditDialog(mode, form) {
  editMode.value = mode
  editDialogState.value = true

  if (!form) {
    crawlerFormRef.name = ''
    crawlerFormRef.reqUrl = ''
    crawlerFormRef.params = ''
    crawlerFormRef.status = 0
  } else {
    crawlerFormRef.id = String(form.id)
    crawlerFormRef.name = form.name
    crawlerFormRef.reqUrl = form.reqUrl
    crawlerFormRef.params = form.params.replace('<br>', '\n')
  }
}

function loadingDialog() {
  return quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    message: '与服务器通信中...',
    progress: true,
    persistent: true,
    ok: false,
  })
}

function ensureDialog(title, msg) {
  return quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    title: title,
    message: msg,
    ok: true,
    cancel: true,
  })
}

function msgDialog(title, msg) {
  return quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    title: title,
    message: msg,
    ok: true,
  })
}

function errorMsg(dialog, e) {
  console.error(e)
  dialog.update({
    title: '啊哈？',
    message: e.message ? e.message : String(e),
    progress: false,
    ok: true,
  })
}

function errorMsg2(e) {
  console.error(e)
  quasar.dialog({
    title: '啊哈？',
    message: e.message ? e.message : String(e),
    ok: true,
  })
}

const quasar = useQuasar()

function checkForm() {
  if (!crawlerFormRef.name || !crawlerFormRef.reqUrl) {
    msgDialog('参数错误', '抓取器的名称和请求地址都不能为空')
    return false
  }

  try {
    new URL(crawlerFormRef.reqUrl)
  } catch (e) {
    msgDialog('参数错误', `URL不正确： ${crawlerFormRef.reqUrl}`)
    return false
  }

  return true
}

async function addCrawlerAction() {
  if (!checkForm()) {
    return false
  }

  const dialog = loadingDialog()
  try {
    await addCrawler(crawlerFormRef)
    dialog.hide()
    editDialogState.value = false
    func('onUpdate')
  } catch (e) {
    errorMsg(dialog, e)
  }

  return dialog
}

const crawlerUrl = apiUrls.agented.collector.configure.crawler.custom

function buildReq(form) {
  const req = {
    name: form.name,
    reqUrl: form.reqUrl,
    params: undefined,
  }

  const params = {}
  const kvStrs = form.params.split('\n')
  for (const kvStr of kvStrs) {
    const kv = kvStr.split('=')
    if (kv.length > 1) {
      params[kv[0].trim()] = kv[1].trim()
    }
  }
  req.params = params

  return req
}

async function addCrawler(form) {
  const req = buildReq(form)
  const response = await agentedClient.post(crawlerUrl.add, req, {
    headers: getHeader(),
  })

  return handleResponse(response)
}

function deleteCrawlerAction(crawler) {
  ensureDialog('确认', `确认删除 '${crawler.name}'？`).onOk(async () => {
    const dialog = loadingDialog()
    try {
      await deleteCrawler(crawler.id)
      dialog.hide()
      func('onUpdate')
    } catch (e) {
      errorMsg(dialog, e)
    }
  })
}

async function deleteCrawler(id) {
  const response = await agentedClient.post(crawlerUrl.delete, undefined, {
    headers: getHeader(),
    params: { id: id },
  })

  return handleResponse(response)
}

async function updateCrawlerAction() {
  if (!checkForm()) {
    return
  }

  const dialog = loadingDialog()
  try {
    await updateCrawler(crawlerFormRef)
    dialog.hide()
    editDialogState.value = false
    func('onUpdate')
  } catch (e) {
    errorMsg(dialog, e)
  }

  return dialog
}

async function updateCrawler(form) {
  const req = buildReq(form)

  const response = await agentedClient.post(crawlerUrl.update, req, {
    params: { id: form.id },
    headers: getHeader(),
  })

  return handleResponse(response)
}

async function switchCrawlerStatusAction(crawler, status) {
  const id = crawler.id
  switch (status) {
    case 'enable': {
      try {
        await enableCrawler(id)
        func('onUpdate')
      } catch (e) {
        errorMsg2(e)
      }
      break
    }
    case 'disable': {
      try {
        await disableCrawler(id)
        func('onUpdate')
      } catch (e) {
        errorMsg2(e)
      }
      break
    }
  }
}

async function disableCrawler(id) {
  const response = await agentedClient.post(crawlerUrl.setStatus, undefined, {
    headers: getHeader(),
    params: { id: id, status: status.Disable },
  })

  return handleResponse(response)
}

async function enableCrawler(id) {
  const response = await agentedClient.post(crawlerUrl.setStatus, undefined, {
    headers: getHeader(),
    params: { id: id, status: status.Enable },
  })

  return handleResponse(response)
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div>
      <q-btn outline color="primary" class="w-32" @click="openEditDialog('add', undefined)"
        >添加
      </q-btn>
    </div>
    <q-markup-table separator="cell" flat bordered>
      <thead>
        <tr>
          <th class="text-left">序号</th>
          <th class="text-left">ID</th>
          <th class="text-left">名称</th>
          <th class="text-left">请求地址</th>
          <th class="text-left">参数</th>
          <th class="text-left">创建时间</th>
          <th class="text-left">更新时间</th>
          <th class="text-left">状态</th>
          <th class="text-left">操作</th>
        </tr>
      </thead>
      <tbody class="text-base">
        <tr v-for="crawler in crawlerView" :key="crawler">
          <td class="text-left">{{ crawler.idx }}</td>
          <td class="text-left">{{ crawler.id }}</td>
          <td class="text-left">{{ crawler.name }}</td>
          <td class="text-left">{{ crawler.reqUrl }}</td>
          <td class="text-left" v-html="crawler.params"></td>
          <td class="text-left">{{ crawler.createTime }}</td>
          <td class="text-left">{{ crawler.updateTime }}</td>
          <td class="text-left">{{ statusEnum[crawler.status] }}</td>
          <td class="text-left">
            <div class="flex flex-row gap-2 justify-start items-center">
              <q-btn
                v-if="crawler.status != 0"
                outline
                color="green"
                @click="switchCrawlerStatusAction(crawler, 'enable')"
                >启用
              </q-btn>
              <q-btn
                v-else
                outline
                color="gray"
                @click="switchCrawlerStatusAction(crawler, 'disable')"
                >禁用
              </q-btn>
              <q-btn outline color="primary" @click="openEditDialog('edit', crawler)"
                >编辑
              </q-btn>
              <q-btn outline color="red" @click="deleteCrawlerAction(crawler)">删除</q-btn>
            </div>
          </td>
        </tr>
      </tbody>
    </q-markup-table>

    <q-dialog v-model="editDialogState" no-shake transition-show="fade" transition-hide="fade">
      <q-card>
        <q-card-section>
          <div v-if="editMode == 'edit'" class="text-h6 select-none">编辑抓取器</div>
          <div v-else class="text-h6 select-none">添加抓取器条目</div>
        </q-card-section>

        <q-card-section class="dialog q-pt-none flex flex-col gap-2">
          <div v-if="editMode == 'edit'">在这里编辑抓取器配置信息</div>
          <div v-else>在这里添加抓取器配置信息</div>
          <div class="flex flex-col gap-4">
            <q-input v-model="crawlerFormRef.name" label="抓取器名称"></q-input>
            <q-input
              v-model="crawlerFormRef.reqUrl"
              label="请求地址"
              class="flex-grow"
            ></q-input>
            <q-input
              outlined
              v-model="crawlerFormRef.params"
              type="textarea"
              placeholder="额外参数，一行一项，以 'Key=Value' 格式填写，如 id=102"
            ></q-input>
          </div>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="取消" color="primary" v-close-popup />
          <q-btn
            flat
            label="OK"
            color="primary"
            @click="editMode == 'edit' ? updateCrawlerAction() : addCrawlerAction()"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<style scoped lang="sass">
.dialog
  min-width: 35rem
</style>
