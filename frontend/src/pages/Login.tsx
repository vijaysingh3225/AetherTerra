import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { apiFetch } from '../lib/api'

interface LoginResponse {
  token: string
  email: string
  role: string
}

export function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()

  if (user) return <Navigate to="/" replace />
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const res = await apiFetch<LoginResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      login(res.token, res.email, res.role)
      navigate(res.role === 'ADMIN' ? '/admin' : '/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="surface-panel mx-auto max-w-sm p-8">
      <p className="eyebrow-label mb-4 text-xs font-medium">Bidder Portal</p>
      <h2 className="mb-2 text-2xl font-light text-[var(--text-primary)]">Sign In</h2>
      <p className="mb-6 text-sm text-[var(--text-secondary)]">Return to your bidder account.</p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && (
          <p className="notice-danger px-4 py-2 text-sm">{error}</p>
        )}
        <div>
          <label className="mb-1 block text-xs font-medium text-[var(--text-secondary)]">Email</label>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="field-shell w-full rounded px-3 py-2 text-sm outline-none"
            placeholder="you@example.com"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-[var(--text-secondary)]">Password</label>
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="field-shell w-full rounded px-3 py-2 text-sm outline-none"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="btn-primary py-2.5 text-sm font-medium transition-all disabled:opacity-50"
        >
          {loading ? 'Signing in...' : 'Sign In'}
        </button>
      </form>
      <p className="mt-5 text-center text-sm text-[var(--text-secondary)]">
        Don't have an account?{' '}
        <Link to="/register" className="accent-link underline underline-offset-4">Register</Link>
      </p>
    </div>
  )
}
