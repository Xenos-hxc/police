export const quarterLabel = (value?: number | string | null) => {
  const quarter = Number(value)
  const labels: Record<number, string> = {
    1: '第一季度',
    2: '第二季度',
    3: '第三季度',
    4: '第四季度'
  }
  return labels[quarter] || ''
}

export const periodLabel = (year?: number | string | null, quarter?: number | string | null) => {
  const q = quarterLabel(quarter)
  return `${year || ''}年${q ? ` ${q}` : ''}`.trim()
}

export const formatDateTime = (value?: string | null) => {
  if (!value) return ''
  return String(value).replace('T', ' ')
}
