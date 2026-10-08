<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { statisticsApi, systemApi } from '@/api'
import MetricCard from '@/components/MetricCard.vue'
import { saveBlob } from '@/utils/download'
import { quarterLabel } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import type {
  ChartCallbackParam,
  DepartmentNode,
  StatisticsCharts,
  StatisticsCompletion,
  StatisticsOverview,
  StatisticsRanking
} from '@/types'

use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

interface StatisticsQuery extends Record<string, unknown> {
  year: number
  quarter?: number
  halfYear?: number
  deptId?: number
  executorDeptType: string
  rankDeptType: string
  page: number
  size: number
}

const summary = ref<Partial<StatisticsOverview>>({})
const ranking = ref<StatisticsRanking[]>([])
const rankingTotal = ref(0)
const loading = ref(false)
const chartLoading = ref(false)
const rankingLoading = ref(false)
const deptLoading = ref(false)
const chart1 = ref<HTMLDivElement>()
const chart2 = ref<HTMLDivElement>()
const chart3 = ref<HTMLDivElement>()
const deptOptions = ref<DepartmentNode[]>([])
const charts: ECharts[] = []
const auth = useAuthStore()
const isGlobalViewer = computed(() => auth.roles.some((role) => ['ADMIN', 'BUREAU'].includes(role)))
const showRanking = computed(() => isGlobalViewer.value)
const rankingTitle = computed(() => '各所完成率排名')
const query = reactive<StatisticsQuery>({
  year: new Date().getFullYear(),
  quarter: Math.floor(new Date().getMonth() / 3) + 1,
  halfYear: undefined,
  deptId: undefined,
  executorDeptType: '',
  rankDeptType: 'STATION',
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
const includedQuarter = (year: number, quarter: number) =>
  year > periodInfo.value.startYear || (year === periodInfo.value.startYear && quarter >= periodInfo.value.startQuarter)
const quarterOptions = computed(() =>
  Array.from({ length: 4 }, (_, index) => index + 1).filter((quarter) => includedQuarter(Number(query.year), quarter))
)
const halfYearOptions = computed(() =>
  [
    { label: '上半年', value: 1, quarters: [1, 2] },
    { label: '下半年', value: 2, quarters: [3, 4] }
  ].filter((item) => item.quarters.some((quarter) => includedQuarter(Number(query.year), quarter)))
)

const executorDeptTypeLabels: Record<string, string> = {
  BUREAU: '公安处',
  STATION: '派出所'
}

const executorDeptTypeOptions = computed(() => {
  if (isGlobalViewer.value) {
    return ['BUREAU', 'STATION']
  }
  return ['STATION']
})
const filteredDeptOptions = computed(() =>
  query.executorDeptType
    ? deptOptions.value.filter((dept) => dept.deptType === query.executorDeptType)
    : deptOptions.value
)
const changeExecutorDeptType = () => {
  if (query.deptId && !filteredDeptOptions.value.some((dept) => dept.id === query.deptId)) query.deptId = undefined
}

const isCompletedStatus = (status: string) => ['APPROVED', 'OVERDUE_SUBMITTED'].includes(status)
const percentValue = (value: number, total: number) => (total ? Number(((value * 100) / total).toFixed(2)) : 0)

const isQuarterEnded = () => {
  const year = Number(query.year)
  const quarter = Number(query.quarter || (query.halfYear === 1 ? 2 : query.halfYear === 2 ? 4 : 0))
  if (!year || !quarter) return false
  const endMonth = quarter * 3
  const endDate = new Date(year, endMonth, 0, 23, 59, 59)
  return Date.now() > endDate.getTime()
}

const rankTypeLabels: Record<string, string> = {
  BUREAU: '公安处',
  STATION: '派出所'
}

const flatten = (nodes: DepartmentNode[], result: DepartmentNode[] = []) => {
  nodes.forEach((node) => {
    result.push(node)
    if (node.children) flatten(node.children, result)
  })
  return result
}

const renderCharts = (data: StatisticsCharts) => {
  charts.forEach((chart) => chart.dispose())
  charts.length = 0
  const status = Object.entries(data.status || {}) as Array<[string, number]>
  const overdueCompletedCount = Number((data.status || {}).OVERDUE_SUBMITTED || 0)
  const completedCount =
    status.filter(([name]) => isCompletedStatus(name)).reduce((sum, [, value]) => sum + Number(value || 0), 0) -
    overdueCompletedCount
  const totalStatusCount = status.reduce((sum, [, value]) => sum + Number(value || 0), 0)
  const unfinishedCount = Math.max(0, totalStatusCount - completedCount - overdueCompletedCount)
  const unfinishedLabel = isQuarterEnded() ? '已逾期' : '未完成'
  const pie = init(chart1.value!)
  pie.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    color: ['#f0a23a', '#26a269', '#d96b2b'],
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        data: [
          { name: unfinishedLabel, value: unfinishedCount },
          { name: '已完成', value: completedCount },
          { name: '逾期完成', value: overdueCompletedCount }
        ]
      }
    ]
  })
  const line = init(chart2.value!)
  line.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 45, right: 20, top: 30, bottom: 50 },
    xAxis: { type: 'category', data: (data.trend || []).map((x) => x.label || quarterLabel(x.quarter)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '应完成',
        type: 'line',
        smooth: true,
        data: (data.trend || []).map((x) => x.required),
        itemStyle: { color: '#1d64b3' }
      },
      {
        name: '已完成',
        type: 'line',
        smooth: true,
        data: (data.trend || []).map((x) => x.completed),
        itemStyle: { color: '#26a269' }
      },
      {
        name: '未完成',
        type: 'line',
        smooth: true,
        data: (data.trend || []).map((x) => x.unfinished),
        itemStyle: { color: '#f0a23a' }
      }
    ]
  })
  const scopeEntries = Object.entries(data.scopeCompletion || {}) as Array<[string, StatisticsCompletion]>
  const scopeRows = scopeEntries.map(([name, value]) => {
    const completed = Number(value.completed || 0)
    const unfinished = Number(value.unfinished || 0)
    const total = completed + unfinished
    return {
      name,
      completed,
      unfinished,
      total,
      completedPercent: percentValue(completed, total),
      unfinishedPercent: percentValue(unfinished, total)
    }
  })
  const scope = init(chart3.value!)
  scope.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: ChartCallbackParam[]) => {
        const row = scopeRows[params?.[0]?.dataIndex] || {}
        return `${row.name}<br/>已完成：${row.completed || 0} / ${row.total || 0}（${row.completedPercent || 0}%）<br/>未完成：${row.unfinished || 0} / ${row.total || 0}（${row.unfinishedPercent || 0}%）`
      }
    },
    legend: { bottom: 0 },
    grid: { left: 45, right: 20, top: 30, bottom: 60 },
    xAxis: { type: 'category', data: scopeRows.map((row) => row.name), axisLabel: { rotate: 16 } },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      {
        name: '已完成',
        type: 'bar',
        stack: '完成情况',
        data: scopeRows.map((row) => row.completedPercent),
        itemStyle: { color: '#26a269' }
      },
      {
        name: '未完成',
        type: 'bar',
        stack: '完成情况',
        data: scopeRows.map((row) => row.unfinishedPercent),
        label: {
          show: true,
          position: 'top',
          formatter: (params: ChartCallbackParam) => `${scopeRows[params.dataIndex]?.completedPercent || 0}%`
        },
        itemStyle: { color: '#f0a23a', borderRadius: [4, 4, 0, 0] }
      }
    ]
  })
  charts.push(pie, line, scope)
}

const loadOverviewAndCharts = async () => {
  loading.value = true
  chartLoading.value = true
  try {
    const [overview, chartData] = await Promise.all([statisticsApi.overview(query), statisticsApi.charts(query)])
    summary.value = overview.data
    await nextTick()
    renderCharts(chartData.data)
  } finally {
    loading.value = false
    chartLoading.value = false
  }
}

const loadRanking = async () => {
  if (!showRanking.value) return
  rankingLoading.value = true
  try {
    const rankData = await statisticsApi.ranking({
      ...query,
      deptId: undefined,
      executorDeptType: 'STATION',
      rankDeptType: 'STATION'
    })
    ranking.value = rankData.data.records || []
    rankingTotal.value = rankData.data.total || 0
  } finally {
    rankingLoading.value = false
  }
}

const search = () => {
  query.page = 1
  const requests = [loadOverviewAndCharts()]
  if (showRanking.value) requests.push(loadRanking())
  Promise.all(requests)
}

const changeQuarter = () => {
  if (query.quarter) query.halfYear = undefined
}

const changeHalfYear = () => {
  if (query.halfYear) query.quarter = undefined
}

const changeYear = () => {
  if (query.quarter && !quarterOptions.value.includes(query.quarter)) query.quarter = undefined
  if (query.halfYear && !halfYearOptions.value.some((item) => item.value === query.halfYear)) query.halfYear = undefined
}

const reset = () => {
  Object.assign(query, {
    year: periodInfo.value.effectiveYear,
    quarter: periodInfo.value.effectiveQuarter,
    halfYear: undefined,
    deptId: undefined,
    executorDeptType: '',
    rankDeptType: 'STATION',
    page: 1,
    size: 10
  })
  search()
}

const exportFile = async () => saveBlob(await statisticsApi.export(query), '统计分析.xlsx')
const resize = () => charts.forEach((chart) => chart.resize())

onMounted(async () => {
  deptLoading.value = true
  try {
    const [deptResponse] = await Promise.all([systemApi.depts(), auth.fetchSystemPeriod()])
    deptOptions.value = flatten(deptResponse.data)
    query.year = periodInfo.value.effectiveYear
    query.quarter = periodInfo.value.effectiveQuarter
  } finally {
    deptLoading.value = false
  }
  search()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  charts.forEach((chart) => chart.dispose())
})
</script>

<template>
  <div class="page">
    <div class="page-heading compact">
      <div>
        <h2>统计分析</h2>
        <p>按任务执行部门统计：管理员可查看全系统，公安处包含三类检查，派出所包含本辖区重点单位和重要部位两类检查。</p>
      </div>
      <el-button @click="exportFile"
        ><el-icon><Download /></el-icon> 导出报表</el-button
      >
    </div>

    <section v-loading="deptLoading" class="panel">
      <div class="filter-bar statistics-filter">
        <el-input-number
          v-model="query.year"
          :min="periodInfo.startYear"
          :max="periodInfo.currentYear + 2"
          @change="changeYear"
        />
        <el-select v-model="query.quarter" clearable placeholder="季度" style="width: 120px" @change="changeQuarter">
          <el-option v-for="n in quarterOptions" :key="n" :label="quarterLabel(n)" :value="n" />
        </el-select>
        <el-select
          v-model="query.halfYear"
          clearable
          placeholder="半年度"
          style="width: 120px"
          @change="changeHalfYear"
        >
          <el-option v-for="item in halfYearOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.deptId" filterable clearable placeholder="所属部门" style="width: 220px">
          <el-option v-for="dept in filteredDeptOptions" :key="dept.id" :label="dept.label" :value="dept.id" />
        </el-select>
        <el-select
          v-model="query.executorDeptType"
          clearable
          placeholder="执行部门类型"
          style="width: 160px"
          @change="changeExecutorDeptType"
        >
          <el-option
            v-for="value in executorDeptTypeOptions"
            :key="value"
            :label="executorDeptTypeLabels[value]"
            :value="value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </section>

    <div v-loading="loading" class="metrics-grid four">
      <MetricCard title="覆盖率" :value="summary.coverageRate || 0" unit="%" icon="Aim" />
      <MetricCard title="提交率" :value="summary.submissionRate || 0" unit="%" icon="DocumentChecked" tone="green" />
      <MetricCard title="逾期率" :value="summary.overdueRate || 0" unit="%" icon="AlarmClock" tone="red" />
      <MetricCard title="已完成" :value="summary.coveredCount || 0" unit="项" icon="CircleCheck" tone="blue" />
    </div>

    <div v-loading="loading" class="statistics-detail-cards">
      <span
        >应覆盖 <b>{{ summary.requiredCount || 0 }}</b></span
      >
      <span
        >已覆盖 <b>{{ summary.coveredCount || 0 }}</b></span
      >
      <span
        >未覆盖 <b>{{ summary.uncoveredCount || 0 }}</b></span
      >
      <span
        >已提交 <b>{{ summary.submittedCount || 0 }}</b></span
      >
      <span
        >未提交 <b>{{ summary.unsubmittedCount || 0 }}</b></span
      >
      <span
        >逾期 <b>{{ summary.overdueCount || 0 }}</b></span
      >
    </div>

    <div v-loading="chartLoading" class="dashboard-grid">
      <section class="panel">
        <div class="panel-title"><b>任务状态分布</b></div>
        <div ref="chart1" class="chart"></div>
      </section>
      <section class="panel">
        <div class="panel-title"><b>检查层级完成情况</b><span>当前部门及下级执行任务</span></div>
        <div ref="chart3" class="chart"></div>
      </section>
    </div>

    <section v-loading="chartLoading" class="panel">
      <div class="panel-title"><b>年度任务完成趋势</b></div>
      <div ref="chart2" class="chart"></div>
    </section>

    <section v-if="showRanking" v-loading="rankingLoading" class="panel">
      <div class="panel-title">
        <b>{{ rankingTitle }}</b>
        <span>按当前统计周期汇总各派出所负责的重点单位和重要部位检查任务</span>
      </div>
      <el-table :data="ranking" stripe table-layout="auto">
        <el-table-column label="排名" width="70">
          <template #default="{ $index }">{{ (query.page - 1) * query.size + $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="220" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ rankTypeLabels[row.deptType] || row.deptType }}</template>
        </el-table-column>
        <el-table-column prop="taskCount" label="任务数" width="100" />
        <el-table-column prop="completedCount" label="完成数" width="100" />
        <el-table-column prop="overdueCount" label="逾期数" width="100" />
        <el-table-column label="完成率" width="190">
          <template #default="{ row }"><el-progress :percentage="row.completionRate" /></template>
        </el-table-column>
        <el-table-column label="逾期率" width="110">
          <template #default="{ row }">{{ row.overdueRate }}%</template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :total="rankingTotal"
          @change="loadRanking"
        />
      </div>
    </section>
  </div>
</template>
