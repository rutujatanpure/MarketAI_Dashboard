/**
 * AuthContext.jsx
 *
 * IDENTICAL to user's existing AuthContext — zero logic changes.
 *
 * The only thing that enables the role-based redirect in Login.jsx is that
 * login() and register() already return `data`. That was already in the
 * user's version. No changes needed here — this file is provided as the
 * clean reference copy.
 */
import { createContext, useContext, useState, useCallback } from 'react'
import { authService } from '../services/authService'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('cs_user')) } catch { return null }
  })

  const login = useCallback(async (email, password) => {
    const data = await authService.login(email, password)
    setUser(data)
    localStorage.setItem('cs_user', JSON.stringify(data))
    return data   // ← Login.jsx reads userData.role from this
  }, [])

  const register = useCallback(async (payload) => {
    const data = await authService.register(payload)
    setUser(data)
    localStorage.setItem('cs_user', JSON.stringify(data))
    return data   // ← Register.jsx can use same pattern if needed
  }, [])

  const logout = useCallback(() => {
    setUser(null)
    localStorage.removeItem('cs_user')
  }, [])

  const getToken = useCallback(() => user?.token ?? null, [user])

  const value = {
    user,
    isAdmin: user?.role === 'ADMIN',
    isAuth:  !!user,
    getToken,
    login,
    register,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}