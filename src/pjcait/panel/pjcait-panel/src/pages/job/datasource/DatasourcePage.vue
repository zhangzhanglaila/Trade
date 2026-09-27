<script setup>
import { reactive, ref, watch } from 'vue'
import { agentedClient, handleResponse } from 'src/api/client.js'
import { apiUrls } from 'src/api/api-urls.js'
import { useInstanceStore } from 'stores/instance.js'
import CustomDatasourcePage from 'pages/job/datasource/CustomDatasourcePage.vue'
import EasyspiderDatasourcePage from 'pages/job/datasource/EasyspiderDatasourcePage.vue'
import { useQuasar } from 'quasar'

const gdeltEnable = ref(false)

const tab = ref('custom')

const serviceName = 'cait-collector'
const instanceStore = useInstanceStore()

function getHeader() {
  return {
    'x-target-instance': instanceStore.selectedInstance[serviceName],
  }
}

let datasourceRef = reactive({
  datasource: {},
  easpider: {},
  gdelt: { enabled: false },
})

const customDatasourcePageRef = ref()
const easyspiderDatasourcePageRef = ref()

async function init() {
  const response = await agentedClient.get(apiUrls.agented.collector.datasource.list, {
    headers: getHeader(),
  })
  const data = handleResponse(response)
  console.log('data', data)
  datasourceRef = data
  gdeltEnable.value = data.gdelt.enabled

  if (customDatasourcePageRef.value) {
    customDatasourcePageRef.value.refreshDatasourceView(data.custom)
  }

  if (easyspiderDatasourcePageRef.value) {
    easyspiderDatasourcePageRef.value.refreshDatasourceView(data.easyspider)
  }
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

const quasar = useQuasar()

function errorMsg2(e) {
  console.error(e)
  quasar.dialog({
    title: '啊哈？',
    message: e.message ? e.message : String(e),
    ok: true,
  })
}

async function switchGdeltStatus(enable) {
  try {
    if (enable) {
      await agentedClient.post(apiUrls.agented.collector.configure.datasource.gdelt.setStatus, undefined, {
        headers: getHeader(),
        params: {status: 1}
      })
    } else {
      await agentedClient.post(apiUrls.agented.collector.configure.datasource.gdelt.setStatus, undefined, {
        headers: getHeader(),
        params: {status: 0}
      })
    }
  } catch (e) {
    errorMsg2(e)
  }

  await init()
}

</script>

<template>
  <div class="p-3 w-full text-base">
    <div class="flex flex-col">
      <h1 class="text-4xl font-medium">数据源配置</h1>
      <q-tabs
        v-model="tab"
        dense
        class="text-grey"
        active-color="primary"
        indicator-color="primary"
        align="justify"
        narrow-indicator
      >
        <q-tab name="custom" label="自定义数据源" />
        <q-tab name="easyspider" label="Easyspider" />
        <q-tab name="gdelt" label="GDELT" />
      </q-tabs>

      <q-separator></q-separator>

      <q-tab-panels v-model="tab" animated transition-prev="jump-left" transition-next="jump-left">
        <q-tab-panel name="custom">
          <div class="flex flex-col">
            <CustomDatasourcePage
              ref="customDatasourcePageRef"
              v-model:datasource="datasourceRef.custom"
              @onUpdate="init()"
            ></CustomDatasourcePage>
          </div>
        </q-tab-panel>

        <q-tab-panel name="easyspider">
          <div class="flex flex-col">
            <EasyspiderDatasourcePage
              ref="easyspiderDatasourcePageRef"
              v-model:datasource="datasourceRef.easyspider"
              @onUpdate="init()"
            ></EasyspiderDatasourcePage>
          </div>
        </q-tab-panel>

        <q-tab-panel name="gdelt">
          <div class="flex flex-row items-center">
            <q-toggle v-model="gdeltEnable" left-label @update:modelValue="v => switchGdeltStatus(v)">启用</q-toggle>
          </div>
        </q-tab-panel>
      </q-tab-panels>
    </div>
  </div>
</template>

<style scoped></style>
