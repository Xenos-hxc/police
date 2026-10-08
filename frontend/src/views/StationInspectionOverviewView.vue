<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { stationInspectionApi } from '@/api'
import { quarterLabel } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

interface TargetProgress {
  initialized: boolean
  requiredCount: number
  completedCount: number
  unfinishedCount: number
  overdueCount: number
  completionRate: number
}

interface QuarterProgress {
  quarter: number
  initialized: boolean
  keyUnit: TargetProgress
  importantPart: TargetProgress
  requiredCount: number
  completedCount: number
  unfinishedCount: number
  overdueCount: number
  completionRate: number
}

interface StationProgress {
  deptId: number
  stationName: string
  year: number
  quarter: number
  progress: QuarterProgress
}

const router = useRouter()
const auth = useAuthStore()
const currentYear = new Date().getFullYear()
const currentQuarter = Math.floor(new Date().getMonth() / 3) + 1
const loading = ref(false)
const rows = ref<StationProgress[]>([])
const total = ref(0)
const query = reactive({
  year: currentYear,
  quarter: currentQuarter,
  stationName: '',
  completionOrder: '',
  page: 1,
  size: 10
})
const periodInfo = computed(
  () =>
    auth.systemPeriod || {
      startYear: 2026,
      startQuarter: 3,
      currentYear,
      currentQuarter,
      effectiveYear: currentYear,
      effectiveQuarter: currentQuarter
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

const load = async () => {
  loading.value = true
  try {
    const response = await stationInspectionApi.progress(query)
    rows.value = response.data.records || []
    total.value = response.data.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.page = 1
  load()
}

const reset = () => {
  Object.assign(query, {
    year: periodInfo.value.effectiveYear,
    quarter: periodInfo.value.effectiveQuarter,
    stationName: '',
    completionOrder: '',
    page: 1
  })
  load()
}

const handleYearChange = () => {
  if (!quarterOptions.value.includes(query.quarter)) query.quarter = quarterOptions.value[0]
}

const handleSortChange = ({ prop, order }: { prop: string; order: string | null }) => {
  if (prop !== 'completionRate') return
  query.completionOrder = order === 'ascending' ? 'ASC' : order === 'descending' ? 'DESC' : ''
  query.page = 1
  load()
}

const openDetail = (row: StationProgress) => {
  router.push({
    path: `/station-inspections/${row.deptId}`,
    query: { year: query.year, quarter: query.quarter }
  })
}

onMounted(async () => {
  await auth.fetchSystemPeriod()
  query.year = periodInfo.value.effectiveYear
  query.quarter = periodInfo.value.effectiveQuarter
  load()
})
</script>

<template>
  <div class="page station-inspection-overview">
    <div class="page-heading compact">
      <div>
        <h2>各所检查详情</h2>
        <p>查看各派出所在所选季度对重点单位和重要部位的检查进度，完成数包含正常完成和逾期完成。</p>
      </div>
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
        <el-input
          v-model="query.stationName"
          clearable
          placeholder="派出所名称"
          style="width: 260px"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search"
          ><el-icon><Search /></el-icon> 查询</el-button
        >
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe table-layout="fixed" @sort-change="handleSortChange">
        <el-table-column prop="stationName" label="派出所" min-width="230" fixed="left" show-overflow-tooltip />
        <el-table-column label="重点单位检查" min-width="190">
          <template #default="{ row }">
            <div class="progress-cell">
              <div class="target-line">
                <span>已完成/应完成</span
                ><b>{{ row.progress.keyUnit.completedCount }}/{{ row.progress.keyUnit.requiredCount }}</b>
              </div>
              <el-progress :percentage="row.progress.keyUnit.completionRate" :stroke-width="8" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="重要部位检查" min-width="190">
          <template #default="{ row }">
            <div class="progress-cell">
              <div class="target-line">
                <span>已完成/应完成</span
                ><b>{{ row.progress.importantPart.completedCount }}/{{ row.progress.importantPart.requiredCount }}</b>
              </div>
              <el-progress :percentage="row.progress.importantPart.completionRate" :stroke-width="8" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="应完成" width="90" align="center"
          ><template #default="{ row }">{{ row.progress.requiredCount }}</template></el-table-column
        >
        <el-table-column label="已完成" width="90" align="center"
          ><template #default="{ row }">{{ row.progress.completedCount }}</template></el-table-column
        >
        <el-table-column label="未完成" width="90" align="center"
          ><template #default="{ row }">{{ row.progress.unfinishedCount }}</template></el-table-column
        >
        <el-table-column
          prop="completionRate"
          label="完成率"
          width="115"
          align="center"
          sortable="custom"
          :sort-orders="['descending', 'ascending', null]"
        >
          <template #default="{ row }"
            ><b class="rate-text">{{ row.progress.completionRate }}%</b></template
          >
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }"
            ><el-button link type="primary" @click="openDetail(row)">查看详情</el-button></template
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
.progress-cell {
  padding: 8px 4px;
}
.target-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 7px;
  color: #53677f;
  font-size: 13px;
}
.target-line b,
.rate-text {
  color: #183f6b;
}
.station-inspection-overview :deep(.el-progress__text) {
  min-width: 42px;
  font-size: 12px !important;
}
</style>
