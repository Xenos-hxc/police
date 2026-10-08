<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi, fileApi, hiddenDangerApi, recordApi, taskApi } from '@/api'
import type { AiAnswer, AiPolicy, AiQualityResult, AiRun } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { authenticatedFileUrl, authenticatedNativeDownload } from '@/utils/download'
import { formatDateTime, periodLabel } from '@/utils/format'
import { getAccessToken } from '@/utils/token'
import { watchFileScanEvents } from '@/utils/fileScanEvents'
import type { Attachment, CheckRecord, CheckTask, UploadApiResponse, UploadProgressEvent } from '@/types'

type UploadType = 'VIDEO' | 'RECORD' | 'HAZARD_MATERIAL' | 'NOTICE'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const taskId = Number(route.params.id)
const task = ref<Partial<CheckTask>>({})
const attachments = ref<Attachment[]>([])
const loading = ref(false)
const aiEnabled = ref(false)
const aiBusy = ref(false)
const aiQuestion = ref('')
const aiAnswer = ref<AiAnswer | null>(null)
const aiQuality = ref<AiQualityResult | null>(null)
const aiRuns = ref<AiRun[]>([])
const aiPolicies = ref<AiPolicy[]>([])
const policyDialog = ref(false)
const policyForm = reactive({ title: '', revision: '', sourceRef: '', content: '' })
const saving = ref(false)
const submitting = ref(false)
const defaultDeadlineDays = ref(30)
const uploadProgress = reactive<Record<UploadType, number>>({ VIDEO: 0, RECORD: 0, HAZARD_MATERIAL: 0, NOTICE: 0 })
const uploading = reactive<Record<UploadType, boolean>>({
  VIDEO: false,
  RECORD: false,
  HAZARD_MATERIAL: false,
  NOTICE: false
})
const previewVisible = ref(false)
const previewUrl = ref('')
const previewType = ref<'image' | 'pdf'>('image')
const record = reactive<CheckRecord>({
  taskId,
  checkTime: '',
  inspectors: '',
  hasDanger: 0,
  rectificationType: '',
  dangerDetail: '',
  rectificationDeadline: '',
  remark: ''
})

const parseDateTime = (value?: string) => (value ? new Date(value.replace(' ', 'T')).getTime() : NaN)
const beforeDeadline = computed(() => {
  const deadline = parseDateTime(task.value.deadline)
  return Number.isFinite(deadline) && Date.now() <= deadline
})
const futurePeriod = computed(() => {
  const year = Number(task.value.taskYear)
  const quarter = Number(task.value.quarter)
  return Boolean(year && quarter && Date.now() < new Date(year, (quarter - 1) * 3, 1).getTime())
})
const isExecutor = computed(() => auth.user?.deptId === task.value.executorDeptId)
const editable = computed(
  () =>
    Number(task.value.countCoverage) === 1 &&
    isExecutor.value &&
    !futurePeriod.value &&
    (['PENDING', 'OVERDUE'].includes(task.value.status || '') ||
      (task.value.status === 'APPROVED' && beforeDeadline.value))
)
const editHint = computed(() => {
  if (Number(task.value.countCoverage) !== 1) return '当前对象未纳入本季度检查范围，仅可查看'
  if (!isExecutor.value) return '仅任务执行部门可以维护检查资料'
  if (futurePeriod.value) return '未到任务所属季度，仅可查看'
  if (task.value.status === 'OVERDUE') return '任务已逾期，仍可补交检查资料，提交后状态为逾期完成'
  if (task.value.status === 'APPROVED' && beforeDeadline.value) return '已完成检查在截止日期前仍可修改并重新提交'
  if (task.value.status === 'PENDING') return '请在截止日期前上传检查视频和检查笔录'
  return '当前检查资料仅可查看'
})
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${getAccessToken()}` }))
const uploadAction = `${import.meta.env.VITE_API_BASE_URL}/files/upload`
const inspectionAttachments = computed(() =>
  attachments.value.filter((file) =>
    ['VIDEO', 'RECORD', 'HAZARD_MATERIAL', 'NOTICE', 'PHOTO', 'OTHER'].includes(file.attachmentType)
  )
)
const targetTypeLabel: Record<string, string> = { STATION: '派出所', KEY_UNIT: '重点单位', IMPORTANT_PART: '重要部位' }
const statusLabel: Record<string, string> = {
  PENDING: '未开始',
  APPROVED: '已完成',
  OVERDUE: '已逾期',
  OVERDUE_SUBMITTED: '逾期完成'
}
const attachmentTypeLabel: Record<string, string> = {
  VIDEO: '检查视频',
  RECORD: '检查笔录',
  HAZARD_MATERIAL: '隐患材料',
  NOTICE: '责令改正通知书',
  PHOTO: '历史检查图片',
  OTHER: '历史附件'
}
const videoExtensions = ['mp4', 'mov', 'avi']
const materialExtensions = [
  'jpg',
  'jpeg',
  'png',
  'pdf',
  'doc',
  'docx',
  'xls',
  'xlsx',
  'ppt',
  'pptx',
  'txt',
  'zip',
  'rar',
  '7z'
]
const materialAccept = materialExtensions.map((item) => `.${item}`).join(',')
const previewExtensions = ['jpg', 'jpeg', 'png', 'pdf']

const filesOf = (type: string) => attachments.value.filter((file) => file.attachmentType === type)
const fileReady = (file: Attachment) =>
  file.storageStatus === 'ACTIVE' && ['CLEAN', 'SKIPPED'].includes(file.scanStatus || '')
const readyFilesOf = (type: string) => filesOf(type).filter(fileReady)
const scanStatusLabel = (file: Attachment) => {
  if (fileReady(file)) return file.scanStatus === 'SKIPPED' ? '未启用病毒扫描' : '已通过'
  if (file.scanStatus === 'REJECTED') return '安全检测拒绝'
  if (file.scanStatus === 'FAILED') return '检测失败，请删除重传'
  return '安全检测中'
}
const resetProgress = () => {
  ;(Object.keys(uploadProgress) as UploadType[]).forEach((type) => {
    if (!filesOf(type).length) {
      uploadProgress[type] = 0
      uploading[type] = false
    }
  })
}
const load = async () => {
  loading.value = true
  try {
    const [detail, defaultDays] = await Promise.all([taskApi.fullDetail(taskId), hiddenDangerApi.defaultDeadlineDays()])
    task.value = detail.data.task
    attachments.value = detail.data.attachments || []
    defaultDeadlineDays.value = defaultDays.data || 30
    if (detail.data.record) Object.assign(record, detail.data.record)
    record.hasDanger = Number(record.hasDanger || 0)
    resetProgress()
  } finally {
    loading.value = false
  }
}
const save = async (showMessage = true) => {
  saving.value = true
  try {
    if (record.id) await recordApi.update(record.id, record)
    else Object.assign(record, (await recordApi.create(record)).data)
    if (showMessage) ElMessage.success('检查资料草稿已保存')
  } finally {
    saving.value = false
  }
}
const validateBeforeSubmit = () => {
  if (!record.checkTime || !String(record.inspectors || '').trim()) return '请填写检查时间和检查人员'
  if (!readyFilesOf('VIDEO').length) return '请至少上传一个已通过安全检测的检查视频'
  if (!readyFilesOf('RECORD').length) return '请至少上传一份已通过安全检测的检查笔录'
  if (Number(record.hasDanger) !== 1) return ''
  if (!['DEADLINE', 'ROUTINE'].includes(record.rectificationType || '')) return '请选择期限改或例行改'
  if (!String(record.dangerDetail || '').trim()) return '请填写隐患详情'
  if (!readyFilesOf('HAZARD_MATERIAL').length) return '请至少上传一份已通过安全检测的隐患材料'
  if (record.rectificationType === 'DEADLINE' && !record.rectificationDeadline) return '请选择整改期限'
  if (record.rectificationType === 'DEADLINE' && !readyFilesOf('NOTICE').length)
    return '请至少上传一份已通过安全检测的责令改正通知书'
  return ''
}
const submit = async () => {
  if (futurePeriod.value) return ElMessage.warning('未到该任务所属季度，不能提交检查资料')
  const message = validateBeforeSubmit()
  if (message) return ElMessage.warning(message)
  submitting.value = true
  try {
    await save(false)
    if (!record.id) throw new Error('检查记录保存失败：未返回记录编号')
    await recordApi.submit(record.id)
    ElMessage.success(
      record.hasDanger === 1 && record.rectificationType === 'DEADLINE'
        ? '检查资料已提交，任务已完成，并已生成隐患整改任务'
        : '检查资料已提交，任务已完成'
    )
    await router.push('/tasks/list')
  } finally {
    submitting.value = false
  }
}

const uploadData = (type: UploadType) => ({ taskId, attachmentType: type })
const validateUpload = (type: UploadType) => (file: File) => {
  const extension = String(file.name.split('.').pop() || '').toLowerCase()
  const valid = type === 'VIDEO' ? videoExtensions.includes(extension) : materialExtensions.includes(extension)
  if (!valid)
    ElMessage.warning(
      type === 'VIDEO' ? '检查视频仅支持 mp4、mov、avi' : '材料支持图片、常用办公文档及 zip、rar、7z 压缩包'
    )
  return valid
}
const onUploadProgress = (type: UploadType) => (event: UploadProgressEvent) => {
  uploading[type] = true
  uploadProgress[type] = Math.max(0, Math.min(100, Math.round(Number(event.percent || 0))))
}
const uploadFailed = (type: UploadType) => {
  uploading[type] = false
  uploadProgress[type] = 0
  ElMessage.error('文件上传失败')
}
const refreshAttachments = async () => {
  attachments.value = (await fileApi.list({ taskId })).data
  resetProgress()
}
const uploaded = async (type: UploadType, response: UploadApiResponse) => {
  if (response?.code && response.code !== 200) {
    uploadFailed(type)
    return ElMessage.error(response.message || '文件上传失败')
  }
  uploading[type] = false
  uploadProgress[type] = 100
  ElMessage.success(response.data?.scanStatus === 'PENDING' ? '上传成功，正在进行安全检测' : '文件上传成功')
  await refreshAttachments()
}
const removeFile = async (file: Attachment) => {
  await ElMessageBox.confirm(`确认删除“${file.originalName}”？`, '删除确认')
  await fileApi.remove(file.id)
  const type = file.attachmentType as UploadType
  if (type in uploadProgress) {
    uploadProgress[type] = 0
    uploading[type] = false
  }
  await refreshAttachments()
}
const setDeadlineAfter = (days: number) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  record.rectificationDeadline = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')
  ].join('-')
}
const changeRectificationType = (value: string) => {
  if (value === 'DEADLINE' && !record.rectificationDeadline) setDeadlineAfter(defaultDeadlineDays.value)
  if (value === 'ROUTINE') record.rectificationDeadline = ''
}
const download = (file: Attachment) => authenticatedNativeDownload(fileApi.downloadUrl(file.id), file.originalName)
const canPreview = (file: Attachment) => previewExtensions.includes(String(file.extension || '').toLowerCase())
const preview = async (file: Attachment) => {
  const extension = String(file.extension || '').toLowerCase()
  if (!canPreview(file)) return ElMessage.info('该文件类型不支持在线预览，请下载后查看')
  previewType.value = ['jpg', 'jpeg', 'png'].includes(extension) ? 'image' : 'pdf'
  closePreview()
  previewUrl.value = await authenticatedFileUrl(fileApi.previewUrl(file.id))
  previewVisible.value = true
}
const closePreview = () => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}
const printPage = () => window.print()
const loadAiRuns = async () => {
  if (aiEnabled.value) {
    const [runs, policies] = await Promise.all([aiApi.runs(taskId), aiApi.policies(taskId)])
    aiRuns.value = runs.data
    aiPolicies.value = policies.data
  }
}
const inspectMaterial = async (file: Attachment) => {
  aiBusy.value = true
  try {
    aiQuality.value = (await aiApi.quality(file.id)).data
    await loadAiRuns()
  } finally {
    await loadAiRuns().catch(() => undefined)
    aiBusy.value = false
  }
}
const askPolicy = async () => {
  if (!aiQuestion.value.trim()) return ElMessage.warning('请先输入问题')
  aiBusy.value = true
  try {
    aiAnswer.value = (await aiApi.question(taskId, aiQuestion.value.trim())).data
  } finally {
    aiBusy.value = false
  }
}
const openAiRun = async (run: AiRun) => {
  aiQuality.value = (await aiApi.run(run.id)).data
}
const reviewAiRun = async (decision: 'ACCEPTED' | 'REJECTED') => {
  if (!aiQuality.value) return
  await aiApi.review(aiQuality.value.runId, decision)
  aiQuality.value = (await aiApi.run(aiQuality.value.runId)).data
  await loadAiRuns()
  ElMessage.success('审核结果已记录，业务数据未被自动修改')
}
const replayAiRun = async (run: AiRun) => {
  aiBusy.value = true
  try {
    aiQuality.value = (await aiApi.replay(run.id)).data
    await loadAiRuns()
  } finally {
    aiBusy.value = false
  }
}
const addPolicy = async () => {
  if (
    !task.value.executorDeptId ||
    !policyForm.title.trim() ||
    !policyForm.revision.trim() ||
    !policyForm.sourceRef.trim() ||
    !policyForm.content.trim()
  )
    return ElMessage.warning('请填写规章标题、版本、出处和正文')
  aiBusy.value = true
  try {
    await aiApi.addPolicy({ deptId: task.value.executorDeptId, ...policyForm })
    policyDialog.value = false
    aiPolicies.value = (await aiApi.policies(taskId)).data
    ElMessage.success('规章已写入本部门知识库')
  } finally {
    aiBusy.value = false
  }
}
const retirePolicy = async (policy: AiPolicy) => {
  await ElMessageBox.confirm(`确认停用《${policy.title}》？停用后问答将不再使用。`, '停用规章')
  await aiApi.retirePolicy(policy.id)
  aiPolicies.value = (await aiApi.policies(taskId)).data
}
const reindexPolicy = async (policy: AiPolicy) => {
  await aiApi.reindexPolicy(policy.id)
  aiPolicies.value = (await aiApi.policies(taskId)).data
}
let scanPoller: ReturnType<typeof setInterval> | undefined
let stopScanEvents: (() => void) | undefined
onMounted(() => {
  void load()
  void aiApi
    .status()
    .then(async (result) => {
      aiEnabled.value = result.data.enabled
      if (aiEnabled.value) await loadAiRuns()
    })
    .catch(() => undefined)
  stopScanEvents = watchFileScanEvents(taskId, () => {
    void refreshAttachments().catch(() => undefined)
  })
  scanPoller = setInterval(() => {
    if (attachments.value.some((file) => ['PENDING', 'SCANNING'].includes(file.scanStatus || ''))) {
      void refreshAttachments().catch(() => undefined)
    }
  }, 30000)
})
onBeforeUnmount(() => {
  closePreview()
  stopScanEvents?.()
  if (scanPoller) clearInterval(scanPoller)
})
</script>

<template>
  <div v-loading="loading" class="page task-detail-page">
    <div class="page-heading compact">
      <div>
        <h2>检查资料</h2>
        <p>{{ task.targetName }}</p>
      </div>
      <div>
        <el-tag
          size="large"
          :type="
            task.status === 'OVERDUE_SUBMITTED'
              ? 'warning'
              : task.overdue
                ? 'danger'
                : task.status === 'APPROVED'
                  ? 'success'
                  : 'primary'
          "
          >{{ statusLabel[task.status || ''] || '未知状态' }}</el-tag
        >
        <el-button @click="printPage"
          ><el-icon><Printer /></el-icon> 打印</el-button
        >
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </div>
    <section class="panel task-overview">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="检查对象">{{ task.targetName }}</el-descriptions-item>
        <el-descriptions-item label="对象类型">{{
          targetTypeLabel[task.targetType || ''] || task.targetType
        }}</el-descriptions-item>
        <el-descriptions-item label="统计周期">{{ periodLabel(task.taskYear, task.quarter) }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ formatDateTime(task.deadline) }}</el-descriptions-item>
        <el-descriptions-item label="任务来源">系统预置</el-descriptions-item>
        <el-descriptions-item label="计入覆盖率">{{ task.countCoverage ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="逾期补交">{{ task.overdueSubmitted ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ task.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </section>
    <section class="panel">
      <div class="panel-title">
        <b>检查记录</b><span>{{ editHint }}</span>
      </div>
      <el-form :model="record" label-width="120px" :disabled="!editable">
        <div class="form-grid">
          <el-form-item label="检查时间" required
            ><el-date-picker
              v-model="record.checkTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
          /></el-form-item>
          <el-form-item label="检查人员" required
            ><el-input v-model="record.inspectors" maxlength="255"
          /></el-form-item>
        </div>
        <el-form-item label="备注"
          ><el-input v-model="record.remark" type="textarea" :rows="2" maxlength="500" show-word-limit
        /></el-form-item>
      </el-form>
    </section>
    <section class="panel">
      <div class="panel-title"><b>视频和笔录</b><span>检查视频与检查笔录均为任务完成的必要材料</span></div>
      <div v-if="editable" class="upload-grid">
        <div>
          <el-upload
            drag
            multiple
            :action="uploadAction"
            :headers="uploadHeaders"
            :data="uploadData('VIDEO')"
            accept=".mp4,.mov,.avi"
            name="file"
            :show-file-list="false"
            :before-upload="validateUpload('VIDEO')"
            :on-progress="onUploadProgress('VIDEO')"
            :on-error="() => uploadFailed('VIDEO')"
            :on-success="(response: UploadApiResponse) => uploaded('VIDEO', response)"
          >
            <el-icon class="el-icon--upload"><VideoCamera /></el-icon>
            <div class="el-upload__text">上传检查视频</div> </el-upload
          ><el-progress v-if="uploading.VIDEO || uploadProgress.VIDEO > 0" :percentage="uploadProgress.VIDEO" />
        </div>
        <div>
          <el-upload
            drag
            multiple
            :action="uploadAction"
            :headers="uploadHeaders"
            :data="uploadData('RECORD')"
            :accept="materialAccept"
            name="file"
            :show-file-list="false"
            :before-upload="validateUpload('RECORD')"
            :on-progress="onUploadProgress('RECORD')"
            :on-error="() => uploadFailed('RECORD')"
            :on-success="(response: UploadApiResponse) => uploaded('RECORD', response)"
          >
            <el-icon class="el-icon--upload"><Document /></el-icon>
            <div class="el-upload__text">上传检查笔录</div> </el-upload
          ><el-progress v-if="uploading.RECORD || uploadProgress.RECORD > 0" :percentage="uploadProgress.RECORD" />
        </div>
      </div>
      <div class="danger-conclusion">
        <div class="danger-title"><b>隐患结论</b><span>选择期限改后将自动生成隐患整改任务</span></div>
        <el-radio-group v-model="record.hasDanger" :disabled="!editable"
          ><el-radio :value="0">无隐患</el-radio><el-radio :value="1">有隐患</el-radio></el-radio-group
        >
        <div v-if="record.hasDanger === 1" class="danger-fields">
          <el-radio-group v-model="record.rectificationType" :disabled="!editable" @change="changeRectificationType"
            ><el-radio value="DEADLINE">期限改</el-radio><el-radio value="ROUTINE">例行改</el-radio></el-radio-group
          >
          <el-form :model="record" label-width="130px" :disabled="!editable">
            <el-form-item label="隐患详情" required
              ><el-input v-model="record.dangerDetail" type="textarea" :rows="4" maxlength="3000" show-word-limit
            /></el-form-item>
            <el-form-item label="上传隐患图片" required>
              <div class="material-upload-row">
                <el-upload
                  multiple
                  :action="uploadAction"
                  :headers="uploadHeaders"
                  :data="uploadData('HAZARD_MATERIAL')"
                  :accept="materialAccept"
                  name="file"
                  :show-file-list="false"
                  :before-upload="validateUpload('HAZARD_MATERIAL')"
                  :on-progress="onUploadProgress('HAZARD_MATERIAL')"
                  :on-error="() => uploadFailed('HAZARD_MATERIAL')"
                  :on-success="(response: UploadApiResponse) => uploaded('HAZARD_MATERIAL', response)"
                >
                  <el-button
                    ><el-icon><Upload /></el-icon> 上传</el-button
                  > </el-upload
                ><span>支持图片、文档和 zip、rar、7z 压缩包</span
                ><el-progress
                  v-if="uploading.HAZARD_MATERIAL || uploadProgress.HAZARD_MATERIAL > 0"
                  :percentage="uploadProgress.HAZARD_MATERIAL"
                />
              </div>
            </el-form-item>
            <template v-if="record.rectificationType === 'DEADLINE'">
              <el-form-item label="整改期限" required>
                <div class="deadline-row">
                  <el-date-picker
                    v-model="record.rectificationDeadline"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :disabled-date="(date: Date) => date.getTime() < new Date().setHours(0, 0, 0, 0)"
                  /><el-button @click="setDeadlineAfter(7)">一周</el-button
                  ><el-button @click="setDeadlineAfter(30)">一个月</el-button
                  ><el-button @click="setDeadlineAfter(90)">三个月</el-button>
                </div>
              </el-form-item>
              <el-form-item label="责令改正通知书" required>
                <div class="material-upload-row">
                  <el-upload
                    multiple
                    :action="uploadAction"
                    :headers="uploadHeaders"
                    :data="uploadData('NOTICE')"
                    :accept="materialAccept"
                    name="file"
                    :show-file-list="false"
                    :before-upload="validateUpload('NOTICE')"
                    :on-progress="onUploadProgress('NOTICE')"
                    :on-error="() => uploadFailed('NOTICE')"
                    :on-success="(response: UploadApiResponse) => uploaded('NOTICE', response)"
                  >
                    <el-button
                      ><el-icon><Upload /></el-icon> 上传通知书材料</el-button
                    > </el-upload
                  ><span>支持多份图片、文档或压缩包</span
                  ><el-progress
                    v-if="uploading.NOTICE || uploadProgress.NOTICE > 0"
                    :percentage="uploadProgress.NOTICE"
                  />
                </div>
              </el-form-item>
            </template>
          </el-form>
        </div>
      </div>
      <el-table :data="inspectionAttachments" table-layout="auto" style="margin-top: 16px">
        <el-table-column prop="originalName" label="文件名" min-width="240" />
        <el-table-column label="材料类型" width="150"
          ><template #default="{ row }">{{
            attachmentTypeLabel[row.attachmentType] || '材料'
          }}</template></el-table-column
        >
        <el-table-column label="大小" width="120"
          ><template #default="{ row }">{{ (row.fileSize / 1024 / 1024).toFixed(2) }} MB</template></el-table-column
        >
        <el-table-column label="安全状态" width="160"
          ><template #default="{ row }">{{ scanStatusLabel(row) }}</template></el-table-column
        >
        <el-table-column label="上传时间" width="180"
          ><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column
        >
        <el-table-column label="操作" width="240"
          ><template #default="{ row }"
            ><el-button v-if="fileReady(row) && canPreview(row)" link @click="preview(row)">预览</el-button
            ><el-button v-if="fileReady(row)" link @click="download(row)">下载</el-button
            ><el-button
              v-if="
                aiEnabled &&
                row.scanStatus === 'CLEAN' &&
                fileReady(row) &&
                ['pdf', 'docx', 'xlsx', 'pptx', 'txt', 'jpg', 'jpeg', 'png'].includes(row.extension)
              "
              link
              type="primary"
              :loading="aiBusy"
              @click="inspectMaterial(row)"
              >智能质检</el-button
            ><el-button v-if="editable" link type="danger" @click="removeFile(row)">删除</el-button></template
          ></el-table-column
        >
      </el-table>
      <div v-if="editable" class="submit-row">
        <el-button size="large" :loading="saving" @click="save(true)">保存草稿</el-button
        ><el-button type="primary" size="large" :loading="submitting" @click="submit">提交检查资料</el-button>
      </div>
    </section>
    <section v-if="aiEnabled" class="panel">
      <div class="panel-title">
        <b>智能质检与整改助手</b><span>仅调用本地模型；建议需人工确认，不自动修改检查或整改数据</span>
      </div>
      <el-button v-if="auth.roles.includes('BUREAU')" @click="policyDialog = true">维护本任务部门规章</el-button>
      <el-table v-if="aiPolicies.length" :data="aiPolicies" size="small" style="margin: 12px 0">
        <el-table-column prop="title" label="规章" min-width="180" />
        <el-table-column prop="revision" label="版本" width="120" />
        <el-table-column prop="sourceRef" label="出处" min-width="150" />
        <el-table-column prop="status" label="索引状态" width="110" />
        <el-table-column v-if="auth.roles.includes('BUREAU')" label="管理" width="140"
          ><template #default="{ row }">
            <el-button v-if="row.status === 'INDEXING'" link @click="reindexPolicy(row)">重试索引</el-button>
            <el-button v-if="row.status !== 'RETIRED'" link type="danger" @click="retirePolicy(row)">停用</el-button>
          </template></el-table-column
        >
      </el-table>
      <el-input
        v-model="aiQuestion"
        type="textarea"
        :rows="2"
        maxlength="500"
        show-word-limit
        placeholder="询问本任务所属部门的规章依据"
      />
      <el-button style="margin-top: 10px" type="primary" :loading="aiBusy" @click="askPolicy">检索规章并提问</el-button>
      <div v-if="aiAnswer" class="ai-result">
        <p>{{ aiAnswer.answer }}</p>
        <el-alert v-if="aiAnswer.insufficientEvidence" type="warning" title="依据不足，请人工核对" :closable="false" />
        <div v-for="citation in aiAnswer.citations" :key="citation.number" class="ai-citation">
          [{{ citation.number }}] {{ citation.title }}（{{ citation.revision }}；{{ citation.sourceRef }}）：{{
            citation.excerpt
          }}
        </div>
      </div>
      <div v-if="aiQuality" class="ai-result">
        <b>材料质检 #{{ aiQuality.runId }}</b>
        <p>
          类别：{{
            aiQuality.category === 'UNKNOWN'
              ? '未识别（请人工确认）'
              : attachmentTypeLabel[aiQuality.category] || aiQuality.category
          }}；日期：{{ aiQuality.documentDate || '未识别' }}；单位：{{ aiQuality.unit || '未识别' }}
        </p>
        <p>缺失/待核对：{{ aiQuality.missingItems.join('、') || '未发现' }}</p>
        <p>整改建议：{{ aiQuality.suggestion }}</p>
        <el-alert
          v-if="aiQuality.insufficientEvidence"
          title="整改建议依据不足；字段识别结果仍需人工复核，不能视为整改结论。"
          type="warning"
          :closable="false"
        />
        <div v-for="citation in aiQuality.citations" :key="citation.number" class="ai-citation">
          [{{ citation.number }}] {{ citation.title }}（{{ citation.revision }}；{{ citation.sourceRef }}）：{{
            citation.excerpt
          }}
        </div>
        <el-alert :title="aiQuality.disclaimer" type="warning" :closable="false" />
        <div v-if="aiQuality.reviewStatus === 'PENDING'" style="margin-top: 10px">
          <el-button type="success" @click="reviewAiRun('ACCEPTED')">人工确认建议</el-button>
          <el-button @click="reviewAiRun('REJECTED')">驳回建议</el-button>
        </div>
      </div>
      <el-table :data="aiRuns" size="small" style="margin-top: 16px">
        <el-table-column prop="id" label="质检编号" width="110" />
        <el-table-column prop="attachmentId" label="附件编号" width="110" />
        <el-table-column prop="status" label="运行状态" width="130" />
        <el-table-column prop="reviewStatus" label="人工确认" width="130" />
        <el-table-column prop="traceId" label="追踪编号" min-width="250" />
        <el-table-column prop="createdAt" label="时间" min-width="180" />
        <el-table-column label="操作" width="160"
          ><template #default="{ row }">
            <el-button v-if="row.status === 'SUCCEEDED'" link @click="openAiRun(row)">查看</el-button>
            <el-button v-if="row.status === 'FAILED'" link type="primary" :loading="aiBusy" @click="replayAiRun(row)"
              >失败回放</el-button
            >
          </template></el-table-column
        >
      </el-table>
    </section>
    <el-dialog v-model="policyDialog" title="录入规章（仅公安处）" width="720px">
      <p>规章将只供该任务执行部门检索。请核对版本和出处，不要录入无效或涉密但未经授权的内容。</p>
      <el-form :model="policyForm" label-width="90px">
        <el-form-item label="标题"><el-input v-model="policyForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="policyForm.revision" maxlength="80" /></el-form-item>
        <el-form-item label="出处"><el-input v-model="policyForm.sourceRef" maxlength="200" /></el-form-item>
        <el-form-item label="正文"
          ><el-input v-model="policyForm.content" type="textarea" :rows="10" maxlength="20000"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="policyDialog = false">取消</el-button
        ><el-button type="primary" :loading="aiBusy" @click="addPolicy">确认并索引</el-button></template
      >
    </el-dialog>
    <el-dialog v-model="previewVisible" title="材料预览" width="900px" @closed="closePreview"
      ><img
        v-if="previewUrl && previewType === 'image'"
        :src="previewUrl"
        alt="材料预览"
        class="preview-image" /><iframe v-else-if="previewUrl" :src="previewUrl" title="PDF预览" class="preview-pdf"
    /></el-dialog>
  </div>
</template>

<style scoped>
.ai-result {
  margin-top: 16px;
  padding: 14px;
  background: #f7faff;
  border: 1px solid #dfe7f1;
  border-radius: 6px;
}
.ai-citation {
  margin: 8px 0;
  color: #52647a;
  font-size: 13px;
}
.danger-conclusion {
  margin-top: 22px;
  padding: 20px;
  border: 1px solid #dfe7f1;
  border-radius: 6px;
  background: #f8fbff;
}
.danger-title {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
.danger-title span,
.material-upload-row > span {
  color: #718096;
  font-size: 13px;
}
.danger-fields {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #e3eaf2;
}
.danger-fields > .el-radio-group {
  margin-bottom: 18px;
}
.material-upload-row {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.material-upload-row .el-progress {
  grid-column: 1/-1;
}
.deadline-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.preview-image {
  display: block;
  max-width: 100%;
  max-height: 650px;
  margin: auto;
}
.preview-pdf {
  width: 100%;
  height: 650px;
  border: 0;
}
</style>
