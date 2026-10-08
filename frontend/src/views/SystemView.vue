<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { archiveApi, systemApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import type {
  AdminOverview,
  ArchiveRecord,
  Department,
  DepartmentNode,
  DictionaryData,
  DictionaryType,
  EditableForm,
  Menu,
  Role,
  SystemConfig,
  SystemRow,
  TreeNode
} from '@/types'

interface SystemQuery extends Record<string, unknown> {
  page: number
  size: number
  keyword: string
  username: string
  module: string
  deptType: string
  status?: number
}

interface MenuTreeInstance {
  setCheckedKeys(keys: number[]): void
  getCheckedKeys(leafOnly: boolean): number[]
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const section = computed(() => String(route.params.section || 'users'))
const rows = ref<SystemRow[]>([])
const tree = ref<TreeNode[]>([])
const flatDepts = ref<DepartmentNode[]>([])
const roles = ref<Role[]>([])
const menus = ref<Menu[]>([])
const dictTypes = ref<DictionaryType[]>([])
const dictData = ref<DictionaryData[]>([])
const selectedDictType = ref('')
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const secondaryDialog = ref(false)
const editingId = ref<number>()
const selectedDept = ref<Partial<Department & ArchiveRecord>>()
const adminOverview = ref<Partial<AdminOverview>>({})
const menuTreeRef = ref<MenuTreeInstance>()
const query = reactive<SystemQuery>({
  page: 1,
  size: 20,
  keyword: '',
  username: '',
  module: '',
  deptType: '',
  status: undefined
})
const form = reactive<EditableForm>({})
const secondaryForm = reactive<EditableForm>({})

const titles: Record<string, string> = {
  admin: '管理员总览',
  users: '账号管理',
  depts: '组织架构',
  roles: '角色管理',
  menus: '菜单管理',
  config: '系统配置',
  dicts: '数据字典',
  logs: '操作日志',
  'login-logs': '登录日志'
}
const deptTypeLabels: Record<string, string> = { BUREAU: '公安处', STATION: '派出所' }
const roleCodeLabels: Record<string, string> = { ADMIN: '系统管理员', BUREAU: '公安处账号', STATION: '派出所账号' }
const dataScopeLabels: Record<string, string> = { ALL: '全部数据', DEPT_AND_CHILD: '本部门及下级', DEPT: '本部门' }
const bureauDeptOptions = computed(() => flatDepts.value.filter((item) => item.deptType === 'BUREAU'))
const accountDeptOptions = computed(() => {
  if (form.accountType === 'BUREAU' || form.accountType === 'ADMIN') return bureauDeptOptions.value
  if (form.accountType === 'STATION') {
    return flatDepts.value.filter((item) => item.deptType === 'STATION' && item.parentId === form.parentDeptId)
  }
  return []
})
const configGroups = computed(() =>
  [
    {
      title: '系统时间边界',
      description: '边界之前的数据保留在数据库中，但不进入任务、统计、提醒、隐患整改和档案检查历史',
      keys: ['system.period.start']
    },
    {
      title: '季度覆盖规则',
      description: '直接驱动任务筛选、首页和统计分析的应完成数量',
      keys: [
        'coverage.bureau.station.quarter',
        'coverage.bureau.unit.quarter',
        'coverage.bureau.part.quarter',
        'coverage.station.unit.quarter',
        'coverage.station.part.quarter'
      ]
    },
    {
      title: '任务与提醒',
      description: '任务截止、逾期补交和首页预警规则',
      keys: ['task.remind.days', 'task.allow.overdue.submit', 'danger.default.deadline.days']
    },
    {
      title: '文件存储',
      description: '检查视频、笔录和隐患整改材料均保存到内网服务器，当前上限为5GB',
      keys: ['upload.path', 'upload.video.max.mb', 'upload.file.max.mb']
    },
    {
      title: '登录安全',
      description: '登录验证码和Token有效期配置',
      keys: ['captcha.enabled', 'token.expire.minutes']
    }
  ].map((group) => ({
    ...group,
    items: rows.value.filter(isSystemConfig).filter((item) => group.keys.includes(item.configKey))
  }))
)
const periodBoundaryOptions = computed(() => {
  const options: Array<{ label: string; value: string }> = []
  const maximumYear = new Date().getFullYear() + 2
  for (let year = 2026; year <= maximumYear; year++) {
    for (let quarter = 1; quarter <= 4; quarter++) {
      if (year === 2026 && quarter < 3) continue
      options.push({ label: `${year}年 第${['一', '二', '三', '四'][quarter - 1]}季度`, value: `${year}-Q${quarter}` })
    }
  }
  return options
})
const flatten = (nodes: DepartmentNode[], result: DepartmentNode[] = []) => {
  nodes.forEach((node) => {
    result.push(node)
    if (node.children) flatten(node.children, result)
  })
  return result
}
const buildOrganizationTree = (
  deptNodes: DepartmentNode[],
  units: ArchiveRecord[],
  parts: ArchiveRecord[]
): TreeNode[] =>
  deptNodes.map((node) => {
    const children = buildOrganizationTree(node.children || [], units, parts)
    if (node.deptType === 'STATION') {
      const stationUnits = units.filter((item) => item.stationDeptIds?.includes(node.id))
      const stationParts = parts.filter((item) => item.stationDeptIds?.includes(node.id))
      if (stationUnits.length) {
        children.push({
          treeKey: `unit-group-${node.id}`,
          label: `重点单位（${stationUnits.length}）`,
          nodeType: 'GROUP',
          children: stationUnits.map((item) => ({
            treeKey: `unit-${node.id}-${item.id}`,
            label: item.unitName || '未命名重点单位',
            nodeType: 'KEY_UNIT',
            archiveId: item.id
          }))
        })
      }
      if (stationParts.length) {
        children.push({
          treeKey: `part-group-${node.id}`,
          label: `重要部位（${stationParts.length}）`,
          nodeType: 'GROUP',
          children: stationParts.map((item) => ({
            treeKey: `part-${node.id}-${item.id}`,
            label: item.partName || '未命名重要部位',
            nodeType: 'IMPORTANT_PART',
            archiveId: item.id
          }))
        })
      }
    }
    return { ...node, treeKey: `dept-${node.id}`, nodeType: 'DEPT', children }
  })
const loadOrganization = async () => {
  const [deptResponse, unitResponse, partResponse] = await Promise.all([
    systemApi.depts(),
    archiveApi.list('key-units', { page: 1, size: 1000, archived: 0 }),
    archiveApi.list('important-parts', { page: 1, size: 1000, archived: 0 })
  ])
  tree.value = buildOrganizationTree(
    deptResponse.data || [],
    unitResponse.data.records || [],
    partResponse.data.records || []
  )
}
const buildMenuTree = (items: Menu[], parentId = 0): Menu[] =>
  items
    .filter((x) => x.parentId === parentId)
    .map((x) => ({ ...x, label: x.menuName, children: buildMenuTree(items, x.id) }))
const resetForm = () => {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, {
    status: 1,
    dataScope: 'DEPT',
    roleIds: [],
    accountType: 'STATION',
    parentId: 0,
    parentDeptId: undefined,
    sortNo: 1,
    menuType: 'C',
    visible: 1
  })
}
const loadCommon = async () => {
  const departments = (await systemApi.depts()).data
  tree.value = departments
  flatDepts.value = flatten(departments)
  roles.value = (await systemApi.roles()).data
  menus.value = (await systemApi.menus()).data
}
const load = async () => {
  loading.value = true
  try {
    if (section.value === 'admin') {
      adminOverview.value = (await systemApi.adminOverview()).data
    } else if (section.value === 'depts') {
      await loadOrganization()
    } else if (section.value === 'roles') {
      roles.value = (await systemApi.roles()).data
      rows.value = roles.value
    } else if (section.value === 'menus') {
      menus.value = (await systemApi.menus()).data
      rows.value = menus.value
    } else if (section.value === 'config') {
      rows.value = (await systemApi.configs()).data
    } else if (section.value === 'dicts') {
      dictTypes.value = (await systemApi.dictTypes()).data
      if (!selectedDictType.value && dictTypes.value.length) selectedDictType.value = dictTypes.value[0].dictType
      dictData.value = selectedDictType.value ? (await systemApi.dicts(selectedDictType.value)).data : []
    } else if (section.value === 'logs' || section.value === 'login-logs') {
      const type = section.value === 'logs' ? 'operation' : 'login'
      const response = await systemApi.logs(type, query)
      rows.value = response.data.records
      total.value = response.data.total
    } else {
      const [response, overview] = await Promise.all([
        systemApi.users(query),
        auth.isAdmin ? systemApi.adminOverview() : Promise.resolve({ data: {} })
      ])
      rows.value = response.data.records
      total.value = response.data.total
      adminOverview.value = overview.data
    }
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = undefined
  resetForm()
  if (section.value === 'users') {
    form.parentDeptId = bureauDeptOptions.value[0]?.id
    applyAccountTypePolicy()
  }
  if (section.value === 'depts' && selectedDept.value) form.parentId = selectedDept.value.id
  dialogVisible.value = true
}
const openEdit = async (row: SystemRow) => {
  if (!row.id) return
  editingId.value = row.id
  resetForm()
  if (section.value === 'users') {
    Object.assign(form, (await systemApi.user(row.id)).data)
    applyAccountTypePolicy(false)
  } else if (section.value === 'depts') Object.assign(form, (await systemApi.dept(row.id)).data)
  else if (section.value === 'roles') {
    const data = (await systemApi.role(row.id)).data
    Object.assign(form, data.role, { menuIds: data.menuIds })
    setTimeout(() => menuTreeRef.value?.setCheckedKeys(data.menuIds), 0)
  } else Object.assign(form, row)
  dialogVisible.value = true
}
const save = async () => {
  if (section.value === 'users') {
    if (!editingId.value && (!form.username || !form.password)) return ElMessage.warning('请输入账号和初始密码')
    if (!editingId.value && String(form.password).length > 32) return ElMessage.warning('初始密码长度必须为1-32位')
    if (!form.accountType || !form.deptId || !form.roleIds?.length)
      return ElMessage.warning('请选择账号类型和账号对应部门')
    if (form.accountType === 'STATION' && !form.parentDeptId) return ElMessage.warning('请选择所属公安处')
    if (editingId.value) await systemApi.updateUser(editingId.value, form)
    else await systemApi.createUser(form)
  } else if (section.value === 'depts') {
    if (!form.deptName || !form.deptType) return ElMessage.warning('请填写部门名称和类型')
    if (editingId.value) await systemApi.updateDept(editingId.value, form)
    else await systemApi.createDept(form)
  } else if (section.value === 'roles') {
    form.menuIds = menuTreeRef.value?.getCheckedKeys(false) || []
    if (!form.roleName || !form.roleCode) return ElMessage.warning('请填写角色名称和编码')
    if (editingId.value) await systemApi.updateRole(editingId.value, form)
    else await systemApi.createRole(form)
  } else if (section.value === 'menus') {
    if (!form.menuName || !form.menuType) return ElMessage.warning('请填写菜单名称和类型')
    if (editingId.value) await systemApi.updateMenu(editingId.value, form)
    else await systemApi.createMenu(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadCommon()
  await load()
}
const remove = async (row: SystemRow) => {
  if (!row.id) return
  await ElMessageBox.confirm('删除后不可恢复，确认继续？', '删除确认', { type: 'warning' })
  if (section.value === 'users') await systemApi.deleteUser(row.id)
  else if (section.value === 'depts') await systemApi.deleteDept(row.id)
  else if (section.value === 'roles') await systemApi.deleteRole(row.id)
  else if (section.value === 'menus') await systemApi.deleteMenu(row.id)
  ElMessage.success('已删除')
  load()
}
const toggleUser = async (row: SystemRow) => {
  if (!row.id) return
  await systemApi.userStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('账号状态已更新')
  load()
}
const resetPassword = async (row: SystemRow) => {
  if (!row.id) return
  const result = await ElMessageBox.prompt('请输入新密码（1-32 位）', `重置 ${row.username} 的密码`, {
    inputPattern: /^.{1,32}$/,
    inputErrorMessage: '密码长度必须为 1-32 位',
    inputValue: 'Demo-Only-Change-Me!2026'
  })
  await systemApi.resetPassword(row.id, result.value)
  ElMessage.success('密码已重置')
}
const applyAccountTypePolicy = (clearInvalidDept = true) => {
  const role = roles.value.find((item) => item.roleCode === form.accountType)
  if (role) form.roleIds = [role.id]
  form.dataScope = ['ADMIN', 'BUREAU'].includes(form.accountType || '') ? 'ALL' : 'DEPT'
  if (form.accountType === 'STATION' && !form.parentDeptId) {
    form.parentDeptId = bureauDeptOptions.value[0]?.id
  }
  if (clearInvalidDept && !accountDeptOptions.value.some((item) => item.id === form.deptId)) {
    form.deptId = undefined
  }
}
const applyParentDeptPolicy = () => {
  if (!accountDeptOptions.value.some((item) => item.id === form.deptId)) form.deptId = undefined
}
const selectDept = async (data: TreeNode) => {
  if (data.nodeType === 'GROUP') {
    selectedDept.value = undefined
    return
  }
  if (data.nodeType === 'KEY_UNIT') {
    if (!data.archiveId) return
    selectedDept.value = { ...(await archiveApi.detail('key-units', data.archiveId)).data, nodeType: data.nodeType }
    return
  }
  if (data.nodeType === 'IMPORTANT_PART') {
    if (!data.archiveId) return
    selectedDept.value = {
      ...(await archiveApi.detail('important-parts', data.archiveId)).data,
      nodeType: data.nodeType
    }
    return
  }
  if (data.id) selectedDept.value = { ...(await systemApi.dept(data.id)).data, nodeType: 'DEPT' }
}
const saveConfigs = async () => {
  await systemApi.saveConfigs(rows.value.filter(isSystemConfig))
  await auth.fetchSystemPeriod()
  ElMessage.success('系统配置已保存')
}
const selectDict = async (type: string) => {
  selectedDictType.value = type
  dictData.value = (await systemApi.dicts(type)).data
}
const openDictType = (row?: DictionaryType) => {
  Object.keys(secondaryForm).forEach((key) => delete secondaryForm[key])
  Object.assign(secondaryForm, row || { status: 1 }, { kind: 'type' })
  secondaryDialog.value = true
}
const openDictData = (row?: DictionaryData) => {
  Object.keys(secondaryForm).forEach((key) => delete secondaryForm[key])
  Object.assign(secondaryForm, row || { dictType: selectedDictType.value, status: 1, sortNo: 1 }, { kind: 'data' })
  secondaryDialog.value = true
}
const saveSecondary = async () => {
  if (secondaryForm.kind === 'type') {
    if (secondaryForm.id) await systemApi.updateDictType(secondaryForm.id, secondaryForm)
    else await systemApi.createDictType(secondaryForm)
  } else {
    if (secondaryForm.id) await systemApi.updateDict(secondaryForm.id, secondaryForm)
    else await systemApi.createDict(secondaryForm)
  }
  secondaryDialog.value = false
  ElMessage.success('字典已保存')
  load()
}
const deleteDictType = async (row: DictionaryType) => {
  await ElMessageBox.confirm('删除字典类型会同时删除其数据项，确认继续？', '删除确认', { type: 'warning' })
  await systemApi.deleteDictType(row.id)
  selectedDictType.value = ''
  load()
}
const deleteDict = async (row: DictionaryData) => {
  await systemApi.deleteDict(row.id)
  ElMessage.success('字典项已删除')
  selectDict(selectedDictType.value)
}

const searchSystem = () => {
  query.page = 1
  load()
}

const resetSystemQuery = () => {
  Object.assign(query, { keyword: '', username: '', module: '', deptType: '', status: undefined, page: 1 })
  load()
}

function isSystemConfig(row: SystemRow): row is SystemConfig {
  return typeof row.id === 'number' && typeof row.configKey === 'string'
}

watch(section, async () => {
  Object.assign(query, { page: 1, keyword: '', username: '', module: '', deptType: '', status: undefined })
  await loadCommon()
  await load()
})
onMounted(async () => {
  await loadCommon()
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-heading compact">
      <div>
        <h2>{{ titles[section] }}</h2>
        <p>维护组织账号、检查对象档案、辖区关系、业务规则与审计信息</p>
      </div>
      <div>
        <el-button
          v-if="['users', 'roles', 'menus'].includes(section) && auth.isAdmin"
          type="primary"
          @click="openCreate"
          ><el-icon><Plus /></el-icon> 新增</el-button
        >
        <el-button v-if="section === 'config'" type="primary" @click="saveConfigs"
          ><el-icon><Check /></el-icon> 保存配置</el-button
        >
      </div>
    </div>

    <template v-if="section === 'admin'">
      <div v-loading="loading" class="admin-metrics">
        <div>
          <span>公安处</span><b>{{ adminOverview.deptCounts?.BUREAU || 0 }}</b>
        </div>
        <div>
          <span>派出所</span><b>{{ adminOverview.deptCounts?.STATION || 0 }}</b
          ><small>账号覆盖 {{ adminOverview.accountCovered?.STATION || 0 }}</small>
        </div>
        <div>
          <span>重点单位档案</span><b>{{ adminOverview.archiveCounts?.KEY_UNIT || 0 }}</b>
        </div>
        <div>
          <span>重要部位档案</span><b>{{ adminOverview.archiveCounts?.IMPORTANT_PART || 0 }}</b>
        </div>
        <div>
          <span>重点单位辖区关系</span><b>{{ adminOverview.relationCounts?.KEY_UNIT || 0 }}</b>
        </div>
        <div>
          <span>重要部位辖区关系</span><b>{{ adminOverview.relationCounts?.IMPORTANT_PART || 0 }}</b>
        </div>
        <div :class="{ danger: adminOverview.missingAccountCount }">
          <span>缺少账号部门</span><b>{{ adminOverview.missingAccountCount || 0 }}</b>
        </div>
      </div>
      <div class="admin-rule-grid">
        <section class="panel">
          <div class="panel-title">
            <b>档案与辖区关系维护</b><span>管理员仅维护基础数据，不执行检查任务和隐患整改</span>
          </div>
          <div class="admin-task-stats">
            <span :class="{ danger: adminOverview.unassignedArchiveCounts?.KEY_UNIT }"
              >未分配重点单位 <b>{{ adminOverview.unassignedArchiveCounts?.KEY_UNIT || 0 }}</b></span
            >
            <span :class="{ danger: adminOverview.unassignedArchiveCounts?.IMPORTANT_PART }"
              >未分配重要部位 <b>{{ adminOverview.unassignedArchiveCounts?.IMPORTANT_PART || 0 }}</b></span
            >
            <span
              >共同管辖重点单位 <b>{{ adminOverview.sharedTargetCounts?.KEY_UNIT || 0 }}</b></span
            >
            <span
              >共同管辖重要部位 <b>{{ adminOverview.sharedTargetCounts?.IMPORTANT_PART || 0 }}</b></span
            >
          </div>
          <div class="admin-maintain-actions">
            <el-button type="primary" @click="router.push('/archives/stations')">维护派出所</el-button>
            <el-button @click="router.push('/archives/units')">维护重点单位及辖区</el-button>
            <el-button @click="router.push('/archives/parts')">维护重要部位及辖区</el-button>
            <el-button @click="router.push('/system/users')">维护账号</el-button>
          </div>
        </section>
        <section class="panel">
          <div class="panel-title"><b>固定业务边界</b><span>管理员不可改变的系统约束</span></div>
          <ul class="rule-list">
            <li>管理员不显示任务列表、任务详情和隐患整改功能，仅负责组织、账号、档案、辖区关系和系统配置。</li>
            <li>公安处分别检查派出所、重点单位和重要部位；派出所以辖区为范围分别检查重点单位和重要部位。</li>
            <li>共同管辖对象会按每个管辖派出所分别生成独立任务，各所提交各自实际检查位置的材料。</li>
            <li>公安处检查派出所以半年度轮换，检查重点单位和重要部位以年度轮换，同周期季度对象不得重复。</li>
            <li>季度100%覆盖任务无需筛选；清空筛选时已完成和逾期完成任务必须保留。</li>
            <li>未来季度禁止提交；逾期补交后状态为“逾期完成”，并作为已完成任务计入覆盖率。</li>
          </ul>
        </section>
      </div>
    </template>

    <section v-else-if="section === 'depts'" class="system-split">
      <div class="panel org-panel">
        <div class="panel-title"><b>组织树</b><span>展开派出所可查看管辖的重点单位和重要部位</span></div>
        <el-tree
          :data="tree"
          node-key="treeKey"
          highlight-current
          :props="{ children: 'children', label: 'label' }"
          @node-click="selectDept"
        >
          <template #default="{ data }"
            ><div class="tree-node">
              <span
                ><el-icon><OfficeBuilding /></el-icon>{{ data.label }}</span
              ><el-tag v-if="data.nodeType === 'DEPT'" size="small">{{
                deptTypeLabels[data.deptType] || data.deptType
              }}</el-tag>
            </div></template
          >
        </el-tree>
      </div>
      <div class="panel">
        <div class="panel-title">
          <b>组织详情</b>
          <div v-if="selectedDept?.nodeType === 'DEPT'">
            <el-button link type="primary" @click="openEdit(selectedDept)">编辑</el-button
            ><el-button v-if="auth.isAdmin" link type="danger" @click="remove(selectedDept)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!selectedDept" description="请在左侧选择公安处、派出所、重点单位或重要部位" />
        <el-descriptions v-else-if="selectedDept.nodeType === 'DEPT'" :column="2" border>
          <el-descriptions-item label="部门名称">{{ selectedDept.deptName }}</el-descriptions-item>
          <el-descriptions-item label="部门类型">{{
            deptTypeLabels[selectedDept.deptType || ''] || selectedDept.deptType
          }}</el-descriptions-item>
          <template v-if="selectedDept.deptType === 'BUREAU'">
            <el-descriptions-item label="负责人">{{ selectedDept.leader || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ selectedDept.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ selectedDept.address || '-' }}</el-descriptions-item>
            <el-descriptions-item label="管辖范围" :span="2">{{
              selectedDept.jurisdiction || '-'
            }}</el-descriptions-item>
          </template>
        </el-descriptions>
        <el-descriptions v-else-if="selectedDept.nodeType === 'KEY_UNIT'" :column="2" border>
          <el-descriptions-item label="重点单位名称">{{ selectedDept.unitName }}</el-descriptions-item>
          <el-descriptions-item label="单位类型">{{ selectedDept.unitType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ selectedDept.leader || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联络员">{{ selectedDept.contactPerson || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ selectedDept.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="确定时间">{{ selectedDept.establishedDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ selectedDept.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="管辖派出所" :span="2">{{
            selectedDept.stationNames?.join('、') || '仅公安处检查'
          }}</el-descriptions-item>
        </el-descriptions>
        <el-descriptions v-else :column="2" border>
          <el-descriptions-item label="重要部位名称">{{ selectedDept.partName }}</el-descriptions-item>
          <el-descriptions-item label="部位类型">{{ selectedDept.partType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="责任单位">{{ selectedDept.responsibleUnit || '-' }}</el-descriptions-item>
          <el-descriptions-item label="责任车间">{{ selectedDept.workshop || '-' }}</el-descriptions-item>
          <el-descriptions-item label="线别">{{ selectedDept.railwayLine || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所处地域">{{ selectedDept.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="管辖派出所" :span="2">{{
            selectedDept.stationNames?.join('、') || '仅公安处检查'
          }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </section>

    <section v-else-if="section === 'dicts'" class="system-split dict-layout">
      <div class="panel">
        <div class="panel-title">
          <b>字典类型</b><el-button link type="primary" @click="openDictType()">新增</el-button>
        </div>
        <div
          v-for="item in dictTypes"
          :key="item.id"
          class="dict-type-item"
          :class="{ active: selectedDictType === item.dictType }"
          @click="selectDict(item.dictType)"
        >
          <span
            >{{ item.dictName }}<small>{{ item.dictType }}</small></span
          >
          <span
            ><el-button link @click.stop="openDictType(item)">编辑</el-button
            ><el-button link type="danger" @click.stop="deleteDictType(item)">删除</el-button></span
          >
        </div>
      </div>
      <div class="panel">
        <div class="panel-title">
          <b>字典数据</b
          ><el-button link type="primary" :disabled="!selectedDictType" @click="openDictData()">新增数据项</el-button>
        </div>
        <el-table :data="dictData" table-layout="auto">
          <el-table-column prop="dictLabel" label="标签" /><el-table-column prop="dictValue" label="值" />
          <el-table-column prop="sortNo" label="排序" width="80" /><el-table-column
            prop="colorType"
            label="颜色"
            width="100"
          />
          <el-table-column label="操作" width="130"
            ><template #default="{ row }"
              ><el-button link @click="openDictData(row)">编辑</el-button
              ><el-button link type="danger" @click="deleteDict(row)">删除</el-button></template
            ></el-table-column
          >
        </el-table>
      </div>
    </section>

    <section v-else class="panel">
      <div v-if="section === 'users' && auth.isAdmin" class="account-coverage-strip">
        <span
          >公安处账号覆盖
          <b>{{ adminOverview.accountCovered?.BUREAU || 0 }}/{{ adminOverview.deptCounts?.BUREAU || 0 }}</b></span
        >
        <span
          >派出所账号覆盖
          <b>{{ adminOverview.accountCovered?.STATION || 0 }}/{{ adminOverview.deptCounts?.STATION || 0 }}</b></span
        >
        <span :class="{ danger: adminOverview.missingAccountCount }"
          >缺失 <b>{{ adminOverview.missingAccountCount || 0 }}</b></span
        >
      </div>
      <div v-if="['users', 'logs', 'login-logs'].includes(section)" class="filter-bar">
        <el-input
          v-if="section === 'users'"
          v-model="query.keyword"
          placeholder="账号或部门"
          clearable
          style="width: 220px"
        />
        <el-select
          v-if="section === 'users'"
          v-model="query.deptType"
          clearable
          placeholder="部门类型"
          style="width: 140px"
        >
          <el-option v-for="(label, value) in deptTypeLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <el-input v-else v-model="query.username" placeholder="操作账号" clearable style="width: 180px" />
        <el-input v-if="section === 'logs'" v-model="query.module" placeholder="模块" clearable style="width: 150px" />
        <el-select v-model="query.status" clearable placeholder="状态" style="width: 130px">
          <el-option label="成功/正常" :value="1" /><el-option label="失败/停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="searchSystem">查询</el-button>
        <el-button @click="resetSystemQuery">重置</el-button>
      </div>

      <el-table v-if="section === 'users'" v-loading="loading" :data="rows" stripe table-layout="auto">
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="deptName" label="部门" min-width="180" /><el-table-column label="角色" min-width="150"
          ><template #default="{ row }">{{
            row.roles?.map((role: string) => roleCodeLabels[role] || role).join('、')
          }}</template></el-table-column
        >
        <el-table-column label="最近登录" width="170"
          ><template #default="{ row }">{{ formatDateTime(row.lastLoginTime) || '-' }}</template></el-table-column
        >
        <el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
              row.status === 1 ? '正常' : '停用'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column v-if="auth.isAdmin" label="操作" width="210" fixed="right"
          ><template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button
            ><el-button link @click="resetPassword(row)">重置密码</el-button>
            <el-button link type="warning" @click="toggleUser(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button
            ><el-button link type="danger" @click="remove(row)">删除</el-button>
          </template></el-table-column
        >
      </el-table>

      <el-table v-else-if="section === 'roles'" :data="rows" stripe table-layout="auto">
        <el-table-column prop="roleName" label="角色名称" /><el-table-column prop="roleCode" label="角色编码" />
        <el-table-column label="数据范围"
          ><template #default="{ row }">{{
            dataScopeLabels[row.dataScope] || row.dataScope
          }}</template></el-table-column
        ><el-table-column label="状态"
          ><template #default="{ row }">{{ row.status === 1 ? '正常' : '停用' }}</template></el-table-column
        >
        <el-table-column label="操作" width="160"
          ><template #default="{ row }"
            ><el-button link @click="openEdit(row)">编辑授权</el-button
            ><el-button link type="danger" @click="remove(row)">删除</el-button></template
          ></el-table-column
        >
      </el-table>

      <el-table
        v-else-if="section === 'menus'"
        :data="buildMenuTree(menus)"
        row-key="id"
        default-expand-all
        table-layout="auto"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="180" /><el-table-column
          prop="menuType"
          label="类型"
          width="80"
        />
        <el-table-column prop="path" label="路由" min-width="180" /><el-table-column
          prop="permission"
          label="权限标识"
          min-width="180"
        />
        <el-table-column label="状态" width="80"
          ><template #default="{ row }">{{ row.status === 1 ? '正常' : '停用' }}</template></el-table-column
        >
        <el-table-column label="操作" width="140"
          ><template #default="{ row }"
            ><el-button link @click="openEdit(row)">编辑</el-button
            ><el-button link type="danger" @click="remove(row)">删除</el-button></template
          ></el-table-column
        >
      </el-table>

      <div v-else-if="section === 'config'" class="config-group-list">
        <section v-for="group in configGroups" :key="group.title" class="config-group">
          <div class="config-group-title">
            <b>{{ group.title }}</b
            ><span>{{ group.description }}</span>
          </div>
          <div v-for="item in group.items" :key="item.id" class="config-row">
            <div>
              <b>{{ item.configName }}</b
              ><small>{{ item.configKey }}</small>
              <p>{{ item.remark || '系统运行参数' }}</p>
            </div>
            <el-select v-if="item.valueType === 'PERIOD'" v-model="item.configValue" style="width: 260px">
              <el-option
                v-for="option in periodBoundaryOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-switch
              v-else-if="item.valueType === 'BOOLEAN'"
              v-model="item.configValue"
              active-value="true"
              inactive-value="false"
            />
            <el-input v-else v-model="item.configValue" style="width: 260px">
              <template v-if="item.configKey.startsWith('coverage.')" #append>%</template>
              <template v-else-if="item.configKey.endsWith('.max.mb')" #append>MB</template>
              <template v-else-if="item.configKey.endsWith('.minutes')" #append>分钟</template>
            </el-input>
          </div>
        </section>
      </div>

      <el-table v-else-if="section === 'logs'" :data="rows" stripe table-layout="auto">
        <el-table-column prop="username" label="操作人" width="120" /><el-table-column
          prop="module"
          label="模块"
          width="120"
        />
        <el-table-column prop="operationType" label="操作类型" width="100" /><el-table-column
          prop="requestUri"
          label="请求地址"
          min-width="250"
        />
        <el-table-column prop="requestIp" label="IP" width="130" /><el-table-column label="结果" width="80"
          ><template #default="{ row }"
            ><el-tag :type="row.result === 1 ? 'success' : 'danger'">{{
              row.result === 1 ? '成功' : '失败'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作时间" width="170"
          ><template #default="{ row }">{{ formatDateTime(row.createTime) || '-' }}</template></el-table-column
        >
      </el-table>

      <el-table v-else :data="rows" stripe table-layout="auto">
        <el-table-column prop="username" label="账号" width="140" /><el-table-column
          prop="loginIp"
          label="登录IP"
          width="150"
        />
        <el-table-column prop="browser" label="客户端" min-width="260" show-overflow-tooltip /><el-table-column
          label="结果"
          width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
              row.status === 1 ? '成功' : '失败'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="message" label="说明" min-width="150" /><el-table-column label="登录时间" width="170"
          ><template #default="{ row }">{{ formatDateTime(row.loginTime) || '-' }}</template></el-table-column
        >
      </el-table>
      <div v-if="['users', 'logs', 'login-logs'].includes(section)" class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑' : '新增'" width="720px">
      <el-form label-width="110px">
        <template v-if="section === 'users'">
          <div class="form-grid">
            <el-form-item label="账号" required
              ><el-input v-model="form.username" :disabled="!!editingId"
            /></el-form-item>
            <el-form-item v-if="!editingId" label="初始密码" required
              ><el-input v-model="form.password" type="password" show-password maxlength="32" show-word-limit
            /></el-form-item>
            <el-form-item label="账号类型" required>
              <el-radio-group v-model="form.accountType" :disabled="!!editingId" @change="applyAccountTypePolicy()">
                <el-radio-button v-if="editingId && form.accountType === 'ADMIN'" value="ADMIN"
                  >系统管理员</el-radio-button
                >
                <el-radio-button value="BUREAU">公安处账号</el-radio-button>
                <el-radio-button value="STATION">派出所账号</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.accountType === 'STATION'" label="所属公安处" required>
              <el-select v-model="form.parentDeptId" filterable style="width: 100%" @change="applyParentDeptPolicy">
                <el-option v-for="d in bureauDeptOptions" :key="d.id" :label="d.label" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="form.accountType === 'STATION' ? '账号对应派出所' : '账号对应公安处'" required>
              <el-select v-model="form.deptId" filterable style="width: 100%">
                <el-option v-for="d in accountDeptOptions" :key="d.id" :label="d.label" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据范围"
              ><el-input :model-value="dataScopeLabels[form.dataScope || ''] || form.dataScope" disabled
            /></el-form-item>
          </div>
          <el-alert
            v-if="form.accountType === 'STATION'"
            type="info"
            :closable="false"
            title="派出所账号隶属于所选公安处，但数据权限绑定到具体派出所。"
          />
        </template>
        <template v-else-if="section === 'depts'">
          <div class="form-grid">
            <el-form-item label="部门名称" required><el-input v-model="form.deptName" /></el-form-item>
            <el-form-item label="部门类型" required
              ><el-select v-model="form.deptType" style="width: 100%"
                ><el-option label="公安处" value="BUREAU" /><el-option label="派出所" value="STATION" /></el-select
            ></el-form-item>
            <el-form-item label="上级部门"
              ><el-select v-model="form.parentId" filterable style="width: 100%"
                ><el-option label="无" :value="0" /><el-option
                  v-for="d in flatDepts"
                  :key="d.id"
                  :label="d.label"
                  :value="d.id" /></el-select
            ></el-form-item>
            <el-form-item v-if="form.deptType !== 'STATION'" label="负责人"
              ><el-input v-model="form.leader"
            /></el-form-item>
            <el-form-item v-if="form.deptType !== 'STATION'" label="联系电话"
              ><el-input v-model="form.phone"
            /></el-form-item>
            <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" /></el-form-item>
          </div>
          <template v-if="form.deptType !== 'STATION'"
            ><el-form-item label="地址"><el-input v-model="form.address" /></el-form-item
            ><el-form-item label="管辖范围"><el-input v-model="form.jurisdiction" type="textarea" /></el-form-item
          ></template>
        </template>
        <template v-else-if="section === 'roles'">
          <div class="form-grid">
            <el-form-item label="角色名称" required><el-input v-model="form.roleName" /></el-form-item
            ><el-form-item label="角色编码" required><el-input v-model="form.roleCode" /></el-form-item>
            <el-form-item label="数据范围"
              ><el-select v-model="form.dataScope" style="width: 100%"
                ><el-option label="全部" value="ALL" /><el-option
                  label="本部门及下级"
                  value="DEPT_AND_CHILD" /><el-option label="本部门" value="DEPT" /></el-select
            ></el-form-item>
          </div>
          <el-form-item label="菜单权限"
            ><el-tree
              ref="menuTreeRef"
              :data="buildMenuTree(menus)"
              node-key="id"
              show-checkbox
              default-expand-all
              :props="{ label: 'menuName' }"
          /></el-form-item>
        </template>
        <template v-else>
          <div class="form-grid">
            <el-form-item label="菜单名称" required><el-input v-model="form.menuName" /></el-form-item
            ><el-form-item label="菜单类型"
              ><el-select v-model="form.menuType"
                ><el-option label="目录" value="M" /><el-option label="菜单" value="C" /><el-option
                  label="按钮"
                  value="F" /></el-select
            ></el-form-item>
            <el-form-item label="上级菜单"
              ><el-select v-model="form.parentId"
                ><el-option label="根目录" :value="0" /><el-option
                  v-for="m in menus"
                  :key="m.id"
                  :label="m.menuName"
                  :value="m.id" /></el-select></el-form-item
            ><el-form-item label="排序"><el-input-number v-model="form.sortNo" /></el-form-item>
            <el-form-item label="路由"><el-input v-model="form.path" /></el-form-item
            ><el-form-item label="组件"><el-input v-model="form.component" /></el-form-item>
            <el-form-item label="权限标识"><el-input v-model="form.permission" /></el-form-item
            ><el-form-item label="图标"><el-input v-model="form.icon" /></el-form-item>
          </div>
        </template>
      </el-form>
      <template #footer
        ><el-button @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      >
    </el-dialog>

    <el-dialog v-model="secondaryDialog" :title="secondaryForm.kind === 'type' ? '字典类型' : '字典数据'" width="520px">
      <el-form label-width="100px">
        <template v-if="secondaryForm.kind === 'type'"
          ><el-form-item label="字典名称"><el-input v-model="secondaryForm.dictName" /></el-form-item
          ><el-form-item label="字典编码"
            ><el-input v-model="secondaryForm.dictType" :disabled="!!secondaryForm.id" /></el-form-item
        ></template>
        <template v-else
          ><el-form-item label="标签"><el-input v-model="secondaryForm.dictLabel" /></el-form-item
          ><el-form-item label="值"><el-input v-model="secondaryForm.dictValue" /></el-form-item
          ><el-form-item label="排序"><el-input-number v-model="secondaryForm.sortNo" /></el-form-item
          ><el-form-item label="颜色"><el-input v-model="secondaryForm.colorType" /></el-form-item
        ></template>
      </el-form>
      <template #footer
        ><el-button @click="secondaryDialog = false">取消</el-button
        ><el-button type="primary" @click="saveSecondary">保存</el-button></template
      >
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-metrics {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.admin-metrics > div {
  padding: 20px;
  border: 1px solid #dfe7f1;
  border-radius: 6px;
  background: linear-gradient(135deg, #fff, #f5f9ff);
}
.admin-metrics span,
.admin-metrics small {
  display: block;
  color: #64748b;
}
.admin-metrics b {
  display: block;
  margin: 8px 0;
  font-size: 30px;
  color: #174f8f;
}
.admin-metrics .danger b,
.account-coverage-strip .danger,
.admin-task-stats .danger b {
  color: #c53d3d;
}
.admin-rule-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.admin-task-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.admin-task-stats span {
  padding: 18px;
  background: #f5f8fc;
  border-radius: 5px;
}
.admin-task-stats b {
  display: block;
  margin-top: 8px;
  font-size: 24px;
  color: #174f8f;
}
.admin-maintain-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}
.rule-list {
  margin: 0;
  padding-left: 20px;
  color: #526173;
  line-height: 2;
}
.account-coverage-strip {
  display: flex;
  gap: 28px;
  margin-bottom: 16px;
  padding: 14px 18px;
  border: 1px solid #dfe7f1;
  background: #f7faff;
}
.config-group-list {
  display: grid;
  gap: 18px;
}
.config-group {
  border: 1px solid #dfe7f1;
  border-radius: 6px;
  overflow: hidden;
}
.config-group-title {
  display: flex;
  justify-content: space-between;
  padding: 14px 18px;
  background: #edf4fc;
}
.config-group-title span {
  color: #64748b;
  font-size: 13px;
}
.config-row {
  display: grid;
  grid-template-columns: minmax(300px, 1fr) auto;
  align-items: center;
  gap: 20px;
  padding: 15px 18px;
  border-top: 1px solid #edf0f4;
}
.config-row:first-of-type {
  border-top: 0;
}
.config-row small,
.config-row p {
  display: block;
  margin: 3px 0 0;
  color: #7b8794;
  font-size: 12px;
}
@media (max-width: 1600px) {
  .admin-metrics {
    grid-template-columns: repeat(4, 1fr);
  }
}
@media (max-width: 1200px) {
  .admin-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
  .admin-rule-grid {
    grid-template-columns: 1fr;
  }
  .admin-task-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
