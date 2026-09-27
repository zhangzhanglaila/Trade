import request from '@/utils/request'

export function getAjReportConfig() {
  return request.get('/aj-report/config')
}
