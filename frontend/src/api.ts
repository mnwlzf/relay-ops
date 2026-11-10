import axios from 'axios'

/**
 * 后端统一返回 ApiResponse{ data, requestId }；这里只取 data，错误统一抛 Error(message)。
 */
const http = axios.create({ baseURL: '/api/v1', timeout: 20000 })

http.interceptors.response.use(
  (response) => response.data?.data ?? response.data,
  (error) => {
    const message = error.response?.data?.message ?? error.response?.data?.error ?? error.message
    return Promise.reject(new Error(message ?? '请求失败'))
  }
)

export const api = {
  modelStatus: () => http.get('/assistant/models'),
  lastMessage: () => http.get('/assistant/messages/last'),

  tasks: () => http.get('/scheduling/tasks'),
  taskTypes: () => http.get('/scheduling/types'),
  toggleTask: (id: number, enabled: boolean) => http.post(`/scheduling/tasks/${id}/toggle?enabled=${enabled}`),
  runTask: (id: number) => http.post(`/scheduling/tasks/${id}/run`),
  taskRuns: (id: number, limit = 10) => http.get(`/scheduling/tasks/${id}/runs`, { params: { limit } }),
  previewSpec: (spec: unknown, count = 10) => http.post('/scheduling/spec/preview', { spec, count }),

  personas: () => http.get('/assistant/personas'),
  promptPreview: (body: unknown) => http.post('/assistant/prompt/preview', body),
  chat: (body: unknown) => http.post('/assistant/chat', body),

  blueprints: () => http.get('/assistant/blueprints'),
  blueprintPreview: (id: number, body: unknown) => http.post(`/assistant/blueprints/${id}/preview`, body),

  platforms: () => http.get('/upstream/platforms'),
  upstreamAccounts: () => http.get('/upstream/accounts'),
  createUpstreamAccount: (body: unknown) => http.post('/upstream/accounts', body),
  collectNow: () => http.post('/upstream/collect'),
  snapshots: (id: number) => http.get('/upstream/accounts/' + id + '/snapshots'),
  collectRuns: () => http.get('/upstream/runs'),
  upstreamDashboard: () => http.get('/upstream/dashboard'),

  monitoringOverview: () => http.get('/monitoring/overview'),
  monitoringAbnormal: () => http.get('/monitoring/abnormal'),
  monitoringTrend: (id: number, hours = 24) =>
    http.get('/monitoring/accounts/' + id + '/trend', { params: { hours } }),
  memoryFacts: (params: unknown) => http.get('/assistant/memory/facts', { params }),
  recall: (body: unknown) => http.post('/assistant/memory/recall', body),
  remember: (body: unknown) => http.post('/assistant/memory/facts', body)
}

export default http
