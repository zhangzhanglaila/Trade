<script setup>
import { reactive, ref, watch } from 'vue'
import { agentedClient, handleResponse } from 'src/api/client.js'
import { apiUrls } from 'src/api/api-urls.js'
import { useInstanceStore } from 'stores/instance.js'
import CustomCrawlersPage from 'pages/collector/crawler/CustomCrawlerPage.vue'
import EasyspiderCrawlersPage from 'pages/collector/crawler/EasyspiderCrawlerPage.vue'
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

let crawlerRef = reactive({
  custom: {},
  easpider: {},
})

const customCrawlerPageRef = ref()
const easyspiderCrawlerPageRef = ref()

async function init() {
  const response = await agentedClient.get(apiUrls.agented.collector.crawler.list, {
    headers: getHeader(),
  })
  const data = handleResponse(response)

  crawlerRef = data

  if (customCrawlerPageRef.value) {
    customCrawlerPageRef.value.refreshCrawlersView(data.custom)
  }

  if (easyspiderCrawlerPageRef.value) {
    easyspiderCrawlerPageRef.value.refreshCrawlersView(data.easyspider)
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
      <h1 class="text-4xl font-medium">抓取器配置</h1>
      <q-tabs
        v-model="tab"
        dense
        class="text-grey"
        active-color="primary"
        indicator-color="primary"
        align="justify"
        narrow-indicator
      >
        <q-tab name="custom" label="自定义抓取器" />
        <q-tab name="easyspider" label="Easyspider" />
      </q-tabs>

      <q-separator></q-separator>

      <q-tab-panels v-model="tab" animated transition-prev="jump-left" transition-next="jump-left">
        <q-tab-panel name="custom">
          <div class="flex flex-col">
            <CustomCrawlersPage
              ref="customCrawlerPageRef"
              v-model:crawlers="crawlerRef.custom"
              @onUpdate="init()"
            ></CustomCrawlersPage>
          </div>
        </q-tab-panel>

        <q-tab-panel name="easyspider">
          <div class="flex flex-col">
            <EasyspiderCrawlersPage
              ref="easyspiderCrawlerPageRef"
              v-model:crawlers="crawlerRef.easyspider"
              @onUpdate="init()"
            ></EasyspiderCrawlersPage>
          </div>
        </q-tab-panel>

      </q-tab-panels>
    </div>
  </div>
</template>

<style scoped></style>
