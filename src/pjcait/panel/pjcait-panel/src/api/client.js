import axios from 'axios'

import config from "../../config";

console.log(config);
const baseUrl = config.backendBaseUrl ? config.backendBaseUrl : `${window.location.origin}/api`;
const agentedBaseUrl = config.agentedBaseUrl ? config.agentedBaseUrl : `${window.location.origin}/api/agent/api`;

const client = axios.create({
  // headers: {},
  baseURL: baseUrl,
  timeout: 8000,
  withCredentials: true
})

const agentedClient = axios.create({
  // headers: {},
  baseURL: agentedBaseUrl,
  timeout: 8000,
  withCredentials: true
})
/**
 * @typedef { {
 *   code: number,
 *   msg: string,
 *   data: any
 * }} Response
 */


/**
 *
 * @param response AxiosResponse
 * @returns {*}
 * @throws {{code: number, message: string}}
 */
function handleResponse(response) {
  /**
   * @type Response
   */
  const json = response.data
  if (json.code !== 0) {
    throw {
      code: json.code, message: json.msg ? json.msg : JSON.stringify(json)
    }
  }

  return json.data
}

export {
  client, agentedClient, handleResponse, baseUrl, agentedBaseUrl
}
