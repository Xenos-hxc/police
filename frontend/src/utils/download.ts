import request from '@/utils/request'

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export async function authenticatedDownload(url: string, filename: string) {
  const blob = await request.get<unknown, Blob>(url, { responseType: 'blob' })
  saveBlob(blob, filename)
}

export async function authenticatedFileUrl(url: string) {
  const blob = await request.get<unknown, Blob>(url, { responseType: 'blob' })
  return URL.createObjectURL(blob)
}

export async function authenticatedNativeDownload(url: string, filename: string) {
  const response = await request.post<unknown, { data: { downloadUrl: string } }>(url)
  const link = document.createElement('a')
  link.href = response.data.downloadUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
}
