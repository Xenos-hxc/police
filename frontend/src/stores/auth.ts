import { defineStore } from 'pinia'
import { authApi, systemApi } from '@/api'
import type { SystemPeriodInfo, UserInfo } from '@/types'
import { clearAccessToken, setAccessToken } from '@/utils/token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as UserInfo | null,
    systemPeriod: null as SystemPeriodInfo | null,
    loaded: false
  }),
  getters: {
    roles: (state) => state.user?.roles || [],
    isAdmin: (state) => state.user?.roles.includes('ADMIN') || false
  },
  actions: {
    async login(form: { username: string; password: string; captcha?: string; captchaId?: string }) {
      const response = await authApi.login(form)
      setAccessToken(response.data.accessToken)
      await this.fetchUser()
    },
    async fetchUser() {
      const [response, periodResponse] = await Promise.all([authApi.me(), systemApi.period()])
      this.user = response.data
      this.systemPeriod = periodResponse.data
      this.loaded = true
    },
    async fetchSystemPeriod() {
      const response = await systemApi.period()
      this.systemPeriod = response.data
      return response.data
    },
    hasAnyRole(roles?: string[]) {
      return !roles?.length || roles.some((role) => this.roles.includes(role))
    },
    hasPermission(permission?: string) {
      return !permission || this.user?.permissions.includes(permission) || this.roles.includes('ADMIN')
    },
    clearSession() {
      clearAccessToken()
      this.user = null
      this.systemPeriod = null
      this.loaded = false
    },
    async logout() {
      try {
        await authApi.logout()
      } catch {
        /* Expired sessions are already logged out. */
      } finally {
        this.clearSession()
      }
    }
  }
})
