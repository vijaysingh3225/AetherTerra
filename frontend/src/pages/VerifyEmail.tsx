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
      .then(async res => {
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
    <div className="mx-auto max-w-sm py-12 text-center">
      {status === 'loading' && (
        <p className="text-neutral-500">Verifying your email…</p>
      )}

      {status === 'success' && (
        <>
          <div className="mb-4 text-4xl">✓</div>
          <h2 className="mb-2 text-2xl font-semibold text-neutral-900">You're verified</h2>
          <p className="mb-6 text-neutral-500">{message}</p>
          <Link
            to="/login"
            className="rounded-md bg-neutral-900 px-6 py-2 text-sm font-medium text-white hover:bg-neutral-700"
          >
            Sign In
          </Link>
        </>
      )}

      {status === 'error' && (
        <>
          <h2 className="mb-2 text-2xl font-semibold text-neutral-900">Verification failed</h2>
          <p className="mb-6 text-neutral-500">{message}</p>
          <Link to="/register" className="text-sm underline text-neutral-500 hover:text-neutral-900">
            Back to registration
          </Link>
        </>
      )}
    </div>
  )
}
