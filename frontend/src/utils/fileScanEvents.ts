import { getAccessToken } from '@/utils/token'

export function watchFileScanEvents(taskId: number, onChange: () => void): () => void {
  let stopped = false
  let controller: AbortController | undefined
  let retryTimer: ReturnType<typeof setTimeout> | undefined
  let previous = ''

  const connect = async () => {
    if (stopped) return
    if (!getAccessToken()) {
      retryTimer = setTimeout(() => void connect(), 5000)
      return
    }
    controller = new AbortController()
    try {
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/files/events?taskId=${taskId}`, {
        headers: { Authorization: `Bearer ${getAccessToken()}`, Accept: 'text/event-stream' },
        credentials: 'include',
        signal: controller.signal
      })
      if (!response.ok || !response.body) throw new Error('状态订阅不可用')
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (!stopped) {
        const { done, value } = await reader.read()
        if (done) break
        buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
        const frames = buffer.split('\n\n')
        buffer = frames.pop() || ''
        for (const frame of frames) {
          const data = frame
            .split('\n')
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.slice(5).trim())
            .join('')
          if (data && data !== previous) {
            previous = data
            onChange()
          }
        }
      }
    } catch {
      // A periodic refresh on the page remains available during connection failures.
    }
    if (!stopped) retryTimer = setTimeout(() => void connect(), 5000)
  }

  void connect()
  return () => {
    stopped = true
    controller?.abort()
    if (retryTimer) clearTimeout(retryTimer)
  }
}
