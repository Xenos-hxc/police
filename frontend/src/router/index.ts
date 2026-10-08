import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '首页', roles: ['BUREAU', 'STATION'] }
        },
        {
          path: 'tasks/list',
          component: () => import('@/views/TaskListView.vue'),
          meta: { title: '任务列表', roles: ['BUREAU', 'STATION'] }
        },
        {
          path: 'tasks/:id',
          component: () => import('@/views/TaskDetailView.vue'),
          meta: { title: '任务详情', roles: ['BUREAU', 'STATION'] }
        },
        {
          path: 'station-inspections',
          component: () => import('@/views/StationInspectionOverviewView.vue'),
          meta: { title: '各所检查详情', roles: ['BUREAU'] }
        },
        {
          path: 'station-inspections/:deptId',
          component: () => import('@/views/StationInspectionDetailView.vue'),
          meta: { title: '派出所检查详情', roles: ['BUREAU'] }
        },
        {
          path: 'hidden-dangers',
          component: () => import('@/views/HiddenDangerListView.vue'),
          meta: { title: '隐患整改', roles: ['BUREAU', 'STATION'] }
        },
        {
          path: 'hidden-dangers/:id',
          component: () => import('@/views/HiddenDangerDetailView.vue'),
          meta: { title: '隐患整改详情', roles: ['BUREAU', 'STATION'] }
        },
        { path: 'archives/bureau', redirect: '/archives/stations' },
        {
          path: 'archives/stations',
          component: () => import('@/views/ArchiveView.vue'),
          meta: { title: '派出所档案', archiveType: 'police-stations', roles: ['ADMIN', 'BUREAU', 'STATION'] }
        },
        {
          path: 'archives/units',
          component: () => import('@/views/ArchiveView.vue'),
          meta: { title: '重点单位档案', archiveType: 'key-units', roles: ['ADMIN', 'BUREAU', 'STATION'] }
        },
        {
          path: 'archives/parts',
          component: () => import('@/views/ArchiveView.vue'),
          meta: { title: '重要部位档案', archiveType: 'important-parts', roles: ['ADMIN', 'BUREAU', 'STATION'] }
        },
        {
          path: 'archives/:type/:id/history',
          component: () => import('@/views/ArchiveHistoryView.vue'),
          meta: { title: '档案检查历史' }
        },
        {
          path: 'statistics',
          component: () => import('@/views/StatisticsView.vue'),
          meta: { title: '统计分析', roles: ['ADMIN', 'BUREAU', 'STATION'] }
        },
        {
          path: 'system/unit-department/units',
          component: () => import('@/views/ArchiveView.vue'),
          meta: { title: '单位和部门管理', archiveType: 'key-units', roles: ['ADMIN'] }
        },
        {
          path: 'system/unit-department/parts',
          component: () => import('@/views/ArchiveView.vue'),
          meta: { title: '单位和部门管理', archiveType: 'important-parts', roles: ['ADMIN'] }
        },
        {
          path: 'system/:section?',
          component: () => import('@/views/SystemView.vue'),
          meta: { title: '系统管理', roles: ['ADMIN'] }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

const archiveHistoryRoles = (type: unknown) => {
  if (['police-stations', 'key-units', 'important-parts'].includes(String(type))) return ['ADMIN', 'BUREAU', 'STATION']
  return []
}

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const auth = useAuthStore()
  if (!auth.loaded) {
    try {
      await auth.fetchUser()
    } catch {
      return '/login'
    }
  }
  const fallback = auth.isAdmin ? '/system/admin' : '/dashboard'
  if (auth.isAdmin && to.path === '/dashboard') return fallback
  if (to.path.startsWith('/archives/') && to.params.type) {
    const roles = archiveHistoryRoles(to.params.type)
    if (!roles.length) return fallback
    if (!auth.hasAnyRole(roles)) return fallback
  }
  if (!auth.hasAnyRole(to.meta.roles as string[] | undefined)) return fallback
  if (to.path.startsWith('/system/')) {
    const adminOnly = ['admin', 'roles', 'menus', 'config', 'dicts', 'logs', 'login-logs']
    const section = String(to.params.section || '')
    if (adminOnly.includes(section) && !auth.isAdmin) return fallback
  }
  return true
})

export default router
