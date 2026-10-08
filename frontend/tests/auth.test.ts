import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/stores/auth'
import { getAccessToken } from '../src/utils/token'

const api = vi.hoisted(() => ({
  login: vi.fn(),
  me: vi.fn(),
  period: vi.fn(),
  logout: vi.fn()
}))

vi.mock('../src/api', () => ({
  authApi: { login: api.login, me: api.me, logout: api.logout },
  systemApi: { period: api.period }
}))

describe('authentication session', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    api.login.mockResolvedValue({ data: { accessToken: 'session-token' } })
    api.me.mockResolvedValue({
      data: {
        id: 1,
        username: 'admin',
        realName: 'Admin',
        deptId: 1,
        deptName: 'Office',
        roles: ['ADMIN'],
        permissions: []
      }
    })
    api.period.mockResolvedValue({
      data: {
        startYear: 2026,
        startQuarter: 1,
        currentYear: 2026,
        currentQuarter: 3,
        effectiveYear: 2026,
        effectiveQuarter: 3
      }
    })
  })

  it('loads identity after login and keeps the access token in memory', async () => {
    const store = useAuthStore()
    await store.login({ username: 'admin', password: 'secret' })
    expect(api.login).toHaveBeenCalledWith({ username: 'admin', password: 'secret' })
    expect(store.loaded).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(store.hasAnyRole(['BUREAU', 'ADMIN'])).toBe(true)
    expect(getAccessToken()).toBe('session-token')
  })

  it('does not mark identity loaded if the profile request fails', async () => {
    api.me.mockRejectedValue(new Error('offline'))
    const store = useAuthStore()
    await expect(store.login({ username: 'admin', password: 'secret' })).rejects.toThrow('offline')
    expect(store.loaded).toBe(false)
    store.clearSession()
    expect(getAccessToken()).toBe('')
  })

  it('clears the local session even when server logout fails', async () => {
    const store = useAuthStore()
    await store.login({ username: 'admin', password: 'secret' })
    api.logout.mockRejectedValue(new Error('expired'))
    await store.logout()
    expect(store.user).toBeNull()
    expect(store.loaded).toBe(false)
    expect(getAccessToken()).toBe('')
  })
})
