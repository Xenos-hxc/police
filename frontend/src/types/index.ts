export type Id = number
export type DateString = string
export type DateTimeString = string
export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface UserInfo {
  id: Id
  username: string
  realName: string
  deptId: Id
  deptName: string
  roles: string[]
  permissions: string[]
}

export interface SystemPeriodInfo {
  startYear: number
  startQuarter: number
  currentYear: number
  currentQuarter: number
  effectiveYear: number
  effectiveQuarter: number
}

export interface CheckTask {
  id: Id
  taskNo: string
  taskName: string
  checkType: string
  taskCategory?: string
  creationMode: string
  initiatorDeptId?: Id
  executorDeptId: Id
  targetId?: Id
  targetType: string
  targetName: string
  taskYear: number
  quarter?: number
  halfYear?: number
  startDate?: DateString
  deadline: DateTimeString
  status: string
  overdue: number
  overdueSubmitted?: number
  countCoverage?: number
  version?: number
  remark?: string
  createTime?: DateTimeString
  updateTime?: DateTimeString
}

export interface CheckRecord {
  id?: Id
  taskId?: Id
  checkTime: DateTimeString
  inspectors: string
  hasDanger: number
  rectificationType?: string
  dangerDetail?: string
  rectificationDeadline?: DateString
  submittedBy?: Id
  submittedTime?: DateTimeString
  complete?: number
  remark?: string
  createTime?: DateTimeString
  updateTime?: DateTimeString
}

export interface Attachment {
  id: Id
  taskId: Id
  recordId?: Id
  attachmentType: string
  originalName: string
  fileSize: number
  contentType: string
  extension: string
  sha256?: string
  scanStatus?: string
  storageStatus?: string
  createTime?: DateTimeString
}

export interface TaskFullDetail {
  task: CheckTask
  record?: CheckRecord
  attachments: Attachment[]
}

export interface TaskListSummary {
  totalCount: number
  completedCount: number
  unfinishedCount: number
  completionRate: number
}

export interface SelectionStatus {
  targetType: string
  targetLabel: string
  requiredCount: number
  submittedCount: number
  selectedCount: number
  remainingNeed: number
  fullCoverage: boolean
  needSelection: boolean
  initialized: boolean
}

export interface SelectionTarget {
  taskId?: Id
  targetId: Id
  targetName: string
  selected: boolean
}

export interface SelectionPool extends Omit<SelectionStatus, 'fullCoverage' | 'needSelection' | 'initialized'> {
  selectableCount: number
  targets: SelectionTarget[]
}

export interface DashboardSummary {
  coverageRate: number
  submissionRate: number
  overdueCount: number
  pendingUploadCount: number
  coveredCount: number
  uncoveredCount: number
  completedCount: number
  unfinishedCount: number
  year: number
  quarter: number
}

export interface TargetCompletion {
  completed: number
  unfinished: number
}

export interface DashboardCharts {
  status: Record<string, number>
  targetCompletion: Record<string, TargetCompletion>
}

export interface DepartmentNode {
  id: Id
  parentId: Id
  label: string
  deptType: string
  status: number
  children: DepartmentNode[]
  treeKey?: string
  nodeType?: string
  archiveId?: Id
}

export interface Department {
  id: Id
  parentId: Id
  ancestors?: string
  deptName: string
  deptType: string
  leader?: string
  phone?: string
  address?: string
  jurisdiction?: string
  sortNo?: number
  status: number
  archived?: number
  createTime?: DateTimeString
  updateTime?: DateTimeString
  remark?: string
  nodeType?: string
}

export interface UserAccount {
  id: Id
  deptId: Id
  deptName: string
  username: string
  status: number
  dataScope: string
  roles: string[]
  lastLoginTime?: DateTimeString
  createTime?: DateTimeString
}

export interface UserAccountForm {
  id?: Id
  deptId?: Id
  username?: string
  password?: string
  status?: number
  dataScope?: string
  roleIds?: Id[]
  accountType?: string
  parentDeptId?: Id
}

export interface Role {
  id: Id
  roleName: string
  roleCode: string
  dataScope: string
  status: number
  createTime?: DateTimeString
  updateTime?: DateTimeString
  remark?: string
}

export interface RoleDetail {
  role: Role
  menuIds: Id[]
}

export interface Menu {
  id: Id
  parentId: Id
  menuName: string
  menuType: string
  path?: string
  component?: string
  permission?: string
  icon?: string
  sortNo?: number
  visible?: number
  status?: number
  remark?: string
  label?: string
  children?: Menu[]
}

export interface SystemConfig {
  id: Id
  configName: string
  configKey: string
  configValue: string
  valueType: string
  systemFlag: number
  remark?: string
}

export interface DictionaryType {
  id: Id
  dictName: string
  dictType: string
  status: number
  remark?: string
}

export interface DictionaryData {
  id: Id
  dictType: string
  dictLabel: string
  dictValue: string
  sortNo: number
  status: number
  colorType?: string
  remark?: string
}

export interface OperationLog {
  id: Id
  userId?: Id
  username?: string
  deptId?: Id
  operationType?: string
  module?: string
  content?: string
  requestMethod?: string
  requestUri?: string
  requestIp?: string
  result?: number
  failureReason?: string
  costTime?: number
  createTime?: DateTimeString
}

export interface LoginLog {
  id: Id
  username: string
  loginIp?: string
  browser?: string
  os?: string
  status: number
  message?: string
  loginTime?: DateTimeString
}

export type SystemRow = Partial<UserAccount & Role & Menu & SystemConfig & OperationLog & LoginLog>

export interface AdminOverview {
  deptCounts: Record<string, number>
  archiveCounts: Record<string, number>
  accountCovered: Record<string, number>
  accountCount: number
  missingAccountCount: number
  relationCounts: Record<string, number>
  sharedTargetCounts: Record<string, number>
  unassignedArchiveCounts: Record<string, number>
}

export interface ArchiveRecord {
  id: Id
  deptId?: Id
  stationCode?: string
  stationName?: string
  unitCode?: string
  unitName?: string
  unitType?: string
  partCode?: string
  partName?: string
  partType?: string
  establishedDate?: DateString
  removedDate?: DateString
  leader?: string
  contactPerson?: string
  phone?: string
  address?: string
  jurisdiction?: string
  jurisdictionText?: string
  guardStatus?: string
  lengthDescription?: string
  railwayLine?: string
  kilometerMark?: string
  location?: string
  responsibleUnit?: string
  workshop?: string
  bureauName?: string
  lastCheckTime?: DateTimeString
  lastCheckResult?: string
  status: number
  archived?: number
  stationDeptIds?: Id[]
  stationNames?: string[]
  remark?: string
  createTime?: DateTimeString
  updateTime?: DateTimeString
}

export interface ArchiveHistoryProgress {
  year: number
  quarter: number
  total: number
  completed: number
  unfinished: number
  submitted: number
  overdue: number
  overdueCompleted: number
  rate: number
}

export interface ArchiveHistoryItem {
  task: CheckTask
  executorDeptName: string
  executorDeptType: string
  record?: CheckRecord
  attachments: Attachment[]
}

export interface ArchiveHistoryDetail {
  archive: ArchiveRecord
  progress: ArchiveHistoryProgress
  items: ArchiveHistoryItem[]
  businessHistoryVisible: boolean
}

export interface HiddenDanger {
  id: Id
  dangerNo: string
  taskId: Id
  taskNo: string
  taskYear: number
  quarter: number
  executorDeptId: Id
  executorDeptName?: string
  targetId: Id
  targetType: string
  targetName: string
  dangerDetail: string
  rectificationType?: string
  rectificationDeadline?: DateString
  status: string
  rectificationCheckTime?: DateTimeString
  rectificationInspectors?: string
  rectificationRemark?: string
  rectificationSubmittedBy?: Id
  rectificationSubmittedTime?: DateTimeString
}

export interface HiddenDangerDetail {
  danger: HiddenDanger
  executorDeptName: string
  attachments: Attachment[]
  editable: boolean
}

export interface Reminder {
  id: Id
  taskId?: Id
  receiverUserId?: Id
  receiverDeptId?: Id
  remindType: string
  remindContent: string
  remindTime: DateTimeString
  readFlag: number
}

export interface StatisticsOverview {
  requiredCount: number
  coveredCount: number
  uncoveredCount: number
  taskCount: number
  submittedCount: number
  unsubmittedCount: number
  overdueCount: number
  rejectedCount: number
  coverageRate: number
  submissionRate: number
  overdueRate: number
}

export interface StatisticsCompletion {
  required?: number
  completed: number
  unfinished: number
  overdue?: number
}

export interface StatisticsTrend {
  quarter: number
  label: string
  required: number
  completed: number
  unfinished: number
}

export interface StatisticsCharts {
  status: Record<string, number>
  targetCompletion: Record<string, StatisticsCompletion>
  scopeCompletion: Record<string, StatisticsCompletion>
  trend: StatisticsTrend[]
}

export interface StatisticsRanking {
  deptId: Id
  deptName: string
  deptType: string
  taskCount: number
  completedCount: number
  overdueCount: number
  completionRate: number
  overdueRate: number
}

export interface TargetProgress {
  initialized: boolean
  requiredCount: number
  completedCount: number
  unfinishedCount: number
  overdueCount: number
  completionRate: number
}

export interface QuarterProgress extends TargetProgress {
  quarter: number
  keyUnit: TargetProgress
  importantPart: TargetProgress
}

export interface StationInfo {
  deptId: Id
  stationName: string
}

export interface StationProgress {
  deptId: Id
  stationName: string
  year: number
  quarter: number
  progress: QuarterProgress
}

export interface InspectionItem {
  task: CheckTask
  record?: CheckRecord
  attachmentCount: number
}

export interface StationInspectionDetail {
  station: StationInfo
  progress: QuarterProgress
  tasks: PageResult<InspectionItem>
}

export interface UploadProgressEvent {
  percent?: number
}

export interface UploadApiResponse {
  code: number
  message?: string
  data?: Attachment
}

export interface ChartCallbackParam {
  dataIndex: number
}

export interface TreeNode {
  id?: Id
  treeKey?: string
  label: string
  nodeType?: string
  archiveId?: Id
  deptType?: string
  parentId?: Id
  children?: TreeNode[]
}

export type EditableForm = Record<string, unknown> & {
  id?: Id
  kind?: 'type' | 'data'
  status?: number
  dataScope?: string
  roleIds?: Id[]
  menuIds?: Id[]
  accountType?: string
  parentId?: Id
  parentDeptId?: Id
  deptId?: Id
  username?: string
  password?: string
  deptName?: string
  deptType?: string
  roleName?: string
  roleCode?: string
  menuName?: string
  menuType?: string
  sortNo?: number
  visible?: number
  dictName?: string
  dictType?: string
  dictLabel?: string
  dictValue?: string
  colorType?: string
  remark?: string
  stationName?: string
  unitName?: string
  unitType?: string
  partName?: string
  partType?: string
  establishedDate?: string
  removedDate?: string
  leader?: string
  contactPerson?: string
  phone?: string
  address?: string
  bureauName?: string
  jurisdictionText?: string
  stationDeptIds?: Id[]
  guardStatus?: string
  lengthDescription?: string
  railwayLine?: string
  kilometerMark?: string
  location?: string
  responsibleUnit?: string
  workshop?: string
}
