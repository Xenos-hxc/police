<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { hiddenDangerApi, systemApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { quarterLabel } from '@/utils/format'
import TargetTypeSwitch from '@/components/TargetTypeSwitch.vue'
import type { DepartmentNode, HiddenDanger, TagType } from '@/types'

interface DangerQuery extends Record<string, unknown> {
  page: number
  size: number
  targetName: string
  year: number
  quarter?: number
  status: string
  executorDeptId?: number
  deadlineRange: string[]
}

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const rows = ref<HiddenDanger[]>([])
const total = ref(0)
const targetTypes = ref<string[]>([])
const activeType = ref('')
const depts = ref<DepartmentNode[]>([])
const loadedTypes = reactive<Record<string, boolean>>({})
const unfinishedCounts = ref<Record<string, number>>({})
const query = reactive<DangerQuery>({
  page: 1,
  size: 10,
  targetName: '',
  year: new Date().getFullYear(),
  quarter: undefined,
  status: '',
  executorDeptId: undefined,
  deadlineRange: []
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
const quarterOptions = computed(() =>
  Array.from({ length: 4 }, (_, index) => index + 1).filter(
    (quarter) =>
      query.year > periodInfo.value.startYear ||
      (query.year === periodInfo.value.startYear && quarter >= periodInfo.value.startQuarter)
  )
)
const handleYearChange = () => {
  if (query.quarter && !quarterOptions.value.includes(query.quarter)) query.quarter = undefined
}
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
const canFilterDept = computed(() => auth.roles.includes('BUREAU'))
const targetSwitchOptions = computed(() =>
  targetTypes.value.map((type) => ({
    value: type,
    label: targetTypeLabel[type],
    unfinishedCount: Number(unfinishedCounts.value[type] || 0),
    lazy: !loadedTypes[type]
  }))
)
const flatten = (nodes: DepartmentNode[], result: DepartmentNode[] = []) => {
  nodes.forEach((node) => {
    result.push(node)
    if (node.children) flatten(node.children, result)
  })
  return result
}
const requestParams = () => ({
  ...query,
  targetType: activeType.value,
  deadlineStart: query.deadlineRange?.[0],
  deadlineEnd: query.deadlineRange?.[1],
  deadlineRange: undefined
})
const load = async () => {
  if (!activeType.value) return
  loading.value = true
  try {
    const response = await hiddenDangerApi.list(requestParams())
    rows.value = response.data.records
    total.value = response.data.total
    loadedTypes[activeType.value] = true
  } finally {
    loading.value = false
  }
}
const switchType = async (type: string) => {
  activeType.value = type
  query.page = 1
  await load()
}
const search = () => {
  query.page = 1
  load()
}
const reset = () => {
  Object.assign(query, {
    page: 1,
    size: 10,
    targetName: '',
    year: periodInfo.value.effectiveYear,
    quarter: undefined,
    status: '',
    executorDeptId: undefined,
    deadlineRange: []
  })
  load()
}
const openDetail = (row: HiddenDanger) => router.push(`/hidden-dangers/${row.id}`)

onMounted(async () => {
  await auth.fetchSystemPeriod()
  query.year = periodInfo.value.effectiveYear
  const [typeResponse, countResponse, deptResponse] = await Promise.all([
    hiddenDangerApi.targetTypes(),
    hiddenDangerApi.unfinishedCounts(),
    canFilterDept.value ? systemApi.depts() : Promise.resolve(undefined)
  ])
  targetTypes.value = typeResponse.data
  unfinishedCounts.value = countResponse.data || {}
  depts.value = deptResponse
    ? flatten(deptResponse.data).filter((item) => ['BUREAU', 'STATION'].includes(item.deptType))
    : []
  activeType.value = targetTypes.value[0] || ''
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-heading compact">
      <div>
        <h2>隐患整改</h2>
        <p>期限改隐患进入整改任务，例行改仅随原检查资料归档</p>
      </div>
    </div>
    <TargetTypeSwitch
      v-if="targetTypes.length"
      :model-value="activeType"
      :options="targetSwitchOptions"
      @change="switchType"
    />
    <section class="panel">
      <div class="filter-bar danger-filter">
        <el-input v-model="query.targetName" clearable placeholder="检查对象" style="width: 190px" />
        <el-input-number
          v-model="query.year"
          :min="periodInfo.startYear"
          :max="periodInfo.effectiveYear"
          @change="handleYearChange"
        />
        <el-select v-model="query.quarter" clearable placeholder="季度" style="width: 130px">
          <el-option v-for="quarter in quarterOptions" :key="quarter" :label="quarterLabel(quarter)" :value="quarter" />
        </el-select>
        <el-select
          v-if="canFilterDept"
          v-model="query.executorDeptId"
          filterable
          clearable
          placeholder="执行部门"
          style="width: 230px"
        >
          <el-option v-for="dept in depts" :key="dept.id" :label="dept.label" :value="dept.id" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="整改状态" style="width: 140px">
          <el-option v-for="(label, value) in statusLabel" :key="value" :label="label" :value="value" />
        </el-select>
        <el-date-picker
          v-model="query.deadlineRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="期限起"
          end-placeholder="期限止"
          style="width: 260px"
        />
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" stripe table-layout="auto">
        <el-table-column label="对象类型" width="100"
          ><template #default="{ row }">{{ targetTypeLabel[row.targetType] }}</template></el-table-column
        >
        <el-table-column prop="targetName" label="检查对象" min-width="300" show-overflow-tooltip />
        <el-table-column prop="dangerDetail" label="隐患详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="executorDeptName" label="执行部门" min-width="180" show-overflow-tooltip />
        <el-table-column label="周期" width="135"
          ><template #default="{ row }">{{ row.taskYear }}年 {{ quarterLabel(row.quarter) }}</template></el-table-column
        >
        <el-table-column prop="rectificationDeadline" label="整改期限" width="120" />
        <el-table-column label="状态" width="105"
          ><template #default="{ row }"
            ><el-tag :type="statusTag[row.status]">{{ statusLabel[row.status] || row.status }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作" width="80" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openDetail(row)">{{
              auth.user?.deptId !== row.executorDeptId
                ? '查看'
                : ['COMPLETED', 'OVERDUE_COMPLETED'].includes(row.status)
                  ? '编辑'
                  : '提交'
            }}</el-button></template
          ></el-table-column
        >
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
.danger-filter {
  align-items: center;
}
</style>
