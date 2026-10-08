<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fileApi, hiddenDangerApi } from '@/api'
import { authenticatedFileUrl, authenticatedNativeDownload } from '@/utils/download'
import { formatDateTime, quarterLabel } from '@/utils/format'
import { getAccessToken } from '@/utils/token'
import { watchFileScanEvents } from '@/utils/fileScanEvents'
import type { Attachment, HiddenDanger, TagType, UploadApiResponse, UploadProgressEvent } from '@/types'

interface RectificationForm extends Record<string, unknown> {
  checkTime: string
  inspectors: string
  remark: string
}

type UploadType = 'RECTIFICATION_VIDEO' | 'RECTIFICATION_ATTACHMENT'
const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const detail = ref<Partial<HiddenDanger>>({})
const attachments = ref<Attachment[]>([])
const editable = ref(false)
const form = reactive<RectificationForm>({ checkTime: '', inspectors: '', remark: '' })
const progress = reactive<Record<UploadType, number>>({ RECTIFICATION_VIDEO: 0, RECTIFICATION_ATTACHMENT: 0 })
const uploading = reactive<Record<UploadType, boolean>>({ RECTIFICATION_VIDEO: false, RECTIFICATION_ATTACHMENT: false })
const previewVisible = ref(false)
const previewUrl = ref('')
const previewType = ref<'image' | 'pdf'>('image')
const uploadAction = `${import.meta.env.VITE_API_BASE_URL}/files/upload`
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${getAccessToken()}` }))
const targetTypeLabel: Record<string, string> = { STATION: '派出所', KEY_UNIT: '重点单位', IMPORTANT_PART: '重要部位' }
const statusLabel: Record<string, string> = {
  PENDING: '待整改',
  OVERDUE: '已逾期',
  COMPLETED: '已完成',
  OVERDUE_COMPLETED: '逾期完成'
}
const statusTag: Record<string, TagType> = {
  PENDING: 'warning',
  OVERDUE: 'danger',
  COMPLETED: 'success',
  OVERDUE_COMPLETED: 'warning'
}
const attachmentLabel: Record<string, string> = {
  HAZARD_MATERIAL: '原隐患材料',
  NOTICE: '责令改正通知书',
  RECTIFICATION_VIDEO: '整改检查视频',
  RECTIFICATION_ATTACHMENT: '整改附件'
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
const visibleAttachmentTypes = ['HAZARD_MATERIAL', 'NOTICE', 'RECTIFICATION_VIDEO', 'RECTIFICATION_ATTACHMENT']
const filesOf = (type: UploadType) => attachments.value.filter((file) => file.attachmentType === type)

const resetProgress = () =>
  (Object.keys(progress) as UploadType[]).forEach((type) => {
    if (!filesOf(type).length) {
      progress[type] = 0
      uploading[type] = false
    }
  })
const refreshAttachments = async () => {
  if (!detail.value.taskId) return
  const response = await fileApi.list({ taskId: detail.value.taskId })
  attachments.value = (response.data || []).filter((file) => visibleAttachmentTypes.includes(file.attachmentType))
  resetProgress()
}
const load = async () => {
  loading.value = true
  try {
    const response = await hiddenDangerApi.detail(id)
    detail.value = { ...response.data.danger, executorDeptName: response.data.executorDeptName }
    attachments.value = response.data.attachments || []
    editable.value = Boolean(response.data.editable)
    Object.assign(form, {
      checkTime: detail.value.rectificationCheckTime || '',
      inspectors: detail.value.rectificationInspectors || '',
      remark: detail.value.rectificationRemark || ''
    })
    resetProgress()
  } finally {
    loading.value = false
  }
}
const uploadData = (type: UploadType) => ({ taskId: detail.value.taskId, attachmentType: type })
const validateUpload = (type: UploadType) => (file: File) => {
  const extension = String(file.name.split('.').pop() || '').toLowerCase()
  const valid =
    type === 'RECTIFICATION_VIDEO' ? videoExtensions.includes(extension) : materialExtensions.includes(extension)
  if (!valid)
    ElMessage.warning(
      type === 'RECTIFICATION_VIDEO' ? '整改视频仅支持 mp4、mov、avi' : '整改附件支持图片、办公文档和压缩包'
    )
  return valid
}
const onProgress = (type: UploadType) => (event: UploadProgressEvent) => {
  uploading[type] = true
  progress[type] = Math.round(Number(event.percent || 0))
}
const onError = (type: UploadType) => {
  uploading[type] = false
  progress[type] = 0
  ElMessage.error('文件上传失败')
}
const uploaded = async (type: UploadType, response: UploadApiResponse) => {
  if (response?.code && response.code !== 200) {
    onError(type)
    return ElMessage.error(response.message || '上传失败')
  }
  uploading[type] = false
  progress[type] = 100
  ElMessage.success(response.data?.scanStatus === 'PENDING' ? '上传成功，正在进行安全检测' : '文件上传成功')
  await refreshAttachments()
}
const removeFile = async (file: Attachment) => {
  await ElMessageBox.confirm(`确认删除“${file.originalName}”？`, '删除确认')
  await fileApi.remove(file.id)
  const type = file.attachmentType as UploadType
  if (type in progress) {
    progress[type] = 0
    uploading[type] = false
  }
  await refreshAttachments()
}
const save = async (message = true) => {
  saving.value = true
  try {
    await hiddenDangerApi.save(id, form)
    if (message) ElMessage.success('整改草稿已保存')
  } finally {
    saving.value = false
  }
}
const submit = async () => {
  if (!form.checkTime || !String(form.inspectors || '').trim()) return ElMessage.warning('请填写检查时间和检查人员')
  if (!filesOf('RECTIFICATION_VIDEO').some(fileReady))
    return ElMessage.warning('请至少上传一个已通过安全检测的整改检查视频')
  if (!filesOf('RECTIFICATION_ATTACHMENT').some(fileReady))
    return ElMessage.warning('请至少上传一份已通过安全检测的整改附件')
  submitting.value = true
  try {
    await hiddenDangerApi.submit(id, form)
    ElMessage.success('隐患整改资料已提交，整改任务已完成')
    await router.push('/hidden-dangers')
  } finally {
    submitting.value = false
  }
}
const download = (file: Attachment) => authenticatedNativeDownload(fileApi.downloadUrl(file.id), file.originalName)
const fileReady = (file: Attachment) =>
  file.storageStatus === 'ACTIVE' && ['CLEAN', 'SKIPPED'].includes(file.scanStatus || '')
const canPreview = (file: Attachment) => previewExtensions.includes(String(file.extension || '').toLowerCase())
const closePreview = () => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}
const preview = async (file: Attachment) => {
  const extension = String(file.extension || '').toLowerCase()
  if (!canPreview(file)) return ElMessage.info('该文件类型不支持在线预览，请下载后查看')
  previewType.value = ['jpg', 'jpeg', 'png'].includes(extension) ? 'image' : 'pdf'
  closePreview()
  previewUrl.value = await authenticatedFileUrl(fileApi.previewUrl(file.id))
  previewVisible.value = true
}
let scanPoller: ReturnType<typeof setInterval> | undefined
let stopScanEvents: (() => void) | undefined
let scanViewMounted = false
onMounted(() => {
  scanViewMounted = true
  void load()
    .then(() => {
      if (scanViewMounted && detail.value.taskId) {
        stopScanEvents = watchFileScanEvents(detail.value.taskId, () => {
          void refreshAttachments().catch(() => undefined)
        })
      }
    })
    .catch(() => undefined)
  scanPoller = setInterval(() => {
    if (attachments.value.some((file) => ['PENDING', 'SCANNING'].includes(file.scanStatus || ''))) {
      void refreshAttachments().catch(() => undefined)
    }
  }, 30000)
})
onBeforeUnmount(() => {
  scanViewMounted = false
  closePreview()
  stopScanEvents?.()
  if (scanPoller) clearInterval(scanPoller)
})
</script>

<template>
  <div v-loading="loading" class="page task-detail-page">
    <div class="page-heading compact">
      <div>
        <h2>隐患整改详情</h2>
        <p>{{ detail.targetName }}</p>
      </div>
      <div>
        <el-tag size="large" :type="statusTag[detail.status || '']">{{
          statusLabel[detail.status || ''] || detail.status
        }}</el-tag
        ><el-button @click="$router.back()">返回</el-button>
      </div>
    </div>
    <section class="panel task-overview">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="对象类型">{{ targetTypeLabel[detail.targetType || ''] }}</el-descriptions-item
        ><el-descriptions-item label="检查对象">{{ detail.targetName }}</el-descriptions-item
        ><el-descriptions-item label="执行部门">{{ detail.executorDeptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="统计周期"
          >{{ detail.taskYear }}年 {{ quarterLabel(detail.quarter || 0) }}</el-descriptions-item
        ><el-descriptions-item label="整改类型">期限改</el-descriptions-item
        ><el-descriptions-item label="整改期限">{{ detail.rectificationDeadline }}</el-descriptions-item>
        <el-descriptions-item label="隐患详情" :span="3">{{ detail.dangerDetail }}</el-descriptions-item>
      </el-descriptions>
    </section>
    <section class="panel">
      <div class="panel-title"><b>整改检查记录</b><span>填写整改检查情况并上传检查视频和整改材料</span></div>
      <el-form :model="form" label-width="120px" :disabled="!editable">
        <div class="form-grid">
          <el-form-item label="检查时间" required
            ><el-date-picker
              v-model="form.checkTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%" /></el-form-item
          ><el-form-item label="检查人员" required><el-input v-model="form.inspectors" maxlength="255" /></el-form-item>
        </div>
        <el-form-item label="备注"
          ><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit
        /></el-form-item>
      </el-form>
      <div v-if="editable" class="upload-grid">
        <div>
          <el-upload
            drag
            multiple
            :action="uploadAction"
            :headers="uploadHeaders"
            :data="uploadData('RECTIFICATION_VIDEO')"
            accept=".mp4,.mov,.avi"
            name="file"
            :show-file-list="false"
            :before-upload="validateUpload('RECTIFICATION_VIDEO')"
            :on-progress="onProgress('RECTIFICATION_VIDEO')"
            :on-error="() => onError('RECTIFICATION_VIDEO')"
            :on-success="(response: UploadApiResponse) => uploaded('RECTIFICATION_VIDEO', response)"
            ><el-icon class="el-icon--upload"><VideoCamera /></el-icon>
            <div class="el-upload__text">上传整改检查视频</div></el-upload
          ><el-progress
            v-if="uploading.RECTIFICATION_VIDEO || progress.RECTIFICATION_VIDEO"
            :percentage="progress.RECTIFICATION_VIDEO"
          />
        </div>
        <div>
          <el-upload
            drag
            multiple
            :action="uploadAction"
            :headers="uploadHeaders"
            :data="uploadData('RECTIFICATION_ATTACHMENT')"
            :accept="materialAccept"
            name="file"
            :show-file-list="false"
            :before-upload="validateUpload('RECTIFICATION_ATTACHMENT')"
            :on-progress="onProgress('RECTIFICATION_ATTACHMENT')"
            :on-error="() => onError('RECTIFICATION_ATTACHMENT')"
            :on-success="(response: UploadApiResponse) => uploaded('RECTIFICATION_ATTACHMENT', response)"
            ><el-icon class="el-icon--upload"><Files /></el-icon>
            <div class="el-upload__text">上传整改附件</div></el-upload
          ><el-progress
            v-if="uploading.RECTIFICATION_ATTACHMENT || progress.RECTIFICATION_ATTACHMENT"
            :percentage="progress.RECTIFICATION_ATTACHMENT"
          />
        </div>
      </div>
      <el-table :data="attachments" table-layout="auto" style="margin-top: 16px"
        ><el-table-column prop="originalName" label="文件名" min-width="240" /><el-table-column
          label="材料类型"
          width="150"
          ><template #default="{ row }">{{ attachmentLabel[row.attachmentType] }}</template></el-table-column
        ><el-table-column label="大小" width="110"
          ><template #default="{ row }">{{ (row.fileSize / 1024 / 1024).toFixed(2) }} MB</template></el-table-column
        ><el-table-column label="上传时间" width="170"
          ><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column
        ><el-table-column label="安全状态" width="140"
          ><template #default="{ row }">{{
            fileReady(row)
              ? '已通过'
              : row.scanStatus === 'REJECTED'
                ? '检测拒绝'
                : row.scanStatus === 'FAILED'
                  ? '检测失败'
                  : '检测中'
          }}</template></el-table-column
        ><el-table-column label="操作" width="170"
          ><template #default="{ row }"
            ><el-button v-if="fileReady(row) && canPreview(row)" link @click="preview(row)">预览</el-button
            ><el-button v-if="fileReady(row)" link @click="download(row)">下载</el-button
            ><el-button
              v-if="editable && ['RECTIFICATION_VIDEO', 'RECTIFICATION_ATTACHMENT'].includes(row.attachmentType)"
              link
              type="danger"
              @click="removeFile(row)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <div v-if="editable" class="submit-row">
        <el-button size="large" :loading="saving" @click="save(true)">保存草稿</el-button
        ><el-button type="primary" size="large" :loading="submitting" @click="submit">提交隐患整改</el-button>
      </div>
    </section>
    <el-dialog v-model="previewVisible" title="材料预览" width="900px"
      ><img
        v-if="previewUrl && previewType === 'image'"
        :src="previewUrl"
        alt="材料预览"
        style="display: block; max-width: 100%; max-height: 650px; margin: auto" /><iframe
        v-else-if="previewUrl"
        :src="previewUrl"
        title="PDF预览"
        style="width: 100%; height: 650px; border: 0"
    /></el-dialog>
  </div>
</template>
