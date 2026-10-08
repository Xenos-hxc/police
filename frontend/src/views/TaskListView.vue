<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskApi } from '@/api'
import type { CheckTask, SelectionPool, SelectionStatus, TagType } from '@/types'
import { saveBlob } from '@/utils/download'
import { formatDateTime, periodLabel, quarterLabel } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import TargetTypeSwitch from '@/components/TargetTypeSwitch.vue'

const route = useRoute()

interface TaskQuery extends Record<string, unknown> {
  page: number
  size: number
  status: string
  targetName: string
  year: number
  quarter: number
  overdue?: number
  targetType: string
  completion: string
}
const auth = useAuthStore()
const loading = ref(false)
const selectionLoading = ref(false)
const rows = ref<CheckTask[]>([])
const total = ref(0)
const taskSummary = ref({ totalCount: 0, completedCount: 0, unfinishedCount: 0, completionRate: 0 })
const selectionStatuses = ref<SelectionStatus[]>([])
const selectionPool = ref<SelectionPool>()
const selectedTargetIds = ref<number[]>([])
const selectionMode = ref<'MANUAL' | 'RANDOM'>('MANUAL')
const selectionVisible = ref(false)
const currentDate = new Date()
const query = reactive<TaskQuery>({
  page: 1,
  size: 10,
  status: '',
  targetName: '',
  year: currentDate.getFullYear(),
  quarter: Math.floor(currentDate.getMonth() / 3) + 1,
  overdue: undefined,
  targetType: '',
  completion: ''
})
const periodInfo = computed(
  () =>
    auth.systemPeriod || {
      startYear: 2026,
      startQuarter: 3,
      currentYear: currentDate.getFullYear(),
      currentQuarter: Math.floor(currentDate.getMonth() / 3) + 1,
      effectiveYear: currentDate.getFullYear(),
      effectiveQuarter: Math.floor(currentDate.getMonth() / 3) + 1
    }
)
const quarterOptions = computed(() =>
  Array.from({ length: 4 }, (_, index) => index + 1).filter(
    (quarter) =>
      query.year > periodInfo.value.startYear ||
      (query.year === periodInfo.value.startYear && quarter >= periodInfo.value.startQuarter)
  )
)
const applyEffectivePeriod = () => {
  query.year = periodInfo.value.effectiveYear
  query.quarter = periodInfo.value.effectiveQuarter
}
const handleYearChange = () => {
  if (!quarterOptions.value.includes(query.quarter)) query.quarter = quarterOptions.value[0]
}

const statusMap: Record<string, { label: string; type: TagType }> = {
  PENDING: { label: '未开始', type: 'info' },
  APPROVED: { label: '已完成', type: 'success' },
  OVERDUE: { label: '已逾期', type: 'danger' },
  OVERDUE_SUBMITTED: { label: '逾期完成', type: 'warning' }
}

const targetTypeMap: Record<string, string> = {
  STATION: '派出所',
  KEY_UNIT: '重点单位',
  IMPORTANT_PART: '重要部位'
}

const activeSelectionStatus = computed(() =>
  selectionStatuses.value.find((item) => item.targetType === query.targetType)
)
const targetSwitchOptions = computed(() =>
  selectionStatuses.value.map((item) => ({
    value: item.targetType,
    label: item.targetLabel,
    unfinishedCount: Math.max(0, Number(item.requiredCount || 0) - Number(item.submittedCount || 0))
  }))
)

const parseDateTime = (value?: string | null) => (value ? new Date(String(value).replace(' ', 'T')).getTime() : NaN)
const beforeDeadline = (value?: string | null) => {
  const deadline = parseDateTime(value)
  return Number.isFinite(deadline) && Date.now() <= deadline
}
const quarterStart = (year?: number, quarter?: number) => {
  if (!year || !quarter) return NaN
  return new Date(year, (quarter - 1) * 3, 1).getTime()
}
const isFutureTask = (row: CheckTask) => {
  const start = quarterStart(row.taskYear, row.quarter)
  return Number.isFinite(start) && Date.now() < start
}
const isElapsedQueryPeriod = () => {
  const end = new Date(Number(query.year), Number(query.quarter) * 3, 0, 23, 59, 59).getTime()
  return Number.isFinite(end) && Date.now() > end
}

const actionLabel = (row: CheckTask) => {
  let originalLabel = '提交'
  if (isFutureTask(row)) return '查看'
  if (row.status === 'OVERDUE_SUBMITTED') return '查看'
  if (row.status === 'APPROVED') originalLabel = beforeDeadline(row.deadline) ? '编辑' : '查看'
  return originalLabel === '提交' ? '编辑' : '查看'
}

const load = async () => {
  loading.value = true
  try {
    const params = { ...query, checkType: 'INTERNAL_SECURITY' }
    const summaryParams = { ...params, status: undefined, completion: undefined, page: undefined, size: undefined }
    const [response, summaryResponse] = await Promise.all([taskApi.list(params), taskApi.summary(summaryParams)])
    rows.value = response.data.records
    total.value = response.data.total
    taskSummary.value = summaryResponse.data
  } finally {
    loading.value = false
  }
}

const loadSelection = async () => {
  selectionLoading.value = true
  try {
    if (!isElapsedQueryPeriod()) {
      await taskApi.initializePeriod({ year: query.year, quarter: query.quarter })
    }
    const response = await taskApi.selectionStatus({ year: query.year, quarter: query.quarter })
    selectionStatuses.value = response.data || []
    if (!query.targetType && selectionStatuses.value.length) {
      query.targetType = selectionStatuses.value[0].targetType
    }
    selectionVisible.value = Boolean(
      activeSelectionStatus.value?.initialized &&
      activeSelectionStatus.value?.needSelection &&
      !activeSelectionStatus.value?.fullCoverage
    )
    if (activeSelectionStatus.value?.initialized) await loadPool()
    else selectionPool.value = undefined
  } finally {
    selectionLoading.value = false
  }
}

const loadCurrentTypeSelection = async () => {
  selectionLoading.value = true
  try {
    if (activeSelectionStatus.value?.initialized) await loadPool()
    selectionVisible.value = Boolean(
      activeSelectionStatus.value?.needSelection && !activeSelectionStatus.value?.fullCoverage
    )
  } finally {
    selectionLoading.value = false
  }
}

const loadPool = async () => {
  if (!query.targetType) return
  const response = await taskApi.selectionPool({
    targetType: query.targetType,
    year: query.year,
    quarter: query.quarter
  })
  selectionPool.value = response.data
  selectedTargetIds.value = (response.data.targets || []).filter((item) => item.selected).map((item) => item.targetId)
}

const switchTargetType = async (value: string) => {
  query.targetType = value
  query.page = 1
  await Promise.all([loadCurrentTypeSelection(), load()])
}

const randomPreviewSelection = () => {
  if (!selectionPool.value) return
  const need = Number(selectionPool.value.remainingNeed || 0)
  const targets = [...(selectionPool.value.targets || [])]
  if (!need) {
    selectedTargetIds.value = []
    return ElMessage.info('当前已提交数量已满足本季度筛选要求')
  }
  if (need > targets.length) {
    return ElMessage.warning('可筛选对象不足')
  }
  for (let i = targets.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[targets[i], targets[j]] = [targets[j], targets[i]]
  }
  selectedTargetIds.value = targets.slice(0, need).map((item) => item.targetId)
  selectionMode.value = 'RANDOM'
  selectionVisible.value = true
  ElMessage.success('已生成随机筛选结果，请确认后点击保存')
}

const saveSelection = async () => {
  if (!query.targetType) return
  selectionLoading.value = true
  try {
    const response = await taskApi.selectionSave({
      targetType: query.targetType,
      year: query.year,
      quarter: query.quarter,
      mode: selectionMode.value,
      targetIds: selectedTargetIds.value
    })
    selectionPool.value = response.data
    selectedTargetIds.value = (response.data.targets || []).filter((item) => item.selected).map((item) => item.targetId)
    ElMessage.success('筛选结果已保存')
    selectionMode.value = 'MANUAL'
    await loadSelection()
    await load()
  } finally {
    selectionLoading.value = false
  }
}

const clearSelection = async () => {
  if (!query.targetType) return
  try {
    await ElMessageBox.confirm(
      '仅清空当前季度尚未完成的筛选结果，已完成和逾期完成任务会保留。是否继续？',
      '清空筛选结果',
      { type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  selectionLoading.value = true
  try {
    await taskApi.selectionClear({
      targetType: query.targetType,
      year: query.year,
      quarter: query.quarter
    })
    selectedTargetIds.value = []
    selectionMode.value = 'MANUAL'
    ElMessage.success('未完成的筛选结果已清空')
    await loadSelection()
    await load()
  } finally {
    selectionLoading.value = false
  }
}

const applyRoutePreset = () => {
  query.overdue = route.meta.overdue as number | undefined
  query.page = 1
}

const reset = () => {
  Object.assign(query, {
    targetName: '',
    status: '',
    targetType: '',
    completion: '',
    year: periodInfo.value.effectiveYear,
    quarter: periodInfo.value.effectiveQuarter,
    page: 1
  })
  loadSelection().then(load)
}

const filterCompletion = (value: string) => {
  query.completion = query.completion === value ? '' : value
  query.status = ''
  query.page = 1
  load()
}

const exportFile = async () => {
  saveBlob(await taskApi.export({ ...query, checkType: 'INTERNAL_SECURITY' }), '内部治安保卫工作监督检查任务.xlsx')
}

const search = async () => {
  query.page = 1
  await loadSelection()
  await load()
}

watch(
  () => route.path,
  () => {
    applyRoutePreset()
    search()
  }
)
onMounted(async () => {
  await auth.fetchSystemPeriod()
  applyEffectivePeriod()
  applyRoutePreset()
  search()
})
</script>

<template>
  <div class="page">
    <div class="page-heading compact">
      <div>
        <h2>内部治安保卫工作监督检查任务</h2>
        <p>公安处分别检查派出所、重点单位和重要部位；派出所分别检查本辖区重点单位和重要部位。</p>
      </div>
      <div>
        <el-button @click="exportFile"
          ><el-icon><Download /></el-icon> 导出</el-button
        >
      </div>
    </div>

    <section class="panel">
      <TargetTypeSwitch
        v-if="selectionStatuses.length > 1"
        :model-value="query.targetType"
        :options="targetSwitchOptions"
        @change="switchTargetType"
      />

      <div class="task-progress-overview">
        <div class="task-progress-count completed">
          <span>已完成</span><b>{{ taskSummary.completedCount }}</b>
        </div>
        <div class="task-progress-count unfinished">
          <span>未完成</span><b>{{ taskSummary.unfinishedCount }}</b>
        </div>
        <div class="task-progress-bar">
          <div>
            <span>当前季度完成进度</span><b>{{ taskSummary.completionRate }}%</b>
          </div>
          <el-progress :percentage="taskSummary.completionRate" :stroke-width="12" :show-text="false" />
        </div>
      </div>

      <div
        v-if="activeSelectionStatus && !activeSelectionStatus.fullCoverage"
        v-loading="selectionLoading"
        class="selection-strip"
      >
        <el-alert :type="activeSelectionStatus.needSelection ? 'warning' : 'success'" :closable="false" show-icon>
          <template #title>
            {{ activeSelectionStatus.targetLabel }}筛选： 应筛选 {{ activeSelectionStatus.requiredCount }} 个， 已提交
            {{ activeSelectionStatus.submittedCount }} 个， 已筛选未提交 {{ activeSelectionStatus.selectedCount }} 个，
            还需筛选 {{ activeSelectionStatus.remainingNeed }} 个。 已提交任务不会被筛选移除。
          </template>
        </el-alert>
        <div class="selection-actions">
          <el-button @click="selectionVisible = !selectionVisible">{{
            selectionVisible ? '收起筛选' : '修改筛选'
          }}</el-button>
          <el-button type="danger" plain :disabled="!activeSelectionStatus.selectedCount" @click="clearSelection"
            >清空筛选结果</el-button
          >
          <el-button type="primary" :disabled="!activeSelectionStatus.remainingNeed" @click="randomPreviewSelection"
            >随机筛选</el-button
          >
        </div>
      </div>

      <div v-if="selectionVisible && selectionPool" v-loading="selectionLoading" class="selection-panel-inline">
        <div class="selection-summary">
          <span>可筛选 {{ selectionPool.selectableCount }} 个</span>
          <span>本次需选择 {{ selectionPool.remainingNeed }} 个</span>
          <span>当前已选 {{ selectedTargetIds.length }} 个</span>
        </div>
        <el-scrollbar height="260px">
          <el-checkbox-group v-model="selectedTargetIds" class="selection-target-grid">
            <el-checkbox
              v-for="item in selectionPool.targets"
              :key="item.targetId"
              :value="item.targetId"
              @change="selectionMode = 'MANUAL'"
            >
              {{ item.targetName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-scrollbar>
        <div class="selection-save-row">
          <el-button type="primary" @click="saveSelection">保存筛选结果</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="query.targetName" clearable placeholder="检查对象" style="width: 220px" />
        <el-select
          v-model="query.status"
          clearable
          placeholder="任务状态"
          style="width: 140px"
          @change="query.completion = ''"
        >
          <el-option v-for="(item, value) in statusMap" :key="value" :label="item.label" :value="value" />
        </el-select>
        <el-input-number
          v-model="query.year"
          :min="periodInfo.startYear"
          :max="periodInfo.currentYear + 2"
          placeholder="年度"
          style="width: 120px"
          @change="handleYearChange"
        />
        <el-select v-model="query.quarter" placeholder="季度" style="width: 130px">
          <el-option v-for="n in quarterOptions" :key="n" :label="quarterLabel(n)" :value="n" />
        </el-select>
        <el-button type="primary" @click="search"
          ><el-icon><Search /></el-icon> 查询</el-button
        >
        <el-button @click="reset">重置</el-button>
        <el-button
          :type="query.completion === 'COMPLETED' ? 'success' : 'default'"
          @click="filterCompletion('COMPLETED')"
          >已完成</el-button
        >
        <el-button
          :type="query.completion === 'UNFINISHED' ? 'warning' : 'default'"
          @click="filterCompletion('UNFINISHED')"
          >未完成</el-button
        >
      </div>

      <el-table v-loading="loading" :data="rows" stripe table-layout="auto">
        <el-table-column label="对象类型" width="120">
          <template #default="{ row }">{{ targetTypeMap[row.targetType] || row.targetType }}</template>
        </el-table-column>
        <el-table-column prop="targetName" label="检查对象" min-width="220" show-overflow-tooltip />
        <el-table-column label="周期" width="160">
          <template #default="{ row }">{{ periodLabel(row.taskYear, row.quarter) }}</template>
        </el-table-column>
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="逾期" width="80">
          <template #default="{ row }">
            <el-tag :type="row.overdue ? 'danger' : 'success'">{{ row.overdue ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type">{{ statusMap[row.status]?.label || '未知状态' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/tasks/${row.id}`)">{{ actionLabel(row) }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </section>
  </div>
</template>
