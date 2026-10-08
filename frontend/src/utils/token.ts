let accessToken = ''

export const getAccessToken = () => accessToken

export const setAccessToken = (value?: string | null) => {
  accessToken = value || ''
}

export const clearAccessToken = () => {
  accessToken = ''
}
