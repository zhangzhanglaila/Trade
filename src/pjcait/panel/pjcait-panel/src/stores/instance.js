import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import { client, handleResponse } from 'src/api/client.js'
import { apiUrls } from 'src/api/api-urls.js'

/**
 * @typedef { import("vue").Ref } Ref
 * @typedef { import("vue").UnwrapRef } UnwrapRef
 */

/**
 *
 * @typedef {{name: string, status: string}} Instance
 */

const a = {
  "AggregatedStatus": "critical",
  "Service": {
    "ID": "es-wrapper-02",
    "Service": "easy-spider-wrapper",
    "Tags": [],
    "Meta": {},
    "Port": 6640,
    "Address": "127.0.0.1",
    "TaggedAddresses": {
      "lan_ipv4": {
        "Address": "127.0.0.1",
        "Port": 6640
      },
      "wan_ipv4": {
        "Address": "127.0.0.1",
        "Port": 6640
      }
    },
    "Weights": {
      "Passing": 1,
      "Warning": 1
    },
    "EnableTagOverride": false,
    "Datacenter": "lensfrex"
  },
  "Checks": [
    {
      "Node": "lensfrex-pc",
      "CheckID": "service:es-wrapper-02",
      "Name": "Service 'easy-spider-wrapper' check",
      "Status": "critical",
      "Notes": "",
      "Output": "Get \"http://127.0.0.1:6640/health\": dial tcp 127.0.0.1:6640: connectex: No connection could be made because the target machine actively refused it.",
      "ServiceID": "es-wrapper-02",
      "ServiceName": "easy-spider-wrapper",
      "ServiceTags": null,
      "Type": "",
      "ExposedPort": 0,
      "Definition": {
        "Interval": "0s",
        "Timeout": "0s",
        "DeregisterCriticalServiceAfter": "0s",
        "HTTP": "",
        "Header": null,
        "Method": "",
        "Body": "",
        "TLSServerName": "",
        "TLSSkipVerify": false,
        "TCP": "",
        "TCPUseTLS": false,
        "UDP": "",
        "GRPC": "",
        "OSService": "",
        "GRPCUseTLS": false
      },
      "CreateIndex": 0,
      "ModifyIndex": 0
    }
  ]
}

export const useInstanceStore = defineStore('instance', () =>{

  const instanceMap = {}

  const currentService = ref('')

  const selectedInstance = reactive({})

  /**
   *
   * @type {Ref<UnwrapRef<Instance[]>, UnwrapRef<Instance[]> | Instance[]>}
   */
  const instanceList = ref({})

  async function getInstanceList(serviceName) {
    const response = await client.get(apiUrls.backend.listInstances, {
      params: {
        serviceName: serviceName,
      },
    })

    const resp = handleResponse(response)

    instanceList.value = resp

    return resp.instances ? resp.instances : []
  }

  return { selectedInstance, currentService, instanceList, getInstanceList }
})
