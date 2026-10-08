<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { dashboardApi } from '@/api'
import MetricCard from '@/components/MetricCard.vue'
import type { ChartCallbackParam, CheckTask, DashboardSummary, TargetCompletion } from '@/types'
import { formatDateTime, quarterLabel } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

use([BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const summary = ref<Partial<DashboardSummary>>({})
const warnings = ref<CheckTask[]>([])
const warningTotal = ref(0)
const summaryLoading = ref(true)
const chartLoading = ref(true)
const warningLoading = ref(true)
const statusChart = ref<HTMLDivElement>()
const typeChart = ref<HTMLDivElement>()
const instances: ECharts[] = []
const auth = useAuthStore()
const router = useRouter()
const periodText = computed(() => {
  const year = Number(summary.value.year || auth.systemPeriod?.effectiveYear || new Date().getFullYear())
  const quarter = Number(
    summary.value.quarter || auth.systemPeriod?.effectiveQuarter || Math.floor(new Date().getMonth() / 3) + 1
  )
  return `${year} 年度 · ${quarterLabel(quarter)}`
})
const warningQuery = reactive({ page: 1, size: 8 })

const typeLabel: Record<string, string> = {
  STATION: '派出所',
  KEY_UNIT: '重点单位',
  IMPORTANT_PART: '重要部位'
}

const isCompletedStatus = (status: string) => ['APPROVED', 'OVERDUE_SUBMITTED'].includes(status)
const percentValue = (value: number, total: number) => (total ? Number(((value * 100) / total).toFixed(2)) : 0)

const archiveHomePath = computed(() => {
  if (auth.roles.includes('STATION')) return '/archives/units'
  return '/archives/stations'
})

const resize = () => instances.forEach((chart) => chart.resize())

const loadWarnings = async () => {
  warningLoading.value = true
  try {
    const response = await dashboardApi.warnings(warningQuery)
    warnings.value = response.data.records
    warningTotal.value = response.data.total
  } finally {
    warningLoading.value = false
  }
}

const openWarning = (item: CheckTask) => {
  if (item.id) {
    router.push(`/tasks/${item.id}`)
  } else {
    router.push('/tasks/list')
  }
}

const renderCharts = async () => {
  chartLoading.value = true
  const c = await dashboardApi.charts()
  await nextTick()
  const pie = init(statusChart.value!)
  const statusEntries = Object.entries(c.data.status || {}) as Array<[string, number]>
  const completedCount = statusEntries
    .filter(([name]) => isCompletedStatus(name))
    .reduce((sum, [, value]) => sum + Number(value || 0), 0)
  const totalStatusCount = statusEntries.reduce((sum, [, value]) => sum + Number(value || 0), 0)
  const unfinishedCount = Math.max(0, totalStatusCount - completedCount)
  pie.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    color: ['#26a269', '#f0a23a'],
    series: [
      {
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '44%'],
        label: { formatter: '{b}\n{d}%' },
        data: [
          { name: '已完成', value: completedCount },
          { name: '未完成', value: unfinishedCount }
        ]
      }
    ]
  })

  const bar = init(typeChart.value!)
  const completionEntries = Object.entries(c.data.targetCompletion || {}) as Array<[string, TargetCompletion]>
  const completionRows = completionEntries.map(([name, value]) => {
    const completed = Number(value.completed || 0)
    const unfinished = Number(value.unfinished || 0)
    const total = completed + unfinished
    return {
      name: typeLabel[name] || name,
      completed,
      unfinished,
      total,
      completedPercent: percentValue(completed, total),
      unfinishedPercent: percentValue(unfinished, total)
    }
  })
  bar.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: ChartCallbackParam[]) => {
        const row = completionRows[params?.[0]?.dataIndex] || {}
        return `${row.name}<br/>已完成：${row.completed || 0} / ${row.total || 0}（${row.completedPercent || 0}%）<br/>未完成：${row.unfinished || 0} / ${row.total || 0}（${row.unfinishedPercent || 0}%）`
      }
    },
    legend: { bottom: 0 },
    grid: { left: 45, right: 25, top: 25, bottom: 45 },
    xAxis: { type: 'category', data: completionRows.map((row) => row.name), axisLabel: { rotate: 12 } },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: { formatter: '{value}%' },
      splitLine: { lineStyle: { color: '#edf1f7' } }
    },
    series: [
      {
        name: '已完成',
        type: 'bar',
        stack: '完成情况',
        data: completionRows.map((row) => row.completedPercent),
        barWidth: 34,
        itemStyle: { color: '#26a269' }
      },
      {
        name: '未完成',
        type: 'bar',
        stack: '完成情况',
        data: completionRows.map((row) => row.unfinishedPercent),
        barWidth: 34,
        label: {
          show: true,
          position: 'top',
          formatter: (params: ChartCallbackParam) => `${completionRows[params.dataIndex]?.completedPercent || 0}%`
        },
        itemStyle: { color: '#f0a23a', borderRadius: [4, 4, 0, 0] }
      }
    ]
  })
  instances.push(pie, bar)
  chartLoading.value = false
}

onMounted(async () => {
  dashboardApi
    .summary()
    .then((response) => {
      summary.value = response.data
    })
    .finally(() => {
      summaryLoading.value = false
    })
  renderCharts()
  loadWarnings()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  instances.forEach((chart) => chart.dispose())
})
</script>

<template>
  <div class="page dashboard">
    <div class="page-heading">
      <div>
        <h2>治安保卫监督检查驾驶舱</h2>
        <p>展示当前部门自己负责执行的检查任务、完成情况和预警提醒</p>
      </div>
      <div class="period-tag">
        <el-icon><Calendar /></el-icon> {{ periodText }}
      </div>
    </div>

    <section class="panel quick-panel">
      <div class="panel-title"><b>快捷入口</b><span>常用业务操作</span></div>
      <div class="quick-actions">
        <router-link to="/tasks/list"
          ><el-icon><Tickets /></el-icon><span>任务管理</span></router-link
        >
        <router-link :to="archiveHomePath"
          ><el-icon><OfficeBuilding /></el-icon><span>档案管理</span></router-link
        >
        <router-link to="/statistics"
          ><el-icon><TrendCharts /></el-icon><span>统计分析</span></router-link
        >
      </div>
    </section>

    <div v-loading="summaryLoading" class="metrics-grid">
      <MetricCard title="已完成" :value="summary.completedCount || 0" unit="项" icon="CircleCheck" tone="green" />
      <MetricCard title="未完成" :value="summary.unfinishedCount || 0" unit="项" icon="Clock" tone="orange" />
      <MetricCard
        title="任务提交率"
        :value="summary.submissionRate || 0"
        unit="%"
        icon="DocumentChecked"
        tone="green"
      />
      <MetricCard title="综合覆盖率" :value="summary.coverageRate || 0" unit="%" icon="Aim" />
    </div>

    <div v-loading="chartLoading" class="dashboard-grid">
      <section class="panel chart-panel">
        <div class="panel-title"><b>当前部门任务状态</b><span>仅当前部门执行任务</span></div>
        <div ref="statusChart" class="chart"></div>
      </section>
      <section class="panel chart-panel wide">
        <div class="panel-title"><b>当前部门对象完成情况</b><span>按检查对象类型统计</span></div>
        <div ref="typeChart" class="chart"></div>
      </section>
    </div>

    <div class="dashboard-grid lower warning-only">
      <section v-loading="warningLoading" class="panel warning-panel">
        <div class="panel-title"><b>预警提醒</b><span class="danger-text">仅展示当前部门执行任务</span></div>
        <div v-if="!warnings.length" class="empty-inline">暂无预警任务</div>
        <div
          v-for="(item, index) in warnings"
          :key="item.id || `${item.targetType}-${index}`"
          class="warning-item clickable"
          @click="openWarning(item)"
        >
          <div class="warning-dot" :class="{ overdue: item.overdue }"></div>
          <div>
            <b>{{ item.targetName }}</b>
            <p>{{ item.taskName }}</p>
          </div>
          <span>{{ item.overdue ? '已逾期' : formatDateTime(item.deadline) }}</span>
        </div>
        <div class="pagination">
          <el-pagination
            v-model:current-page="warningQuery.page"
            v-model:page-size="warningQuery.size"
            layout="total, prev, pager, next"
            :total="warningTotal"
            @change="loadWarnings"
          />
        </div>
      </section>
    </div>
  </div>
</template>
