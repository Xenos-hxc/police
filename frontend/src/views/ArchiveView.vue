<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { archiveApi, systemApi } from '@/api'
import { saveBlob } from '@/utils/download'
import { useAuthStore } from '@/stores/auth'
import type { ArchiveRecord, DepartmentNode, EditableForm } from '@/types'

interface ArchiveQuery extends Record<string, unknown> {
  page: number
  size: number
  name: string
  typeOrArea: string
  leader: string
  phone: string
  status?: number
  stationDeptId?: number
  archived: number
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const archiveType = computed(() => String(route.meta.archiveType || 'police-stations'))
const managementMode = computed(() => route.path.startsWith('/system/unit-department/'))
const rows = ref<ArchiveRecord[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number>()
const stationOptions = ref<DepartmentNode[]>([])
const form = reactive<EditableForm>({})
const query = reactive<ArchiveQuery>({
  page: 1,
  size: 10,
  name: '',
  typeOrArea: '',
  leader: '',
  phone: '',
  status: undefined,
  stationDeptId: undefined,
  archived: 0
})

const pageMeta: Record<string, { title: string; object: string; filename: string }> = {
  'police-stations': { title: '派出所档案', object: '派出所', filename: '派出所档案.xlsx' },
  'key-units': { title: '重点单位档案', object: '重点单位', filename: '重点单位档案.xlsx' },
  'important-parts': { title: '重要部位档案', object: '重要部位', filename: '重要部位档案.xlsx' }
}
const meta = computed(() => pageMeta[archiveType.value])
const isStation = computed(() => archiveType.value === 'police-stations')
const isUnit = computed(() => archiveType.value === 'key-units')
const isPart = computed(() => archiveType.value === 'important-parts')
const canMaintain = computed(() => auth.roles.some((role) => ['ADMIN', 'BUREAU'].includes(role)))
const archiveActionLabel = computed(() => (auth.isAdmin ? '档案详情' : '检查历史'))
const canAssignJurisdiction = computed(() => auth.roles.some((role) => ['ADMIN', 'BUREAU'].includes(role)))
const filteredByStation = computed(() => stationOptions.value.find((item) => item.id === query.stationDeptId))

const flatten = (nodes: DepartmentNode[], output: DepartmentNode[] = []) => {
  nodes.forEach((node) => {
    output.push(node)
    if (node.children?.length) flatten(node.children, output)
  })
  return output
}

const newForm = () => {
  const common = { status: 1, remark: '' }
  if (isStation.value) {
    return { ...common, stationName: '' }
  }
  const stationDeptIds = auth.roles.includes('STATION') && auth.user?.deptId ? [auth.user.deptId] : []
  if (isUnit.value) {
    return {
      ...common,
      unitName: '',
      unitType: '',
      establishedDate: '',
      leader: '',
      contactPerson: '',
      phone: '',
      address: '',
      bureauName: '演示公安处',
      jurisdictionText: '',
      stationDeptIds
    }
  }
  return {
    ...common,
    partName: '',
    partType: '重要部位',
    establishedDate: '',
    removedDate: '',
    guardStatus: '',
    lengthDescription: '',
    railwayLine: '',
    kilometerMark: '',
    location: '',
    responsibleUnit: '',
    workshop: '',
    bureauName: '演示公安处',
    jurisdictionText: '',
    stationDeptIds
  }
}

const applyRouteFilter = () => {
  const value = Number(route.query.stationDeptId)
  query.stationDeptId = Number.isFinite(value) && value > 0 ? value : undefined
  query.page = 1
}

const load = async () => {
  loading.value = true
  try {
    const response = await archiveApi.list(archiveType.value, query)
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
    page: 1,
    name: '',
    typeOrArea: '',
    leader: '',
    phone: '',
    status: undefined,
    stationDeptId: undefined,
    archived: 0
  })
  if (route.query.stationDeptId) router.replace({ path: route.path })
  load()
}

const openCreate = () => {
  editingId.value = undefined
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, newForm())
  dialogVisible.value = true
}

const openEdit = async (row: ArchiveRecord) => {
  editingId.value = row.id
  const detail = (await archiveApi.detail(archiveType.value, row.id)).data
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, newForm(), detail)
  dialogVisible.value = true
}

const save = async () => {
  const name = isStation.value ? form.stationName : isUnit.value ? form.unitName : form.partName
  if (!String(name || '').trim()) {
    return ElMessage.warning('名称不能为空')
  }
  saving.value = true
  try {
    if (editingId.value) await archiveApi.update(archiveType.value, editingId.value, form)
    else await archiveApi.create(archiveType.value, form)
    ElMessage.success('档案已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row: ArchiveRecord) => {
  await archiveApi.status(archiveType.value, row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? '档案已停用' : '档案已启用')
  await load()
}

const deleteRow = async (row: ArchiveRecord) => {
  await ElMessageBox.confirm(`确认删除“${displayName(row)}”？系统将作逻辑归档并保留历史检查记录。`, '删除确认', {
    type: 'warning'
  })
  await archiveApi.remove(archiveType.value, row.id)
  ElMessage.success('档案已删除并归档')
  await load()
}

const displayName = (row: ArchiveRecord) => row.stationName || row.unitName || row.partName || ''
const jurisdictionNames = (row: ArchiveRecord) =>
  row.stationNames?.length ? row.stationNames.join('、') : row.jurisdictionText || '仅公安处检查'

const history = (row: ArchiveRecord) => router.push(`/archives/${archiveType.value}/${row.id}/history`)
const openJurisdiction = (row: ArchiveRecord, type: 'units' | 'parts') => {
  router.push({
    path: `/archives/${type}`,
    query: { stationDeptId: row.deptId, stationName: row.stationName }
  })
}
const switchManagementType = (value: string | number | boolean | undefined) => {
  router.push(`/system/unit-department/${String(value)}`)
}

const exportFile = async () => saveBlob(await archiveApi.export(archiveType.value), meta.value.filename)
const importFile = async (request: UploadRequestOptions) => {
  const response = await archiveApi.import(archiveType.value, request.file)
  ElMessage.success(`成功导入 ${response.data.imported || 0} 条档案`)
  await load()
}

watch(
  () => route.fullPath,
  async () => {
    applyRouteFilter()
    await load()
  }
)

onMounted(async () => {
  stationOptions.value = flatten((await systemApi.depts()).data).filter((item) => item.deptType === 'STATION')
  applyRouteFilter()
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-heading compact">
      <div>
        <h2>{{ managementMode ? '单位和部门管理' : meta.title }}</h2>
        <p v-if="managementMode">集中维护重点单位、重要部位信息及其管辖派出所关系。</p>
        <p v-else>公安处查看全部真实档案；派出所仅查看辖区关系中明确分配给本所的档案，所有查询均支持模糊匹配。</p>
      </div>
      <div class="heading-actions">
        <el-upload v-if="canMaintain" :show-file-list="false" accept=".xlsx" :http-request="importFile">
          <el-button>导入</el-button>
        </el-upload>
        <el-button @click="exportFile"
          ><el-icon><Download /></el-icon> 导出</el-button
        >
        <el-button v-if="canMaintain" type="primary" @click="openCreate"
          ><el-icon><Plus /></el-icon> 新增{{ meta.object }}</el-button
        >
      </div>
    </div>

    <el-radio-group
      v-if="managementMode"
      :model-value="isUnit ? 'units' : 'parts'"
      class="management-type-switch"
      @update:model-value="switchManagementType"
    >
      <el-radio-button value="units">重点单位管理</el-radio-button>
      <el-radio-button value="parts">重要部位管理</el-radio-button>
    </el-radio-group>

    <el-alert
      v-if="filteredByStation"
      type="info"
      :closable="false"
      show-icon
      class="scope-alert"
      :title="`当前仅展示 ${filteredByStation.label} 管辖的${meta.object}`"
    />

    <section class="panel">
      <div class="filter-bar archive-filter">
        <el-input v-model="query.name" clearable :placeholder="`${meta.object}名称`" />
        <el-input v-if="!isStation" v-model="query.typeOrArea" clearable placeholder="类型/辖区" />
        <el-input
          v-if="!isStation"
          v-model="query.leader"
          clearable
          :placeholder="isPart ? '责任单位/车间' : '负责人/联络员'"
        />
        <el-input v-if="isUnit" v-model="query.phone" clearable placeholder="联系电话" />
        <el-select
          v-if="!isStation && canAssignJurisdiction"
          v-model="query.stationDeptId"
          clearable
          filterable
          placeholder="管辖派出所"
        >
          <el-option v-for="item in stationOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search"
          ><el-icon><Search /></el-icon> 查询</el-button
        >
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-if="isStation" v-loading="loading" :data="rows" stripe table-layout="auto">
        <el-table-column prop="stationName" label="派出所名称" min-width="280" show-overflow-tooltip />
        <el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
              row.status === 1 ? '正常' : '停用'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="history(row)">{{ archiveActionLabel }}</el-button>
            <el-button link @click="openJurisdiction(row, 'units')">查看重点单位</el-button>
            <el-button link @click="openJurisdiction(row, 'parts')">查看重要部位</el-button>
            <template v-if="canMaintain">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-dropdown trigger="click">
                <el-button link>更多</el-button>
                <template #dropdown
                  ><el-dropdown-menu>
                    <el-dropdown-item @click="toggleStatus(row)">{{
                      row.status === 1 ? '停用' : '启用'
                    }}</el-dropdown-item>
                    <el-dropdown-item divided @click="deleteRow(row)">删除（归档）</el-dropdown-item>
                  </el-dropdown-menu></template
                >
              </el-dropdown>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="isUnit" v-loading="loading" :data="rows" stripe table-layout="auto">
        <el-table-column prop="unitName" label="重点单位名称" min-width="280" show-overflow-tooltip />
        <el-table-column prop="leader" label="负责人" width="105" />
        <el-table-column prop="contactPerson" label="联络员" width="105" />
        <el-table-column prop="jurisdictionText" label="源表辖区" min-width="140" show-overflow-tooltip />
        <el-table-column label="管辖派出所" min-width="210" show-overflow-tooltip
          ><template #default="{ row }">{{ jurisdictionNames(row) }}</template></el-table-column
        >
        <el-table-column prop="establishedDate" label="确定时间" width="120" />
        <el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
              row.status === 1 ? '正常' : '停用'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作" width="180" fixed="right"
          ><template #default="{ row }">
            <el-button link type="primary" @click="history(row)">{{ archiveActionLabel }}</el-button>
            <el-button v-if="canMaintain" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-dropdown v-if="canMaintain" trigger="click"
              ><el-button link>更多</el-button
              ><template #dropdown
                ><el-dropdown-menu>
                  <el-dropdown-item @click="toggleStatus(row)">{{
                    row.status === 1 ? '停用' : '启用'
                  }}</el-dropdown-item>
                  <el-dropdown-item divided @click="deleteRow(row)">删除（归档）</el-dropdown-item>
                </el-dropdown-menu></template
              ></el-dropdown
            >
          </template></el-table-column
        >
      </el-table>

      <el-table v-else v-loading="loading" :data="rows" stripe table-layout="auto">
        <el-table-column prop="partName" label="重要部位名称" min-width="250" show-overflow-tooltip />
        <!--        <el-table-column prop="partType" label="部位类型" width="110" show-overflow-tooltip />-->
        <el-table-column prop="railwayLine" label="线别" width="100" show-overflow-tooltip />
        <el-table-column prop="responsibleUnit" label="责任单位" min-width="150" show-overflow-tooltip />
        <el-table-column label="管辖派出所" min-width="220" show-overflow-tooltip
          ><template #default="{ row }">{{ jurisdictionNames(row) }}</template></el-table-column
        >
        <el-table-column prop="location" label="所处地域" min-width="190" show-overflow-tooltip />
        <el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
              row.status === 1 ? '正常' : '停用'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作" width="180" fixed="right"
          ><template #default="{ row }">
            <el-button link type="primary" @click="history(row)">{{ archiveActionLabel }}</el-button>
            <el-button v-if="canMaintain" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-dropdown v-if="canMaintain" trigger="click"
              ><el-button link>更多</el-button
              ><template #dropdown
                ><el-dropdown-menu>
                  <el-dropdown-item @click="toggleStatus(row)">{{
                    row.status === 1 ? '停用' : '启用'
                  }}</el-dropdown-item>
                  <el-dropdown-item divided @click="deleteRow(row)">删除（归档）</el-dropdown-item>
                </el-dropdown-menu></template
              ></el-dropdown
            >
          </template></el-table-column
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

    <el-dialog
      v-model="dialogVisible"
      :title="`${editingId ? '编辑' : '新增'}${meta.object}`"
      width="900px"
      destroy-on-close
    >
      <el-form :model="form" label-width="120px">
        <template v-if="isStation">
          <el-form-item label="派出所名称" required><el-input v-model="form.stationName" /></el-form-item>
          <el-alert
            type="info"
            :closable="false"
            title="真实派出所源表仅包含派出所名称；负责人、电话、地址和管辖范围不作为派出所档案字段。"
          />
        </template>

        <template v-else-if="isUnit">
          <div class="form-grid">
            <el-form-item label="重点单位名称" required><el-input v-model="form.unitName" /></el-form-item>
            <el-form-item label="单位类型"><el-input v-model="form.unitType" /></el-form-item>
            <el-form-item label="确定时间"
              ><el-date-picker v-model="form.establishedDate" value-format="YYYY-MM-DD"
            /></el-form-item>
            <el-form-item label="负责人"><el-input v-model="form.leader" /></el-form-item>
            <el-form-item label="联络员"><el-input v-model="form.contactPerson" /></el-form-item>
            <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
            <el-form-item label="管辖公安处"><el-input v-model="form.bureauName" /></el-form-item>
          </div>
          <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
          <el-form-item label="源表管辖信息"><el-input v-model="form.jurisdictionText" /></el-form-item>
          <el-form-item label="管辖派出所">
            <el-select
              v-model="form.stationDeptIds"
              multiple
              filterable
              :disabled="!canAssignJurisdiction"
              style="width: 100%"
            >
              <el-option v-for="item in stationOptions" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <div class="form-grid">
            <el-form-item label="重要部位名称" required><el-input v-model="form.partName" /></el-form-item>
            <el-form-item label="部位类型"><el-input v-model="form.partType" /></el-form-item>
            <el-form-item label="确立时间"
              ><el-date-picker v-model="form.establishedDate" value-format="YYYY-MM-DD"
            /></el-form-item>
            <el-form-item label="撤销时间"
              ><el-date-picker v-model="form.removedDate" value-format="YYYY-MM-DD"
            /></el-form-item>
            <el-form-item label="值守情况"><el-input v-model="form.guardStatus" /></el-form-item>
            <el-form-item label="桥隧全长"><el-input v-model="form.lengthDescription" /></el-form-item>
            <el-form-item label="线别"><el-input v-model="form.railwayLine" /></el-form-item>
            <el-form-item label="公里数"><el-input v-model="form.kilometerMark" /></el-form-item>
            <el-form-item label="责任单位（站段）"><el-input v-model="form.responsibleUnit" /></el-form-item>
            <el-form-item label="责任单位（车间）"><el-input v-model="form.workshop" /></el-form-item>
            <el-form-item label="管辖公安处"><el-input v-model="form.bureauName" /></el-form-item>
          </div>
          <el-form-item label="所处地域"><el-input v-model="form.location" type="textarea" /></el-form-item>
          <el-form-item label="源表管辖信息"><el-input v-model="form.jurisdictionText" /></el-form-item>
          <el-form-item label="管辖派出所">
            <el-select
              v-model="form.stationDeptIds"
              multiple
              filterable
              :disabled="!canAssignJurisdiction"
              style="width: 100%"
            >
              <el-option v-for="item in stationOptions" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.heading-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.management-type-switch {
  margin-bottom: 16px;
}
.management-type-switch :deep(.el-radio-button__inner) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 180px;
  height: 44px;
}
.scope-alert {
  margin-bottom: 14px;
}
.archive-filter > * {
  width: 160px;
}
.archive-filter > *:first-child {
  width: 220px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}
@media (max-width: 1100px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .archive-filter > * {
    width: calc(33.333% - 10px);
  }
}
</style>
