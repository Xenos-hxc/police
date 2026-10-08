<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { archiveApi, fileApi } from '@/api'
import { authenticatedFileUrl, authenticatedNativeDownload } from '@/utils/download'
import { formatDateTime, quarterLabel } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import type { ArchiveHistoryItem, ArchiveHistoryProgress, ArchiveRecord, Attachment, TagType } from '@/types'

type ArchiveType = 'police-stations' | 'key-units' | 'important-parts'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const archiveType = computed(() => String(route.params.type) as ArchiveType)
const archiveId = computed(() => Number(route.params.id))
const loading = ref(false)
const archive = ref<Partial<ArchiveRecord>>({})
const progress = ref<Partial<ArchiveHistoryProgress>>({})
const items = ref<ArchiveHistoryItem[]>([])
const businessHistoryVisible = ref(false)
const previewVisible = ref(false)
const previewUrl = ref('')
const previewType = ref<'image' | 'pdf'>('image')

const now = new Date()
const query = reactive({
  year: Number(route.query.year || now.getFullYear()),
  quarter: Number(route.query.quarter || Math.floor(now.getMonth() / 3) + 1),
  startDate: '',
  endDate: ''
})
const periodInfo = computed(
  () =>
    auth.systemPeriod || {
      startYear: 2026,
      startQuarter: 3,
      currentYear: now.getFullYear(),
      currentQuarter: Math.floor(now.getMonth() / 3) + 1,
      effectiveYear: now.getFullYear(),
      effectiveQuarter: Math.floor(now.getMonth() / 3) + 1
    }
)
const quarterOptions = computed(() => {
  const maximum = query.year === periodInfo.value.effectiveYear ? periodInfo.value.effectiveQuarter : 4
  return Array.from({ length: maximum }, (_, index) => index + 1).filter(
    (quarter) =>
      query.year > periodInfo.value.startYear ||
      (query.year === periodInfo.value.startYear && quarter >= periodInfo.value.startQuarter)
  )
})
const handleYearChange = () => {
  if (!quarterOptions.value.includes(query.quarter)) query.quarter = quarterOptions.value[0]
}

const archiveTitles: Record<ArchiveType, string> = {
  'police-stations': '派出所档案',
  'key-units': '重点单位档案',
  'important-parts': '重要部位档案'
}

const targetTypeMap: Record<string, string> = {
  STATION: '派出所',
  KEY_UNIT: '重点单位',
  IMPORTANT_PART: '重要部位'
}

const statusMap: Record<string, { label: string; type: TagType }> = {
  PENDING: { label: '未开始', type: 'info' },
  APPROVED: { label: '已完成', type: 'success' },
  OVERDUE: { label: '已逾期', type: 'danger' },
  OVERDUE_SUBMITTED: { label: '逾期完成', type: 'warning' }
}

const fieldMap: Record<ArchiveType, Array<[keyof ArchiveRecord, string]>> = {
  'police-stations': [
    ['stationName', '派出所名称'],
    ['remark', '备注']
  ],
  'key-units': [
    ['unitName', '重点单位名称'],
    ['unitType', '单位类型'],
    ['establishedDate', '确定时间'],
    ['leader', '负责人'],
    ['contactPerson', '联络员'],
    ['phone', '联系电话'],
    ['address', '地址'],
    ['bureauName', '管辖公安处'],
    ['jurisdictionText', '源表管辖信息'],
    ['stationNames', '管辖派出所'],
    ['remark', '备注']
  ],
  'important-parts': [
    ['partName', '重要部位名称'],
    ['partType', '部位类型'],
    ['establishedDate', '确立时间'],
    ['removedDate', '撤销时间'],
    ['guardStatus', '值守情况'],
    ['lengthDescription', '桥隧全长'],
    ['railwayLine', '线别'],
    ['kilometerMark', '公里数'],
    ['location', '所处地域'],
    ['responsibleUnit', '责任单位（站段）'],
    ['workshop', '责任单位（车间）'],
    ['bureauName', '管辖公安处'],
    ['jurisdictionText', '源表管辖信息'],
    ['stationNames', '管辖派出所'],
    ['remark', '备注']
  ]
}

const titleName = computed(() => {
  const data = archive.value
  return data.stationName || data.unitName || data.partName || archiveTitles[archiveType.value] || '档案详情'
})

const load = async () => {
  loading.value = true
  try {
    const response = await archiveApi.historyDetail(archiveType.value, archiveId.value, query)
    archive.value = response.data.archive || {}
    progress.value = response.data.progress || {}
    items.value = response.data.items || []
    businessHistoryVisible.value = response.data.businessHistoryVisible === true
  } finally {
    loading.value = false
  }
}

const formatFileSize = (size?: number) => {
  const value = Number(size || 0)
  if (value >= 1024 * 1024 * 1024) return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MB`
  if (value >= 1024) return `${(value / 1024).toFixed(2)} KB`
  return `${value} B`
}

const isImage = (file: Attachment) => ['jpg', 'jpeg', 'png'].includes(String(file.extension || '').toLowerCase())
const isVideo = (file: Attachment) => ['mp4', 'mov', 'avi'].includes(String(file.extension || '').toLowerCase())
const isPreviewable = (file: Attachment) => isImage(file) || String(file.extension || '').toLowerCase() === 'pdf'

const preview = async (file: Attachment) => {
  if (!isPreviewable(file)) {
    ElMessage.info('该文件类型不支持在线预览，请下载后查看')
    return
  }
  previewType.value = isImage(file) ? 'image' : 'pdf'
  closePreview()
  previewUrl.value = await authenticatedFileUrl(fileApi.previewUrl(file.id))
  previewVisible.value = true
}

const download = (file: Attachment) => authenticatedNativeDownload(fileApi.downloadUrl(file.id), file.originalName)
const attachmentTypeLabels: Record<string, string> = {
  VIDEO: '检查视频',
  RECORD: '检查笔录',
  HAZARD_MATERIAL: '隐患材料',
  NOTICE: '责令改正通知书',
  RECTIFICATION_VIDEO: '整改视频',
  RECTIFICATION_ATTACHMENT: '整改材料',
  PHOTO: '历史图片',
  OTHER: '历史附件'
}
const attachmentTypeOrder = [
  'VIDEO',
  'RECORD',
  'HAZARD_MATERIAL',
  'NOTICE',
  'RECTIFICATION_VIDEO',
  'RECTIFICATION_ATTACHMENT',
  'PHOTO',
  'OTHER'
]
const fileTypeLabel = (type: string) => attachmentTypeLabels[type] || attachmentTypeLabels.OTHER
const attachmentGroups = (files?: Attachment[]) => {
  const grouped = new Map<string, Attachment[]>()
  const sourceFiles = files || []
  sourceFiles.forEach((file) => {
    const sourceType = String(file.attachmentType || 'OTHER')
    const type = attachmentTypeLabels[sourceType] ? sourceType : 'OTHER'
    const group = grouped.get(type) || []
    group.push(file)
    grouped.set(type, group)
  })
  return attachmentTypeOrder
    .filter((type) => grouped.has(type))
    .map((type) => ({ type, label: fileTypeLabel(type), files: grouped.get(type) || [] }))
}
const dangerTypeLabel = (type?: string) => ({ DEADLINE: '期限改', ROUTINE: '例行改' })[String(type || '')] || '-'
const archiveValue = (key: keyof ArchiveRecord) => {
  const value = archive.value[key]
  if (Array.isArray(value)) return value.length ? value.join('、') : '-'
  return value || '-'
}

const closePreview = () => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

onMounted(async () => {
  await auth.fetchSystemPeriod()
  const afterStart =
    query.year > periodInfo.value.startYear ||
    (query.year === periodInfo.value.startYear && query.quarter >= periodInfo.value.startQuarter)
  const beforeMaximum =
    query.year < periodInfo.value.effectiveYear ||
    (query.year === periodInfo.value.effectiveYear && query.quarter <= periodInfo.value.effectiveQuarter)
  if (!afterStart || !beforeMaximum) {
    query.year = periodInfo.value.effectiveYear
    query.quarter = periodInfo.value.effectiveQuarter
  }
  load()
})
onBeforeUnmount(closePreview)
</script>

<template>
  <div v-loading="loading" class="page archive-history-page">
    <div class="page-heading compact">
      <div>
        <h2>{{ titleName }}</h2>
        <p>{{ archiveTitles[archiveType] }} · 检查进度与历史材料</p>
      </div>
      <div>
        <el-button @click="router.back()">返回</el-button>
      </div>
    </div>

    <section v-if="businessHistoryVisible" class="panel">
      <div class="panel-title">
        <b>当前季度检查进度</b>
        <span>{{ progress.year }} 年 {{ quarterLabel(progress.quarter) }}</span>
      </div>
      <div class="mini-metrics">
        <div>
          <span>应检查任务</span><b>{{ progress.total || 0 }}</b>
        </div>
        <div>
          <span>已完成</span><b>{{ progress.completed || 0 }}</b>
        </div>
        <div>
          <span>未完成</span><b>{{ progress.unfinished || 0 }}</b>
        </div>
        <div class="danger">
          <span>逾期未完成</span><b>{{ progress.overdue || 0 }}</b>
        </div>
        <div class="warning">
          <span>逾期完成</span><b>{{ progress.overdueCompleted || 0 }}</b>
        </div>
      </div>
      <el-progress
        :percentage="Number(progress.rate || 0)"
        :stroke-width="18"
        :status="Number(progress.rate || 0) >= 100 ? 'success' : undefined"
      />
    </section>

    <section class="panel">
      <div class="panel-title"><b>档案详细信息</b><span>基础档案与联系方式</span></div>
      <el-descriptions :column="2" border>
        <el-descriptions-item v-for="field in fieldMap[archiveType]" :key="field[0]" :label="field[1]">
          {{ archiveValue(field[0]) }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ archive.status === 1 ? '正常' : '停用' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(archive.createTime) || '-' }}</el-descriptions-item>
      </el-descriptions>
    </section>

    <section v-if="businessHistoryVisible" class="panel">
      <div class="panel-title">
        <b>检查历史与上传材料</b>
        <span>按年度、季度和时间范围查询</span>
      </div>
      <div class="filter-bar">
        <el-input-number
          v-model="query.year"
          :min="periodInfo.startYear"
          :max="periodInfo.effectiveYear"
          style="width: 120px"
          @change="handleYearChange"
        />
        <el-select v-model="query.quarter" placeholder="季度" style="width: 130px">
          <el-option v-for="n in quarterOptions" :key="n" :label="quarterLabel(n)" :value="n" />
        </el-select>
        <el-date-picker
          v-model="query.startDate"
          value-format="YYYY-MM-DD"
          placeholder="开始日期"
          style="width: 150px"
        />
        <el-date-picker v-model="query.endDate" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 150px" />
        <el-button type="primary" @click="load"
          ><el-icon><Search /></el-icon> 查询</el-button
        >
      </div>

      <el-empty v-if="!items.length" description="暂无检查记录" />
      <el-collapse v-else>
        <el-collapse-item v-for="item in items" :key="item.task.id" :name="item.task.id">
          <template #title>
            <div class="history-title">
              <b>{{ item.executorDeptName }} · {{ item.task.taskYear }} 年 {{ quarterLabel(item.task.quarter) }}</b>
              <el-tag size="small" :type="statusMap[item.task.status]?.type">
                {{ statusMap[item.task.status]?.label || '未知状态' }}
              </el-tag>
            </div>
          </template>

          <el-descriptions :column="3" border class="history-desc">
            <el-descriptions-item label="检查部门">{{ item.executorDeptName || '未知检查部门' }}</el-descriptions-item>
            <el-descriptions-item label="检查对象">{{ item.task.targetName }}</el-descriptions-item>
            <el-descriptions-item label="对象类型">{{
              targetTypeMap[item.task.targetType] || item.task.targetType
            }}</el-descriptions-item>
            <el-descriptions-item label="统计周期"
              >{{ item.task.taskYear }} 年 {{ quarterLabel(item.task.quarter) }}</el-descriptions-item
            >
            <el-descriptions-item label="截止时间">{{ formatDateTime(item.task.deadline) }}</el-descriptions-item>
            <el-descriptions-item label="是否逾期">{{ item.task.overdue ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{
              formatDateTime(item.record?.submittedTime) || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="检查时间">{{
              formatDateTime(item.record?.checkTime) || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="检查人员">{{ item.record?.inspectors || '-' }}</el-descriptions-item>
            <el-descriptions-item label="隐患结论">{{
              Number(item.record?.hasDanger) === 1 ? '有隐患' : '无隐患'
            }}</el-descriptions-item>
            <template v-if="Number(item.record?.hasDanger) === 1">
              <el-descriptions-item label="整改类型">{{
                dangerTypeLabel(item.record?.rectificationType)
              }}</el-descriptions-item>
              <el-descriptions-item label="整改期限">{{
                item.record?.rectificationDeadline || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="隐患详情" :span="3">{{
                item.record?.dangerDetail || '-'
              }}</el-descriptions-item>
            </template>
            <el-descriptions-item label="备注" :span="3">{{
              item.record?.remark || item.task.remark || '-'
            }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="item.attachments?.length" class="attachment-groups">
            <section v-for="group in attachmentGroups(item.attachments)" :key="group.type" class="attachment-group">
              <div class="attachment-group-title">
                <b>{{ group.label }}</b>
                <el-tag size="small" type="info">{{ group.files.length }} 份</el-tag>
              </div>
              <div class="attachment-list">
                <div v-for="file in group.files" :key="file.id" class="attachment-card">
                  <div class="attachment-icon" :class="{ video: isVideo(file), image: isImage(file) }">
                    <el-icon v-if="isVideo(file)"><VideoCamera /></el-icon>
                    <el-icon v-else-if="isImage(file)"><Picture /></el-icon>
                    <el-icon v-else><Document /></el-icon>
                  </div>
                  <div class="attachment-meta">
                    <b>{{ file.originalName }}</b>
                    <span>{{ formatFileSize(file.fileSize) }} · {{ formatDateTime(file.createTime) }}</span>
                  </div>
                  <el-button v-if="isPreviewable(file)" link type="primary" @click="preview(file)">预览</el-button>
                  <el-button link @click="download(file)">下载</el-button>
                </div>
              </div>
            </section>
          </div>
          <el-empty v-else description="暂无上传的检查或整改材料" :image-size="80" />
        </el-collapse-item>
      </el-collapse>
    </section>

    <el-dialog v-model="previewVisible" title="附件预览" width="900px" @closed="closePreview">
      <img
        v-if="previewUrl && previewType === 'image'"
        :src="previewUrl"
        alt="附件预览"
        style="display: block; max-width: 100%; max-height: 650px; margin: auto"
      />
      <iframe v-else-if="previewUrl" :src="previewUrl" title="PDF预览" style="width: 100%; height: 650px; border: 0" />
    </el-dialog>
  </div>
</template>

<style scoped>
.archive-history-page .mini-metrics {
  grid-template-columns: repeat(5, 1fr);
  margin-bottom: 16px;
}

.archive-history-page .mini-metrics .warning b {
  color: #d88a21;
}

.history-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.history-title b {
  color: #183f6b;
}

.history-title span {
  color: #40566f;
}

.history-desc {
  margin-bottom: 14px;
}

.attachment-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.attachment-group {
  padding: 12px;
  border: 1px solid #dce6f1;
  border-radius: 5px;
  background: #fff;
}

.attachment-group-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  color: #183f6b;
}

.attachment-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.attachment-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 5px;
  background: #f8fbff;
}

.attachment-icon {
  width: 42px;
  height: 42px;
  border-radius: 4px;
  display: grid;
  place-items: center;
  color: #315b87;
  background: #eaf2fc;
  font-size: 22px;
  flex: 0 0 auto;
}

.attachment-icon.video {
  color: #1f7a68;
  background: #e8f6f2;
}

.attachment-icon.image {
  color: #b46b18;
  background: #fff3e3;
}

.attachment-meta {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.attachment-meta b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #243c58;
}

.attachment-meta span {
  color: #7b8999;
  font-size: 12px;
}
</style>
