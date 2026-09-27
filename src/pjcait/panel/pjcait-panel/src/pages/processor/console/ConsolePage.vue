<script setup>
import { reactive, ref, watch } from 'vue'
import { apiUrls } from 'src/api/api-urls.js'
import { agentedBaseUrl } from 'src/api/client.js'
import { useInstanceStore } from 'stores/instance'
import dayjs from 'dayjs'
import { CronExpressionParser } from 'cron-parser'
import { useQuasar } from 'quasar'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { marked } from 'marked'

import VChart from 'vue-echarts'

import 'echarts'
import { jsonrepair } from 'jsonrepair'

const serviceName = 'cait-processor'

const quasar = useQuasar()

const instanceStore = useInstanceStore()

function getHeader() {
  return {
    'x-target-instance': instanceStore.selectedInstance[serviceName],
  }
}

async function init() {}

if (instanceStore.selectedInstance[serviceName]) {
  init()
}

watch(instanceStore.selectedInstance, async (newValue, oldValue) => {
  console.log('watch', newValue)
  if (newValue[serviceName]) {
    await init()
  }
})

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

const inputContent = ref()

const thinkingRef = reactive({
  enable: false,
  budget: 4000,
})

const newsLines = ref([])

const resultsExpand = ref([])
const thinkDisplayExpand = ref([])

const resultContents = reactive([])
const thinkingContents = reactive([])

const useThink = ref(false)

const storage = ref(false)

const processStatus = ref([true, true])

const visionStatus = ref(false)

async function processAction() {
  if (!inputContent.value) {
    errorMsg2('内容不能为空')
    return
  }

  useThink.value = thinkingRef.enable

  const content = inputContent.value
  const thinking = {
    enable: thinkingRef.enable,
    budget: Number(thinkingRef.budget),
  }

  try {
    newsLines.value = []
    const lines = content.split('\n')
    for (let line of lines) {
      newsLines.value.push(line.trim())
    }

    await requestProcess(content, thinking)
  } catch (e) {
    console.log('SSE请求错误', e)
    errorMsg2(`sse请求错误：${String(e)}`)
  }
}

const resultText = ref('')

async function requestProcess(content, thinking) {
  resultsExpand.value = []
  thinkDisplayExpand.value = []
  resultContents.splice(0, resultContents.length)
  thinkingContents.splice(0, thinkingContents.length)
  processStatus.value = []
  resultText.value = ''

  const req = {
    content: content,
    storage: storage.value,
    thinking: thinking,
  }

  // const url = `${agentedBaseUrl}`
  // new UR

  const url = agentedBaseUrl + apiUrls.agented.processor.process.run
  await fetchEventSource(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Target-Instance': instanceStore.selectedInstance[serviceName],
    },
    body: JSON.stringify(req),
    openWhenHidden: true,
    onmessage: (event) => {
      switch (event.event) {
        case 'data': {
          try {
            const resp = JSON.parse(event.data)
            const part = resp.part
            const result = resp.result
            resultText.value = `处理中：${part}/${newsLines.value.length}`

            if (!processStatus.value[part]) {
              processStatus.value[part] = true
            }

            const content = result?.choices[0]?.delta.content
            if (!resultContents[part]) {
              resultContents[part] = content ? content : ''
            } else {
              resultContents[part] += content ? content : ''
            }

            const thinking = result?.choices[0]?.delta.reasoning_content
            if (!thinkingContents[part]) {
              thinkingContents[part] = thinking ? thinking.replaceAll('\n', '<br>') : ''
            } else {
              thinkingContents[part] += thinking ? thinking.replaceAll('\n', '<br>') : ''
            }
          } catch (e) {
            console.error(e)
            resultText.value = `处理错误：${String(e)}`
          }
          break
        }
        case 'done':
          break
        case 'err': {
          try {
            const resp = JSON.parse(event.data)
            const part = resp.part
            if (part === -1) {
              resultContents[part] = resp.err
            }
          } catch (e) {
            console.error(e)
            resultText.value = `处理错误：${String(e)}`
          }
          break
        }
      }
    },
    onclose: () => {
      resultText.value = '完成'
    },
    onerror: (err) => {
      console.error('SSE接收错误:', err)
      resultText.value = `接收SSE数据时出错：${String(err)}`
      throw err
    },
  })
}

const option = ref({
  title: {
    text: '抽取结果',
    top: 'top',
    left: 'center',
  },
  tooltip: {},
  series: [
    {
      type: 'graph',
      layout: 'force', // 可选：'none', 'circular', 'force'
      roam: true, // 支持鼠标缩放和平移
      label: {
        show: true,
        position: 'inside',
      },
      force: {
        repulsion: 1000,
        edgeLength: [50, 200],
      },
      draggable: true,
      autoCurveness: true,
      data: [],
      links: [],
      lineStyle: {
        color: 'source',
      },
      edgeLabel: {
        show: true,
        fontSize: 12,
        formatter: function (params) {
          return params.data.label || ''
        },
      },
      edgeSymbol: ['', 'arrow'],
    },
  ],
})

function convert(text) {
  const data = JSON.parse(jsonrepair(text))
  const nodes = data.nodes || []
  const edges = data.edges || []

  const ecNodes = []
  const ecLinks = []
  nodes.forEach((node) => {
    if (!ecNodes.find(node1 => node1.name === node.label)) {
      ecNodes.push({ name: node.label, symbolSize: 60 })
    }
  })
  edges.forEach((edge) => {
    if (!ecNodes.find((node) => node.name === edge.source)) {
      ecNodes.push({ name: edge.source, symbolSize: 60 })
    }

    if (!ecNodes.find((node) => node.name === edge.target)) {
      ecNodes.push({ name: edge.target, symbolSize: 60 })
    }

    ecLinks.push({ source: edge.source, target: edge.target, label: edge.label })
  })

  return {
    nodes: ecNodes,
    links: ecLinks,
  }
}

function openVisionAction(text) {
  if (!text) {
    return
  }

  let data = {}
  try {
    data = convert(text)
    console.log(data)
  } catch (e) {
    errorMsg2(`结果数据转换错误，可能是输入的文本不符合 JSON 格式。${String(e)}`)
    return
  }

  option.value.series[0].data = data.nodes
  option.value.series[0].links = data.links

  visionStatus.value = true
}

function openVisionActionAll() {
  const nodes = []
  const links = []
  try {
    for (const result of resultContents) {
      const converted = convert(result)
      converted.nodes.forEach((node) => {
        if (!nodes.find((node1) => node1.name === node.name)) {
          nodes.push({ name: node.name, symbolSize: 60 })
        }
      })

      converted.links.forEach((link) => {
        if (
          !links.find(
            (link1) =>
              link1.source === link.source &&
              link1.target === link.target &&
              link1.label === link.label,
          )
        ) {
          links.push({ source: link.source, target: link.target, label: link.label })
        }
      })
    }
  } catch (e) {
    errorMsg2(`结果数据转换错误，可能是输入的文本不符合 JSON 格式。${String(e)}`)
    return
  }

  console.log('nodes', nodes)
  console.log('links', links)

  option.value.series[0].data = nodes
  option.value.series[0].links = links

  visionStatus.value = true
}
</script>

<template>
  <div class="p-3 w-full text-base">
    <div class="flex flex-col gap-6">
      <h1 class="text-4xl font-medium">控制台</h1>
      <div class="flex flex-col gap-2 w-1/2 min-w-96">
        <div class="text-2xl">新闻数据直接输入</div>
        <div class="flex flex-col gap-3">
          <div>在这里直接输入新闻内容，可立即处理数据，并显示结果</div>
          <div class="flex flex-col gap-4">
            <div>
              <q-toggle v-model="storage" label="存储结果" left-label />
              <q-toggle v-model="thinkingRef.enable" label="开启推理" left-label />
              <q-input
                v-if="thinkingRef.enable"
                v-model="thinkingRef.budget"
                label="推理最大Token数"
                type="number"
                outlined
              />
            </div>
            <div>
              <div>新闻内容</div>
              <q-input
                outlined
                v-model="inputContent"
                type="textarea"
                placeholder="新闻内容"
              ></q-input>
            </div>
          </div>
        </div>
        <q-btn unelevated color="primary" class="w-48" icon="post_add" @click="processAction()"
          >发送
        </q-btn>
      </div>

      <div v-if="newsLines && newsLines.length" class="flex flex-col gap-1">
        <div class="text-2xl">处理结果</div>
        <div class="text-base" v-if="resultText !== undefined && resultText !== ''">
          <div>{{ resultText }}</div>
          <q-btn
            v-if="resultText === '完成'"
            class="w-32"
            color="primary"
            unelevated
            @click="openVisionActionAll()"
            >可视化
          </q-btn>
        </div>
        <q-list class="rounded-borders bg-gray-100">
          <q-expansion-item
            v-for="(line, i) in newsLines"
            :key="line"
            v-model="resultsExpand[i]"
            expand-separator
            hide-expand-icon
            dense
          >
            <template v-slot:header>
              <div class="flex items-center w-full">
                <p class="">{{ line }}</p>
              </div>
            </template>

            <template v-slot:default>
              <div v-if="processStatus[i]">
                <div class="p-2 text-sm flex flex-col gap-1">
                  <q-expansion-item
                    v-if="useThink"
                    v-model="thinkDisplayExpand[i]"
                    :expand-separator="processStatus[i]"
                    hide-expand-icon
                    dense
                    header-class="rounded"
                  >
                    <template v-slot:header>
                      <div class="flex items-center">
                        <div>推理...</div>
                      </div>
                    </template>

                    <template v-slot:default>
                      <div
                        class="text-gray-500 bg-blue-50 rounded py-2 px-4 mt-1"
                        v-html="thinkingContents[i]"
                      ></div>
                    </template>
                  </q-expansion-item>
                  <div class="flex flex-col gap-2 px-4">
                    <code
                      class=""
                      v-html="marked.parse(resultContents[i] ? resultContents[i] : '')"
                    ></code>
                    <q-btn
                      class="w-32"
                      color="primary"
                      unelevated
                      @click="openVisionAction(resultContents[i])"
                      >可视化
                    </q-btn>
                  </div>
                </div>
              </div>
            </template>
          </q-expansion-item>
        </q-list>
      </div>
    </div>

    <q-dialog v-model="visionStatus" no-shake transition-show="fade" transition-hide="fade">
      <q-card class="dialog pt-2">
        <q-card-section class="q-pt-none flex flex-col gap-2"></q-card-section>
        <q-card-section class="q-pt-none flex flex-col gap-2">
          <div class="flex flex-col gap-4">
            <v-chart autoresize class="chart" :option="option" />
          </div>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="OK" color="primary" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<style scoped lang="sass">
.newsItem
  transition: all 0.17s ease

.dialog
  min-width: 85vw
  min-height: 80vh

.chart
  min-width: 80vw
  min-height: 80vh
</style>
