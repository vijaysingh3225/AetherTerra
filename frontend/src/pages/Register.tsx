import { useState, FormEvent } from 'react'
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
      <div className="mx-auto max-w-sm text-center">
        <div className="mb-4 text-4xl">✉️</div>
        <h2 className="mb-2 text-2xl font-semibold text-neutral-900">Check your email</h2>
        <p className="text-neutral-500">
          We sent a verification link to <span className="font-medium text-neutral-800">{email}</span>.
          Click it to activate your account.
        </p>
        <p className="mt-4 text-sm text-neutral-400">
          Didn't get it? Check your spam folder, or{' '}
          <button
            onClick={() => setSubmitted(false)}
            className="underline hover:text-neutral-900"
          >
            try again
          </button>
          .
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-sm">
      <h2 className="mb-6 text-2xl font-semibold text-neutral-900">Create Account</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && (
          <p className="rounded-md bg-red-50 px-4 py-2 text-sm text-red-600">{error}</p>
        )}
        <div>
          <label className="mb-1 block text-sm font-medium text-neutral-700">Email</label>
          <input
            type="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
            placeholder="you@example.com"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-neutral-700">Password</label>
          <input
            type="password"
            required
            minLength={8}
            value={password}
            onChange={e => setPassword(e.target.value)}
            className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
          />
          <p className="mt-1 text-xs text-neutral-400">At least 8 characters</p>
        </div>
        <button
          type="submit"
          disabled={loading}
          className="rounded-md bg-neutral-900 py-2 text-sm font-medium text-white hover:bg-neutral-700 disabled:opacity-50"
        >
          {loading ? 'Creating account…' : 'Create Account'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-neutral-500">
        Already have an account?{' '}
        <Link to="/login" className="underline hover:text-neutral-900">Sign In</Link>
      </p>
    </div>
  )
}
