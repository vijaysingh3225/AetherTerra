import { createContext, useContext, useState } from 'react'
import type { ReactNode } from 'react'

interface AuthUser {
  email: string
  role: string
}

interface AuthContextValue {
  user: AuthUser | null
  login: (token: string, email: string, role: string) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    try {
      const stored = localStorage.getItem('at_user')
      return stored ? JSON.parse(stored) : null
    } catch {
      return null
    }
  })

  const login = (token: string, email: string, role: string) => {
    localStorage.setItem('at_token', token)
    localStorage.setItem('at_user', JSON.stringify({ email, role }))
    setUser({ email, role })
  }

  const logout = () => {
    localStorage.removeItem('at_token')
    localStorage.removeItem('at_user')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
