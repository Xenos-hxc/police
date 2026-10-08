<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { stationInspectionApi } from '@/api'
import TargetTypeSwitch from '@/components/TargetTypeSwitch.vue'
import { formatDateTime, quarterLabel } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import type { InspectionItem, QuarterProgress, StationInfo, TagType, TargetProgress } from '@/types'

interface StationInspectionQuery extends Record<string, unknown> {
  year: number
  quarter: number
  targetType: string
  targetName: string
  status: string
  page: number
  size: number
}

const emptyProgress = (): TargetProgress => ({
  initialized: false,
  requiredCount: 0,
  completedCount: 0,
  unfinishedCount: 0,
  overdueCount: 0,
  completionRate: 0
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const stationDeptId = Number(route.params.deptId)
const loading = ref(false)
const station = ref<Partial<StationInfo>>({})
const progress = ref<Partial<QuarterProgress>>({ keyUnit: emptyProgress(), importantPart: emptyProgress() })
const rows = ref<InspectionItem[]>([])
const total = ref(0)
const query = reactive<StationInspectionQuery>({
  year: Number(route.query.year) || new Date().getFullYear(),
  quarter: Number(route.query.quarter) || Math.floor(new Date().getMonth() / 3) + 1,
  targetType: 'KEY_UNIT',
  targetName: '',
  status: '',
  page: 1,
  size: 10
})
const periodInfo = computed(
  () =>
    auth.systemPeriod || {
      startYear: 2026,
      startQuarter: 3,
      currentYear: new Date().getFullYear(),
      currentQuarter: Math.floor(new Date().getMonth() / 3) + 1,
      effectiveYear: new Date().getFullYear(),
      effectiveQuarter: Math.floor(new Date().getMonth() / 3) + 1
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

const statusMap: Record<string, { label: string; type: TagType }> = {
  PENDING: { label: '未开始', type: 'info' },
  APPROVED: { label: '已完成', type: 'success' },
  OVERDUE: { label: '已逾期', type: 'danger' },
  OVERDUE_SUBMITTED: { label: '逾期完成', type: 'warning' }
}
const dangerTypeMap: Record<string, string> = { DEADLINE: '期限改', ROUTINE: '例行改' }
const targetTypeMap: Record<string, string> = { KEY_UNIT: '重点单位', IMPORTANT_PART: '重要部位' }
const targetSwitchOptions = computed(() => [
  { value: 'KEY_UNIT', label: '重点单位', unfinishedCount: Number(progress.value.keyUnit?.unfinishedCount || 0) },
  {
    value: 'IMPORTANT_PART',
    label: '重要部位',
    unfinishedCount: Number(progress.value.importantPart?.unfinishedCount || 0)
  }
])
const activeProgress = computed<TargetProgress>(() =>
  query.targetType === 'KEY_UNIT'
    ? progress.value.keyUnit || emptyProgress()
    : progress.value.importantPart || emptyProgress()
)

const load = async () => {
  loading.value = true
  try {
    const response = await stationInspectionApi.detail(stationDeptId, query)
    station.value = response.data.station || {}
    progress.value = response.data.progress || { keyUnit: {}, importantPart: {} }
    rows.value = response.data.tasks?.records || []
    total.value = response.data.tasks?.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.page = 1
  load()
}

const switchTargetType = () => {
  query.page = 1
  query.targetName = ''
  query.status = ''
  load()
}

const reset = () => {
  Object.assign(query, { targetName: '', status: '', page: 1 })
  load()
}

const openTask = (taskId: number) => router.push(`/tasks/${taskId}`)

onMounted(async () => {
  await auth.fetchSystemPeriod()
  const routeYear = Number(route.query.year)
  const routeQuarter = Number(route.query.quarter)
  const afterStart =
    routeYear > periodInfo.value.startYear ||
    (routeYear === periodInfo.value.startYear && routeQuarter >= periodInfo.value.startQuarter)
  const beforeMaximum =
    routeYear < periodInfo.value.effectiveYear ||
    (routeYear === periodInfo.value.effectiveYear && routeQuarter <= periodInfo.value.effectiveQuarter)
  if (!afterStart || !beforeMaximum) {
    query.year = periodInfo.value.effectiveYear
    query.quarter = periodInfo.value.effectiveQuarter
  }
  load()
})
</script>

<template>
  <div class="page station-inspection-detail">
    <div class="page-heading compact">
      <div>
        <h2>{{ station.stationName || '派出所检查详情' }}</h2>
        <p>{{ query.year }} 年 {{ quarterLabel(query.quarter) }}重点单位和重要部位检查进度及检查内容。</p>
      </div>
      <el-button @click="router.push('/station-inspections')">返回各所检查详情</el-button>
    </div>

    <section class="panel">
      <div class="filter-bar">
        <el-input-number
          v-model="query.year"
          :min="periodInfo.startYear"
          :max="periodInfo.effectiveYear"
          style="width: 140px"
          @change="handleYearChange"
        />
        <el-select v-model="query.quarter" style="width: 140px">
          <el-option v-for="quarter in quarterOptions" :key="quarter" :label="quarterLabel(quarter)" :value="quarter" />
        </el-select>
        <el-button type="primary" @click="search">查询季度</el-button>
      </div>
      <TargetTypeSwitch v-model="query.targetType" :options="targetSwitchOptions" @change="switchTargetType" />
      <div class="station-progress-strip">
        <span
          >应完成 <b>{{ activeProgress.requiredCount || 0 }}</b></span
        >
        <span
          >已完成 <b class="completed">{{ activeProgress.completedCount || 0 }}</b></span
        >
        <span
          >未完成 <b class="unfinished">{{ activeProgress.unfinishedCount || 0 }}</b></span
        >
        <span
          >已逾期 <b class="overdue">{{ activeProgress.overdueCount || 0 }}</b></span
        >
        <div class="progress-main">
          <span>完成率</span>
          <el-progress :percentage="activeProgress.completionRate || 0" />
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-title">
        <b>{{ targetTypeMap[query.targetType] }}检查明细</b>
        <span>展开一行可直接查看该项检查记录内容</span>
      </div>
      <div class="filter-bar">
        <el-input
          v-model="query.targetName"
          clearable
          placeholder="检查对象名称"
          style="width: 260px"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" clearable placeholder="任务状态" style="width: 150px">
          <el-option v-for="(item, value) in statusMap" :key="value" :label="item.label" :value="value" />
        </el-select>
        <el-button type="primary" @click="search"
          ><el-icon><Search /></el-icon> 查询</el-button
        >
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe table-layout="auto">
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="inspection-content">
              <el-descriptions v-if="row.record" :column="3" border>
                <el-descriptions-item label="检查时间">{{
                  formatDateTime(row.record.checkTime) || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="检查人员">{{ row.record.inspectors || '-' }}</el-descriptions-item>
                <el-descriptions-item label="隐患结论">{{
                  Number(row.record.hasDanger) === 1 ? '有隐患' : '无隐患'
                }}</el-descriptions-item>
                <template v-if="Number(row.record.hasDanger) === 1">
                  <el-descriptions-item label="整改类型">{{
                    dangerTypeMap[row.record.rectificationType] || '-'
                  }}</el-descriptions-item>
                  <el-descriptions-item label="整改期限">{{
                    row.record.rectificationDeadline || '-'
                  }}</el-descriptions-item>
                  <el-descriptions-item label="隐患详情">{{ row.record.dangerDetail || '-' }}</el-descriptions-item>
                </template>
                <el-descriptions-item label="提交时间">{{
                  formatDateTime(row.record.submittedTime) || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="备注" :span="2">{{
                  row.record.remark || row.task.remark || '-'
                }}</el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="该任务尚未填写检查记录" :image-size="70" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="检查对象" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.task.targetName }}</template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }"
            ><el-tag :type="statusMap[row.task.status]?.type">{{
              statusMap[row.task.status]?.label || '未知状态'
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="检查时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.record?.checkTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="检查人员" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.record?.inspectors || '-' }}</template>
        </el-table-column>
        <el-table-column label="检查材料" width="100">
          <template #default="{ row }">{{ row.attachmentCount }} 份</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }"
            ><el-button link type="primary" @click="openTask(row.task.id)">查看检查资料</el-button></template
          >
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

<style scoped>
.station-progress-strip {
  display: grid;
  grid-template-columns: repeat(4, 120px) minmax(260px, 1fr);
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid #dce6f1;
  border-radius: 5px;
  background: #f8fbff;
  color: #65768a;
}
.station-progress-strip > span {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.station-progress-strip b {
  color: #1d64b3;
  font-size: 22px;
}
.station-progress-strip .completed {
  color: #26a269;
}
.station-progress-strip .unfinished {
  color: #d88a21;
}
.station-progress-strip .overdue {
  color: #d64a43;
}
.progress-main {
  display: grid;
  grid-template-columns: 60px 1fr;
  align-items: center;
  gap: 10px;
}
.inspection-content {
  padding: 14px 28px;
  background: #f8fbff;
}
@media (max-width: 1350px) {
  .station-progress-strip {
    grid-template-columns: repeat(4, 1fr);
  }
  .progress-main {
    grid-column: 1/-1;
  }
}
</style>
