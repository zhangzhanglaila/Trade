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

const datasourceView = ref([])

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

const customDatasource = defineModel('datasource')

async function init() {
  refreshDatasourceView(customDatasource.value)
}

onMounted(() => {
  if (customDatasource.value) {
    init()
  }
})

watch(customDatasource, async (newValue, oldValue) => {
  refreshDatasourceView(newValue)
})

function refreshDatasourceView(newValue) {
  datasourceView.value = []
  for (let i = 0; i < newValue.length; i++) {
    const datasource = newValue[i]
    const params = []
    try {
      const p = JSON.parse(datasource.params)
      for (const k in p) {
        params.push(`${k}=${p[k]}`)
      }
    } catch (e) {
      console.log(e)
    }

    datasourceView.value.push({
      idx: i,
      id: String(datasource.id),
      name: datasource.name,
      reqUrl: datasource.reqUrl,
      crawlMethod: datasource.crawlMethod,
      params: params.join('<br>'),
      createTime: dayjs(datasource.createTime).format('YYYY-MM-DD HH:mm:ss'),
      updateTime: dayjs(datasource.updateTime).format('YYYY-MM-DD HH:mm:ss'),
      status: datasource.status,
    })
  }
}

defineExpose({
  refreshDatasourceView,
})

const datasourceFormRef = reactive({
  id: '',
  name: '',
  reqUrl: '',
  crawlMethod: 0,
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
    datasourceFormRef.name = ''
    datasourceFormRef.reqUrl = ''
    datasourceFormRef.crawlMethod = 0
    datasourceFormRef.params = ''
    datasourceFormRef.status = 0
  } else {
    datasourceFormRef.id = String(form.id)
    datasourceFormRef.name = form.name
    datasourceFormRef.reqUrl = form.reqUrl
    datasourceFormRef.crawlMethod = form.crawlMethod
    datasourceFormRef.params = form.params.replace('<br>', '\n')
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
  if (!datasourceFormRef.name || !datasourceFormRef.reqUrl) {
    msgDialog('参数错误', '数据源的名称和请求地址都不能为空')
    return false
  }

  try {
    new URL(datasourceFormRef.reqUrl)
  } catch (e) {
    msgDialog('参数错误', `URL不正确： ${datasourceFormRef.reqUrl}`)
    return false
  }

  return true
}

async function addDatasourceAction() {
  if (!checkForm()) {
    return false
  }

  const dialog = loadingDialog()
  try {
    await addDatasource(datasourceFormRef)
    dialog.hide()
    editDialogState.value = false
    func('onUpdate')
  } catch (e) {
    errorMsg(dialog, e)
  }

  return dialog
}

const datasourceUrl = apiUrls.agented.collector.configure.datasource.custom

function buildReq(form) {
  const req = {
    name: form.name,
    reqUrl: form.reqUrl,
    crawlMethod: form.crawlMethod,
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

async function addDatasource(form) {
  const req = buildReq(form)
  const response = await agentedClient.post(datasourceUrl.add, req, {
    headers: getHeader(),
  })

  return handleResponse(response)
}

function deleteDatasourceAction(datasource) {
  ensureDialog('确认', `确认删除 '${datasource.name}'？`).onOk(async () => {
    const dialog = loadingDialog()
    try {
      await deleteDatasource(datasource.id)
      dialog.hide()
      func('onUpdate')
    } catch (e) {
      errorMsg(dialog, e)
    }
  })
}

async function deleteDatasource(id) {
  const response = await agentedClient.post(datasourceUrl.delete, undefined, {
    headers: getHeader(),
    params: { id: id },
  })

  return handleResponse(response)
}

async function updateDatasourceAction() {
  if (!checkForm()) {
    return
  }

  const dialog = loadingDialog()
  try {
    await updateDatasource(datasourceFormRef)
    dialog.hide()
    editDialogState.value = false
    func('onUpdate')
  } catch (e) {
    errorMsg(dialog, e)
  }

  return dialog
}

async function updateDatasource(form) {
  const req = buildReq(form)

  const response = await agentedClient.post(datasourceUrl.update, req, {
    params: { id: form.id },
    headers: getHeader(),
  })

  return handleResponse(response)
}

async function switchDatasourceStatusAction(datasource, status) {
  const id = datasource.id
  switch (status) {
    case 'enable': {
      try {
        await enableDatasource(id)
        func('onUpdate')
      } catch (e) {
        errorMsg2(e)
      }
      break
    }
    case 'disable': {
      try {
        await disableDatasource(id)
        func('onUpdate')
      } catch (e) {
        errorMsg2(e)
      }
      break
    }
  }
}

async function disableDatasource(id) {
  const response = await agentedClient.post(datasourceUrl.setStatus, undefined, {
    headers: getHeader(),
    params: { id: id, status: status.Disable },
  })

  return handleResponse(response)
}

async function enableDatasource(id) {
  const response = await agentedClient.post(datasourceUrl.setStatus, undefined, {
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
          <th class="text-left">数据抓取方式</th>
          <th class="text-left">参数</th>
          <th class="text-left">创建时间</th>
          <th class="text-left">更新时间</th>
          <th class="text-left">状态</th>
          <th class="text-left">操作</th>
        </tr>
      </thead>
      <tbody class="text-base">
        <tr v-for="datasource in datasourceView" :key="datasource">
          <td class="text-left">{{ datasource.idx }}</td>
          <td class="text-left">{{ datasource.id }}</td>
          <td class="text-left">{{ datasource.name }}</td>
          <td class="text-left">{{ datasource.reqUrl }}</td>
          <td class="text-left">{{ crawlTypes[datasource.crawlMethod] }}</td>
          <td class="text-left" v-html="datasource.params"></td>
          <td class="text-left">{{ datasource.createTime }}</td>
          <td class="text-left">{{ datasource.updateTime }}</td>
          <td class="text-left">{{ statusEnum[datasource.status] }}</td>
          <td class="text-left">
            <div class="flex flex-row gap-2 justify-start items-center">
              <q-btn
                v-if="datasource.status != 0"
                outline
                color="green"
                @click="switchDatasourceStatusAction(datasource, 'enable')"
                >启用
              </q-btn>
              <q-btn
                v-else
                outline
                color="gray"
                @click="switchDatasourceStatusAction(datasource, 'disable')"
                >禁用
              </q-btn>
              <q-btn outline color="primary" @click="openEditDialog('edit', datasource)"
                >编辑
              </q-btn>
              <q-btn outline color="red" @click="deleteDatasourceAction(datasource)">删除</q-btn>
            </div>
          </td>
        </tr>
      </tbody>
    </q-markup-table>

    <q-dialog v-model="editDialogState" no-shake transition-show="fade" transition-hide="fade">
      <q-card>
        <q-card-section>
          <div v-if="editMode == 'edit'" class="text-h6 select-none">编辑数据源</div>
          <div v-else class="text-h6 select-none">添加数据源条目</div>
        </q-card-section>

        <q-card-section class="dialog q-pt-none flex flex-col gap-2">
          <div v-if="editMode == 'edit'">在这里编辑数据源配置信息</div>
          <div v-else>在这里添加数据源配置信息</div>
          <div class="flex flex-col gap-4">
            <q-input v-model="datasourceFormRef.name" label="数据源名称"></q-input>
            <q-input
              v-model="datasourceFormRef.reqUrl"
              label="请求地址"
              class="flex-grow"
            ></q-input>
            <q-select
              outlined
              v-model="datasourceFormRef.crawlMethod"
              :options="crawlTypesOptions"
              map-options
              emit-value
              label="获取到的数据抓取方式"
            ></q-select>
            <q-input
              outlined
              v-model="datasourceFormRef.params"
              type="textarea"
              :placeholder="'额外参数，一行一项，以 \'Key=Value\' 格式填写，\n如使用自定义爬取方式时，指定 crawler_id=102'"
            ></q-input>
          </div>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="取消" color="primary" v-close-popup />
          <q-btn
            flat
            label="OK"
            color="primary"
            @click="editMode == 'edit' ? updateDatasourceAction() : addDatasourceAction()"
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
