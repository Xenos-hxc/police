import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/utils/token'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  withCredentials: true
})

let refreshing = false
let waiters: Array<{ resolve: (token: string) => void; reject: (error: unknown) => void }> = []

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body?.code && body.code !== 200) {
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const isLoginRequest = original?.url?.includes('/auth/login')
    const isRefreshRequest = original?.url?.includes('/auth/refresh-token')
    if (error.response?.status === 401 && !isLoginRequest && !isRefreshRequest && !original._retry) {
      original._retry = true
      if (!refreshing) {
        refreshing = true
        try {
          const response = await axios.post(
            `${import.meta.env.VITE_API_BASE_URL}/auth/refresh-token`,
            {},
            {
              withCredentials: true
            }
          )
          const token = response.data.data.accessToken
          setAccessToken(token)
          waiters.forEach((waiter) => waiter.resolve(token))
          waiters = []
        } catch (refreshError) {
          waiters.forEach((waiter) => waiter.reject(refreshError))
          waiters = []
          clearAccessToken()
          location.href = '/login'
          return Promise.reject(error)
        } finally {
          refreshing = false
        }
      }
      const token = refreshing
        ? await new Promise<string>((resolve, reject) => waiters.push({ resolve, reject }))
        : getAccessToken()
      original.headers.Authorization = `Bearer ${token}`
      return service(original)
    }
    const responseData = error.response?.data
    let message = errorMessage(responseData)
    if (!message && responseData instanceof Blob && responseData.type.includes('application/json')) {
      try {
        message = JSON.parse(await responseData.text())?.message
      } catch {
        // Keep the generic request error when a proxy returns malformed JSON.
      }
    }
    const timeout = error.code === 'ECONNABORTED' || error.message?.includes('timeout')
    if (error.response?.status !== 401) {
      ElMessage.error(message || (timeout ? '请求超时，请缩小查询范围或稍后重试' : '网络异常或服务不可用'))
    }
    return Promise.reject(error)
  }
)

export default service

function errorMessage(value: unknown): string | undefined {
  if (typeof value !== 'object' || value === null || !('message' in value)) return undefined
  const message = (value as { message?: unknown }).message
  return typeof message === 'string' ? message : undefined
}
