import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { apiFetch } from '../lib/api'

export function Register() {
  const { user } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  if (user) return <Navigate to="/" replace />

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await apiFetch('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      setSubmitted(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  if (submitted) {
    return (
      <div className="surface-panel mx-auto max-w-sm rounded-[1.75rem] p-8 text-center">
        <div className="mb-4 text-4xl text-[var(--champagne)]">✦</div>
        <h2 className="mb-2 text-2xl font-semibold text-[var(--text-primary)]">Check your email</h2>
        <p className="text-[var(--text-secondary)]">
          We sent a verification link to <span className="font-medium text-[var(--text-primary)]">{email}</span>.
          Click it to activate your account.
        </p>
        <p className="mt-4 text-sm text-[var(--text-tertiary)]">
          Didn't get it? Check your spam folder, or{' '}
          <button
            onClick={() => setSubmitted(false)}
            className="accent-link underline"
          >
            try again
          </button>
          .
        </p>
      </div>
    )
  }

  return (
    <div className="surface-panel mx-auto max-w-sm rounded-[1.75rem] p-8">
      <h2 className="mb-2 text-2xl font-semibold text-[var(--text-primary)]">Create Account</h2>
      <p className="mb-6 text-sm text-[var(--text-secondary)]">Join the auction floor.</p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && (
          <p className="notice-danger rounded-xl px-4 py-2 text-sm">{error}</p>
        )}
        <div>
          <label className="mb-1 block text-sm font-medium text-[var(--text-secondary)]">Email</label>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="field-shell w-full rounded-xl px-3 py-2 text-sm outline-none"
            placeholder="you@example.com"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-[var(--text-secondary)]">Password</label>
          <input
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="field-shell w-full rounded-xl px-3 py-2 text-sm outline-none"
          />
          <p className="mt-1 text-xs text-[var(--text-tertiary)]">At least 8 characters</p>
        </div>
        <button
          type="submit"
          disabled={loading}
          className="btn-primary rounded-xl py-2 text-sm font-medium transition-all disabled:opacity-50"
        >
          {loading ? 'Creating account...' : 'Create Account'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-[var(--text-secondary)]">
        Already have an account?{' '}
        <Link to="/login" className="accent-link underline">Sign In</Link>
      </p>
    </div>
  )
}
