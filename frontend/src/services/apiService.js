import axios from 'axios'

// Fixed

const BASE_URL = import.meta.env.VITE_API_URL || 'https://marketai-dashboard-3.onrender.com'

export const apiService = axios.create({ baseURL: BASE_URL, timeout: 15000 })

// ── Helper: check if JWT is expired ──────────────────────────────────────────
function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp * 1000 < Date.now() + 10000
  } catch {
    return true
  }
}

// ── Attach JWT on every request ───────────────────────────────────────────────
apiService.interceptors.request.use(config => {
  const stored = localStorage.getItem('cs_user')  // ← cs_user (tumhara actual key)
  if (stored) {
    try {
      const { token } = JSON.parse(stored)
      if (token) {
        if (isTokenExpired(token)) {
          console.warn('[apiService] JWT expired — logging out')
          localStorage.removeItem('cs_user')
          setTimeout(() => { window.location.href = '/login' }, 50)
          return Promise.reject(new Error('JWT expired'))
        }
        config.headers.Authorization = `Bearer ${token}`
      }
    } catch (_) {}
  }
  return config
})

// ── Global response error handler ─────────────────────────────────────────────
apiService.interceptors.response.use(
  res => res,
  err => {
    const status = err.response?.status

    if (status === 401) {
      localStorage.removeItem('cs_user')
      if (window.__reactRouterNavigate) {
        window.__reactRouterNavigate('/login')
      } else {
        setTimeout(() => { window.location.href = '/login' }, 100)
      }
    }

    if (status === 403) {
      const url     = err.config?.url ?? '?'
      const hasAuth = !!err.config?.headers?.Authorization
      console.group(`[403] ${url}`)
      console.warn('Auth header present:', hasAuth)
      if (hasAuth) {
        try {
          const p = JSON.parse(atob(err.config.headers.Authorization.replace('Bearer ', '').split('.')[1]))
          console.warn('Token role:', p.role, '| expires:', new Date(p.exp * 1000).toLocaleString())
        } catch { console.warn('Could not decode token') }
      }
      console.groupEnd()
    }

    return Promise.reject(err)
  }
)
