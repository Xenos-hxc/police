import request from '@/utils/request'
import type {
  AdminOverview,
  ApiResponse,
  ArchiveHistoryDetail,
  ArchiveHistoryItem,
  ArchiveRecord,
  Attachment,
  CheckRecord,
  CheckTask,
  DashboardCharts,
  DashboardSummary,
  Department,
  DepartmentNode,
  DictionaryData,
  DictionaryType,
  HiddenDanger,
  HiddenDangerDetail,
  LoginLog,
  Menu,
  OperationLog,
  PageResult,
  Reminder,
  Role,
  RoleDetail,
  SelectionPool,
  SelectionStatus,
  StationInspectionDetail,
  StationProgress,
  StatisticsCharts,
  StatisticsOverview,
  StatisticsRanking,
  SystemConfig,
  SystemPeriodInfo,
  TaskFullDetail,
  TaskListSummary,
  UserAccount,
  UserAccountForm,
  UserInfo
} from '@/types'

export const authApi = {
  login: (data: { username: string; password: string; captcha?: string; captchaId?: string }) =>
    request.post<unknown, ApiResponse<{ accessToken: string; tokenType: string; expiresAt: string }>>(
      '/auth/login',
      data
    ),
  captcha: () =>
    request.get<unknown, ApiResponse<{ enabled: boolean; captchaId?: string; image?: string }>>('/auth/captcha'),
  me: () => request.get<unknown, ApiResponse<UserInfo>>('/auth/me'),
  changePassword: (data: { oldPassword: string; newPassword: string }) => request.post('/auth/change-password', data),
  logout: () => request.post('/auth/logout')
}

export const dashboardApi = {
  summary: () => request.get<unknown, ApiResponse<DashboardSummary>>('/dashboard/summary'),
  charts: () => request.get<unknown, ApiResponse<DashboardCharts>>('/dashboard/charts'),
  todo: (params: Record<string, unknown> = {}) =>
    request.get<unknown, ApiResponse<PageResult<CheckTask>>>('/dashboard/todo', { params }),
  warnings: (params: Record<string, unknown> = {}) =>
    request.get<unknown, ApiResponse<PageResult<CheckTask>>>('/dashboard/warnings', { params })
}

export const taskApi = {
  list: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<CheckTask>>>('/check-tasks', { params }),
  summary: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<TaskListSummary>>('/check-tasks/summary', { params }),
  overdueReminders: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<CheckTask>>>('/check-tasks/overdue-reminders', { params }),
  detail: (id: number) => request.get<unknown, ApiResponse<CheckTask>>(`/check-tasks/${id}`),
  fullDetail: (id: number) => request.get<unknown, ApiResponse<TaskFullDetail>>(`/check-tasks/${id}/full`),
  update: (id: number, data: Record<string, unknown>) => request.put(`/check-tasks/${id}`, data),
  submit: (id: number) => request.post(`/check-tasks/${id}/submit`),
  selectionStatus: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<SelectionStatus[]>>('/check-tasks/selection-status', { params }),
  initializePeriod: (data: { year: number; quarter: number }) =>
    request.post<unknown, ApiResponse<SelectionStatus[]>>('/check-tasks/initialize-period', data),
  selectionPool: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<SelectionPool>>('/check-tasks/selection-pool', { params }),
  selectionSave: (data: Record<string, unknown>) =>
    request.post<unknown, ApiResponse<SelectionPool>>('/check-tasks/selection-save', data),
  selectionClear: (data: Record<string, unknown>) =>
    request.post<unknown, ApiResponse<SelectionPool>>('/check-tasks/selection-clear', data),
  export: (params: Record<string, unknown>) =>
    request.get<unknown, Blob>('/check-tasks/export', { params, responseType: 'blob' })
}

export const recordApi = {
  get: (taskId: number) => request.get<unknown, ApiResponse<CheckRecord | null>>(`/check-records/${taskId}`),
  create: (data: Record<string, unknown>) => request.post<unknown, ApiResponse<CheckRecord>>('/check-records', data),
  update: (id: number, data: Record<string, unknown>) => request.put(`/check-records/${id}`, data),
  submit: (id: number) => request.post(`/check-records/${id}/submit`)
}

export const archiveApi = {
  list: (type: string, params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<ArchiveRecord>>>(`/${type}`, { params }),
  create: (type: string, data: Record<string, unknown>) => request.post(`/${type}`, data),
  update: (type: string, id: number, data: Record<string, unknown>) => request.put(`/${type}/${id}`, data),
  detail: (type: string, id: number) => request.get<unknown, ApiResponse<ArchiveRecord>>(`/${type}/${id}`),
  history: (type: string, id: number, params: Record<string, unknown> = {}) =>
    request.get<unknown, ApiResponse<ArchiveHistoryItem[]>>(`/${type}/${id}/history`, { params }),
  historyDetail: (type: string, id: number, params: Record<string, unknown> = {}) =>
    request.get<unknown, ApiResponse<ArchiveHistoryDetail>>(`/${type}/history-detail/${id}`, { params }),
  status: (type: string, id: number, status: number) => request.put(`/${type}/${id}/status`, { status }),
  archive: (type: string, id: number) => request.put(`/${type}/${id}/archive`),
  remove: (type: string, id: number) => request.delete(`/${type}/${id}`),
  export: (type: string) => request.get<unknown, Blob>(`/${type}/export`, { responseType: 'blob' }),
  import: (type: string, file: File) => {
    const data = new FormData()
    data.append('file', file)
    return request.post<unknown, ApiResponse<{ imported: number }>>(`/${type}/import`, data)
  }
}

export const fileApi = {
  list: (params: { taskId: number }) => request.get<unknown, ApiResponse<Attachment[]>>('/files', { params }),
  remove: (id: number) => request.delete(`/files/${id}`),
  downloadUrl: (id: number) => `/files/${id}/download-ticket`,
  previewUrl: (id: number) => `/files/${id}/preview`
}

export interface AiCitation {
  number: number
  policyId: number
  title: string
  revision: string
  sourceRef: string
  excerpt: string
}

export interface AiQualityResult {
  runId: number
  category: string
  documentDate?: string
  unit?: string
  missingItems: string[]
  suggestion: string
  insufficientEvidence: boolean
  citations: AiCitation[]
  reviewStatus: string
  disclaimer: string
}

export interface AiAnswer {
  answer: string
  citations: AiCitation[]
  insufficientEvidence: boolean
}

export interface AiRun {
  id: number
  attachmentId: number
  status: string
  reviewStatus: string
  errorCode?: string
  traceId: string
  createdAt: string
}

export interface AiPolicy {
  id: number
  title: string
  revision: string
  sourceRef: string
  status: string
}

export const aiApi = {
  status: () => request.get<unknown, ApiResponse<{ enabled: boolean }>>('/ai/status'),
  addPolicy: (data: { deptId: number; title: string; revision: string; sourceRef: string; content: string }) =>
    request.post<unknown, ApiResponse<{ id: number }>>('/ai/policies', data, { timeout: 180000 }),
  policies: (taskId: number) => request.get<unknown, ApiResponse<AiPolicy[]>>('/ai/policies', { params: { taskId } }),
  reindexPolicy: (id: number) => request.post(`/ai/policies/${id}/reindex`, {}, { timeout: 180000 }),
  retirePolicy: (id: number) => request.post(`/ai/policies/${id}/retire`),
  quality: (attachmentId: number) =>
    request.post<unknown, ApiResponse<AiQualityResult>>(
      `/ai/attachments/${attachmentId}/quality`,
      {},
      { timeout: 660000 }
    ),
  question: (taskId: number, question: string) =>
    request.post<unknown, ApiResponse<AiAnswer>>('/ai/questions', { taskId, question }, { timeout: 180000 }),
  runs: (taskId: number) => request.get<unknown, ApiResponse<AiRun[]>>('/ai/runs', { params: { taskId } }),
  run: (id: number) => request.get<unknown, ApiResponse<AiQualityResult>>(`/ai/runs/${id}`),
  review: (id: number, decision: 'ACCEPTED' | 'REJECTED') => request.post(`/ai/runs/${id}/review`, { decision }),
  replay: (id: number) =>
    request.post<unknown, ApiResponse<AiQualityResult>>(`/ai/runs/${id}/replay`, {}, { timeout: 660000 })
}

export const hiddenDangerApi = {
  list: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<HiddenDanger>>>('/hidden-dangers', { params }),
  detail: (id: number) => request.get<unknown, ApiResponse<HiddenDangerDetail>>(`/hidden-dangers/${id}`),
  targetTypes: () => request.get<unknown, ApiResponse<string[]>>('/hidden-dangers/target-types'),
  unfinishedCounts: () =>
    request.get<unknown, ApiResponse<Record<string, number>>>('/hidden-dangers/unfinished-counts'),
  defaultDeadlineDays: () => request.get<unknown, ApiResponse<number>>('/hidden-dangers/default-deadline-days'),
  save: (id: number, data: Record<string, unknown>) => request.put(`/hidden-dangers/${id}`, data),
  submit: (id: number, data: Record<string, unknown>) => request.post(`/hidden-dangers/${id}/submit`, data)
}

export const reminderApi = {
  list: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<Reminder>>>('/reminders', { params }),
  unreadCount: () => request.get<unknown, ApiResponse<number>>('/reminders/unread-count'),
  read: (id: number) => request.put(`/reminders/${id}/read`),
  readAll: () => request.put('/reminders/read-all')
}

export const statisticsApi = {
  overview: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<StatisticsOverview>>('/statistics/overview', { params }),
  charts: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<StatisticsCharts>>('/statistics/charts', { params }),
  ranking: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<StatisticsRanking>>>('/statistics/ranking', { params }),
  export: (params: Record<string, unknown>) =>
    request.get<unknown, Blob>('/statistics/export', { params, responseType: 'blob' })
}

export const stationInspectionApi = {
  progress: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<StationProgress>>>('/station-inspections/progress', { params }),
  detail: (stationDeptId: number, params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<StationInspectionDetail>>(`/station-inspections/${stationDeptId}`, { params })
}

export const systemApi = {
  period: () => request.get<unknown, ApiResponse<SystemPeriodInfo>>('/system-period'),
  adminOverview: () => request.get<unknown, ApiResponse<AdminOverview>>('/admin/overview'),
  users: (params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<UserAccount>>>('/users', { params }),
  user: (id: number) => request.get<unknown, ApiResponse<UserAccountForm>>(`/users/${id}`),
  createUser: (data: Record<string, unknown>) => request.post('/users', data),
  updateUser: (id: number, data: Record<string, unknown>) => request.put(`/users/${id}`, data),
  deleteUser: (id: number) => request.delete(`/users/${id}`),
  resetPassword: (id: number, password: string) => request.post(`/users/${id}/reset-password`, { password }),
  userStatus: (id: number, status: number) => request.put(`/users/${id}/status`, { status }),
  depts: () => request.get<unknown, ApiResponse<DepartmentNode[]>>('/depts/tree'),
  dept: (id: number) => request.get<unknown, ApiResponse<Department>>(`/depts/${id}`),
  createDept: (data: Record<string, unknown>) => request.post('/depts', data),
  updateDept: (id: number, data: Record<string, unknown>) => request.put(`/depts/${id}`, data),
  deleteDept: (id: number) => request.delete(`/depts/${id}`),
  deptStatus: (id: number, status: number) => request.put(`/depts/${id}/status`, { status }),
  roles: () => request.get<unknown, ApiResponse<Role[]>>('/roles'),
  role: (id: number) => request.get<unknown, ApiResponse<RoleDetail>>(`/roles/${id}`),
  createRole: (data: Record<string, unknown>) => request.post('/roles', data),
  updateRole: (id: number, data: Record<string, unknown>) => request.put(`/roles/${id}`, data),
  deleteRole: (id: number) => request.delete(`/roles/${id}`),
  menus: () => request.get<unknown, ApiResponse<Menu[]>>('/menus'),
  createMenu: (data: Record<string, unknown>) => request.post('/menus', data),
  updateMenu: (id: number, data: Record<string, unknown>) => request.put(`/menus/${id}`, data),
  deleteMenu: (id: number) => request.delete(`/menus/${id}`),
  configs: () => request.get<unknown, ApiResponse<SystemConfig[]>>('/configs'),
  saveConfigs: (data: SystemConfig[]) => request.put('/configs', data),
  dictTypes: () => request.get<unknown, ApiResponse<DictionaryType[]>>('/dict-types'),
  createDictType: (data: Record<string, unknown>) => request.post('/dict-types', data),
  updateDictType: (id: number, data: Record<string, unknown>) => request.put(`/dict-types/${id}`, data),
  deleteDictType: (id: number) => request.delete(`/dict-types/${id}`),
  dicts: (type?: string) => request.get<unknown, ApiResponse<DictionaryData[]>>('/dicts', { params: { type } }),
  createDict: (data: Record<string, unknown>) => request.post('/dicts', data),
  updateDict: (id: number, data: Record<string, unknown>) => request.put(`/dicts/${id}`, data),
  deleteDict: (id: number) => request.delete(`/dicts/${id}`),
  logs: (type: string, params: Record<string, unknown>) =>
    request.get<unknown, ApiResponse<PageResult<OperationLog | LoginLog>>>(`/logs/${type}`, { params })
}
