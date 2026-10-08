<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi, dashboardApi, taskApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, quarterLabel } from '@/utils/format'
import type { CheckTask } from '@/types'

interface MenuItem {
  path?: string
  label: string
  icon?: string
  roles?: string[]
  permission?: string
  children?: MenuItem[]
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const alertCount = ref(0)
const taskAlerts = ref<CheckTask[]>([])
const reminderVisible = ref(false)
const overdueVisible = ref(false)
const overdueTasks = ref<CheckTask[]>([])
const overdueTotal = ref(0)
const overdueLoading = ref(false)
const overdueQuery = reactive({ page: 1, size: 8 })
const passwordVisible = ref(false)
const passwordChanging = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 1, max: 32, message: '新密码长度必须为1-32位', trigger: 'blur' }
  ],
  confirmPassword: [
    {
      validator: (_rule, value, callback) => {
        if (!value) callback(new Error('请再次输入新密码'))
        else if (value !== passwordForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const active = computed(() => route.path)
const title = computed(() => String(route.meta.title || '首页'))
const formatHeaderName = (value?: string) => {
  const name = value?.trim() || ''
  if (name === '演示公安处') return '演示公安处'
  return name.replace(/^演示公安处/, '').replace(/^演示铁路公安局/, '')
}
const displayDeptName = computed(() => formatHeaderName(auth.user?.deptName))
const displayUserName = computed(() => formatHeaderName(auth.user?.realName) || auth.user?.username || '')
const menus = computed<MenuItem[]>(() =>
  [
    {
      path: '/dashboard',
      label: '首页',
      icon: 'DataBoard',
      roles: ['BUREAU', 'STATION'],
      permission: 'dashboard:view'
    },
    {
      path: '/statistics',
      label: '统计分析',
      icon: 'TrendCharts',
      roles: ['ADMIN', 'BUREAU', 'STATION'],
      permission: 'statistics:view'
    },
    { path: '/tasks/list', label: '任务列表', icon: 'Tickets', roles: ['BUREAU', 'STATION'], permission: 'task:list' },
    {
      path: '/station-inspections',
      label: '各所检查详情',
      icon: 'DataAnalysis',
      roles: ['BUREAU'],
      permission: 'statistics:view'
    },
    {
      path: '/hidden-dangers',
      label: '隐患整改',
      icon: 'WarningFilled',
      roles: ['BUREAU', 'STATION'],
      permission: 'danger:list'
    },
    {
      label: '档案管理',
      icon: 'OfficeBuilding',
      children: [
        {
          path: '/archives/stations',
          label: '派出所档案',
          roles: ['ADMIN', 'BUREAU', 'STATION'],
          permission: 'station:list'
        },
        {
          path: '/archives/units',
          label: '重点单位档案',
          roles: ['ADMIN', 'BUREAU', 'STATION'],
          permission: 'unit:list'
        },
        {
          path: '/archives/parts',
          label: '重要部位档案',
          roles: ['ADMIN', 'BUREAU', 'STATION'],
          permission: 'part:list'
        }
      ]
    },
    {
      label: '组织权限',
      icon: 'User',
      roles: ['ADMIN'],
      permission: 'system:user:list',
      children: [
        { path: '/system/users', label: '账号管理', permission: 'system:user:list' },
        { path: '/system/depts', label: '组织架构', permission: 'system:user:list' },
        { path: '/system/unit-department/units', label: '单位和部门管理', permission: 'system:user:list' },
        { path: '/system/roles', label: '角色管理', roles: ['ADMIN'], permission: 'system:user:list' },
        { path: '/system/menus', label: '菜单管理', roles: ['ADMIN'], permission: 'system:user:list' }
      ]
    },
    {
      label: '系统管理',
      icon: 'Setting',
      roles: ['ADMIN'],
      permission: 'system:config:list',
      children: [
        { path: '/system/admin', label: '管理员总览', permission: 'system:config:list' },
        { path: '/system/config', label: '业务规则配置', permission: 'system:config:list' },
        { path: '/system/dicts', label: '数据字典', permission: 'system:config:list' },
        { path: '/system/logs', label: '操作日志', permission: 'system:config:list' },
        { path: '/system/login-logs', label: '登录日志', permission: 'system:config:list' }
      ]
    }
  ].filter((item) => auth.hasAnyRole(item.roles) && auth.hasPermission(item.permission))
)

const visibleChildren = (children: MenuItem[]) =>
  children.filter((item) => auth.hasAnyRole(item.roles) && auth.hasPermission(item.permission))

let alertRefreshTimer: number | undefined

const loadTaskAlerts = async () => {
  if (auth.isAdmin) return
  const response = await dashboardApi.warnings({ page: 1, size: 8 })
  alertCount.value = response.data.total || 0
  taskAlerts.value = response.data.records || []
}

const refreshTaskAlerts = () => loadTaskAlerts().catch(() => undefined)

const loadOverdueTasks = async () => {
  if (auth.isAdmin) return
  overdueLoading.value = true
  try {
    const response = await taskApi.overdueReminders(overdueQuery)
    overdueTasks.value = response.data.records || []
    overdueTotal.value = response.data.total || 0
    if (overdueTotal.value > 0) overdueVisible.value = true
  } finally {
    overdueLoading.value = false
  }
}

const openOverdueTask = (item: CheckTask) => {
  overdueVisible.value = false
  router.push(`/tasks/${item.id}`)
}

const openTaskAlert = (item: CheckTask) => {
  reminderVisible.value = false
  router.push(item.id ? `/tasks/${item.id}` : '/tasks/list')
}

const alertContent = (item: CheckTask) => {
  if (!item.id) return item.taskName || '本季度监督检查任务尚未筛选'
  if (item.status === 'OVERDUE' || Number(item.overdue) === 1) return '任务已逾期，请尽快提交检查视频和检查笔录'
  return '任务即将截止，请及时提交检查视频和检查笔录'
}

const logout = async () => {
  await auth.logout()
  router.replace('/login')
}

const openPasswordDialog = () => {
  Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  passwordVisible.value = true
  window.setTimeout(() => passwordFormRef.value?.clearValidate(), 0)
}

const changePassword = async () => {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  passwordChanging.value = true
  try {
    await authApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    passwordVisible.value = false
    auth.clearSession()
    await router.replace('/login')
  } finally {
    passwordChanging.value = false
  }
}

onMounted(async () => {
  if (auth.isAdmin) return
  await Promise.allSettled([loadTaskAlerts(), loadOverdueTasks()])
  alertRefreshTimer = window.setInterval(refreshTaskAlerts, 60_000)
})

watch(
  () => route.fullPath,
  () => {
    if (!auth.isAdmin) refreshTaskAlerts()
  }
)

onBeforeUnmount(() => {
  if (alertRefreshTimer) window.clearInterval(alertRefreshTimer)
})
</script>

<template>
  <el-container class="app-shell">
    <el-aside :width="collapsed ? '72px' : '248px'" class="app-aside">
      <div class="brand">
        <div class="brand-mark">铁</div>
        <div v-if="!collapsed" class="brand-text">
          <b>铁路公安</b>
          <span>治安保卫监督检查</span>
        </div>
      </div>
      <el-menu
        :default-active="active"
        router
        :collapse="collapsed"
        background-color="#0d2f5f"
        text-color="#dbe8ff"
        active-text-color="#ffffff"
      >
        <template v-for="item in menus" :key="item.label">
          <el-sub-menu v-if="item.children?.length" :index="item.label">
            <template #title>
              <el-icon><component :is="item.icon || 'Menu'" /></el-icon>
              <span>{{ item.label }}</span>
            </template>
            <el-menu-item v-for="child in visibleChildren(item.children)" :key="child.path" :index="child.path">
              {{ child.label }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.icon || 'Menu'" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-button link @click="collapsed = !collapsed"
            ><el-icon><Fold /></el-icon
          ></el-button>
          <h1>{{ title }}</h1>
        </div>
        <div class="header-right">
          <el-popover
            v-if="!auth.isAdmin"
            v-model:visible="reminderVisible"
            placement="bottom-end"
            width="360"
            trigger="click"
          >
            <template #reference>
              <el-badge :value="alertCount" :max="99" :hidden="!alertCount">
                <el-button circle
                  ><el-icon><Bell /></el-icon
                ></el-button>
              </el-badge>
            </template>
            <div class="notice-head">
              <b>当前任务提醒</b>
              <span>共 {{ alertCount }} 项</span>
            </div>
            <div v-if="!taskAlerts.length" class="empty-inline">暂无需要处理的任务</div>
            <div
              v-for="(item, index) in taskAlerts"
              :key="item.id || `${item.targetType}-${index}`"
              class="notice-item"
              @click="openTaskAlert(item)"
            >
              <b>{{ item.targetName }}</b>
              <p>{{ alertContent(item) }}</p>
              <span
                >{{ item.taskYear }} 年 {{ quarterLabel(item.quarter) }} · 截止
                {{ formatDateTime(item.deadline) }}</span
              >
            </div>
          </el-popover>
          <span class="dept-name">{{ displayDeptName }}</span>
          <el-dropdown>
            <span class="user-dropdown"
              >{{ displayUserName }}<el-icon><ArrowDown /></el-icon
            ></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openPasswordDialog">修改密码</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="passwordVisible" title="修改密码" width="460px" destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            maxlength="32"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            maxlength="32"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            maxlength="32"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordChanging" @click="changePassword">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="overdueVisible"
      title="逾期任务提醒"
      width="920px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="`当前账号有 ${overdueTotal} 项逾期未完成任务，请及时补交检查视频和检查笔录。`"
      />
      <el-table v-loading="overdueLoading" :data="overdueTasks" stripe table-layout="auto" class="overdue-table">
        <el-table-column prop="targetName" label="检查对象" min-width="220" show-overflow-tooltip />
        <el-table-column label="检查周期" width="150">
          <template #default="{ row }">{{ row.taskYear }}年 {{ quarterLabel(row.quarter) }}</template>
        </el-table-column>
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"
            ><el-button link type="primary" @click="openOverdueTask(row)">立即提交</el-button></template
          >
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="overdueQuery.page"
          v-model:page-size="overdueQuery.size"
          layout="total, prev, pager, next"
          :total="overdueTotal"
          @change="loadOverdueTasks"
        />
      </div>
      <template #footer><el-button @click="overdueVisible = false">稍后处理</el-button></template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.overdue-table {
  margin-top: 16px;
}
</style>
