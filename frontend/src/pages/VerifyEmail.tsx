import { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'

type Status = 'loading' | 'success' | 'error'

export function VerifyEmail() {
  const [searchParams] = useSearchParams()
  const [status, setStatus] = useState<Status>('loading')
  const [message, setMessage] = useState('')

  useEffect(() => {
    const token = searchParams.get('token')
    if (!token) {
      setStatus('error')
      setMessage('No verification token found in the link.')
      return
    }

    fetch(`/api/v1/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then(async (res) => {
        const json = await res.json()
        if (res.ok) {
          setStatus('success')
        } else {
          setStatus('error')
        }
        setMessage(json.message || 'Something went wrong. Please try again.')
      })
      .catch(() => {
        setStatus('error')
        setMessage('Something went wrong. Please try again.')
      })
  }, [searchParams])

  return (
    <div className="surface-panel mx-auto max-w-sm px-8 py-12 text-center">
      {status === 'loading' && (
        <p className="text-[var(--text-secondary)]">Verifying your email...</p>
      )}

      {status === 'success' && (
        <>
          <div className="mb-4 text-3xl text-[var(--accent)]">✦</div>
          <h2 className="mb-2 text-2xl font-light text-[var(--text-primary)]">You're verified</h2>
          <p className="mb-6 text-[var(--text-secondary)]">{message}</p>
          <Link
            to="/login"
            className="btn-primary px-6 py-2.5 text-sm font-medium"
          >
            Sign In
          </Link>
        </>
      )}

      {status === 'error' && (
        <>
          <h2 className="mb-2 text-2xl font-light text-[var(--text-primary)]">Verification failed</h2>
          <p className="mb-6 text-[var(--text-secondary)]">{message}</p>
          <Link to="/register" className="accent-link text-sm underline underline-offset-4">
            Back to registration
          </Link>
        </>
      )}
    </div>
  )
}
