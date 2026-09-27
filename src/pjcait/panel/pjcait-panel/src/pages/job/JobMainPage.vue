<script setup lang="ts">
import { ref, watch } from 'vue'

const options = ref([])

const serviceName = 'cait-collector'

import {useInstanceStore} from 'stores/instance'

const instanceStore = useInstanceStore()

async function refreshInstanceList() {
  const resp = await instanceStore.getInstanceList(serviceName)
  console.log('instanceStore.getInstanceList resp', resp)

  const collectorServiceInstances = resp[serviceName]
  if (!collectorServiceInstances) {
    return
  }

  options.value = []
  for (const collectorServiceInstance of collectorServiceInstances) {
    options.value.push(collectorServiceInstance.ID)
  }

  instanceStore.selectedInstance[serviceName] = options.value[0] ? options.value[0] : ''
}

refreshInstanceList()

function getHeader() {
  return {
    'x-target-instance': instanceStore.selectedInstance[serviceName],
  }
}

import { agentedClient, handleResponse } from 'src/api/client.js'
import { apiUrls } from 'src/api/api-urls.js'
import { useQuasar } from 'quasar'

const quasar = useQuasar()

function msgDialog(title, msg) {
  return quasar.dialog({
    transitionShow: 'fade',
    transitionHide: 'fade',
    title: title,
    message: msg,
    ok: true,
  })
}

watch(instanceStore.selectedInstance, async (newValue) => {
  try {
    await agentedClient.get(apiUrls.agented.collector.health.check, {
      headers: getHeader(),
    })
  } catch (e) {
    msgDialog('错误', `节点不可用：${instanceStore.selectedInstance[serviceName]}`)
  }
})

</script>

<template>
<div class="flex flex-nowrap justify-start w-full overflow-hidden">
  <div class="sticky top-0">
    <q-tabs indicator-color="primary" align="left" vertical class="w-52 bg-gray-100 ">
      <q-route-tab to="/job" label="控制台" />
      <q-route-tab to="/job/datasource" label="数据源配置" />
      <q-route-tab to="/job/data" label="任务记录" />

      <q-separator class="m-2"/>

      <div class="p-2">
        <q-select square filled dense v-model="instanceStore.selectedInstance[serviceName]" :options="options" label="节点选择" />
      </div>
    </q-tabs>
  </div>
  <div class="p-1 flex flex-grow">
    <router-view v-slot="{ Component }">
      <transition name="slide-fade-blur" mode="out-in">
        <component :is="Component"></component>
      </transition>
    </router-view>
  </div>
</div>
</template>

<style scoped lang="sass">

</style>
