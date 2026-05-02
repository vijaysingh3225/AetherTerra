import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { loadStripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { apiFetch } from '../lib/api'
import { queryClient } from '../lib/queryClient'

interface UserProfile {
  email: string
  role: string
  shirtSize: string | null
  emailVerified: boolean
  paymentMethodReady: boolean
  paymentMethodAddedAt: string | null
}

const shirtSizes = ['XS', 'S', 'M', 'L', 'XL', 'XXL']

const STRIPE_KEY = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as string | undefined
const stripePromise = STRIPE_KEY ? loadStripe(STRIPE_KEY) : null

// ── Stripe card form (rendered inside <Elements>) ─────────────────────────────

function StripeCardForm({ onSuccess }: { onSuccess: () => void }) {
  const stripe = useStripe()
  const elements = useElements()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!stripe || !elements) return
    setError(null)
    setSaving(true)
    try {
      const result = await stripe.confirmSetup({
        elements,
        confirmParams: { return_url: window.location.href },
        redirect: 'if_required',
      })
      if (result.error) {
        setError(result.error.message ?? 'Card setup failed.')
      } else {
        onSuccess()
      }
    } catch {
      setError('An unexpected error occurred.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mt-5 space-y-4">
      <PaymentElement />
      <button
        type="submit"
        disabled={!stripe || saving}
        className="btn-primary w-full px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
      >
        {saving ? 'Saving...' : 'Save card'}
      </button>
      {error && <p className="notice-danger px-4 py-3 text-sm">{error}</p>}
    </form>
  )
}

// ── Payment method section ────────────────────────────────────────────────────

function PaymentMethodSection({ profile }: { profile: UserProfile }) {
  const [clientSecret, setClientSecret] = useState<string | null>(null)
  const [feedback, setFeedback] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function startSetup() {
    setFeedback(null)
    setLoading(true)
    try {
      const result = await apiFetch<{ clientSecret: string }>(
        '/api/v1/account/payment-method/setup-intent',
        { method: 'POST' },
      )
      setClientSecret(result.clientSecret)
    } catch (e) {
      setFeedback(e instanceof Error ? e.message : 'Could not start card setup.')
    } finally {
      setLoading(false)
    }
  }

  async function handleSuccess() {
    setClientSecret(null)
    setFeedback('Card saved — your account is now bid-ready.')
    await queryClient.invalidateQueries({ queryKey: ['me'] })
  }

  const isMock = !STRIPE_KEY

  return (
    <section className="surface-panel p-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h3 className="text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">
            Payment Method
          </h3>
          <p className="mt-1 text-sm text-[var(--text-secondary)]">
            {isMock
              ? 'Running in mock mode — Stripe is not configured in this environment.'
              : 'A card on file is required before your first bid is accepted.'}
          </p>
        </div>
        <span className={`px-3 py-1 text-xs font-medium ${profile.paymentMethodReady ? 'status-live' : 'status-upcoming'}`}>
          {profile.paymentMethodReady ? 'Ready' : 'Not saved'}
        </span>
      </div>

      {profile.paymentMethodReady ? (
        <p className="mt-4 text-sm text-[var(--text-secondary)]">
          Payment method confirmed.
          {profile.paymentMethodAddedAt && (
            <> Added {new Date(profile.paymentMethodAddedAt).toLocaleDateString()}.</>
          )}
        </p>
      ) : isMock ? (
        <MockPaymentMethodForm onSuccess={handleSuccess} />
      ) : clientSecret ? (
        <Elements stripe={stripePromise} options={{ clientSecret, appearance: { theme: 'night' } }}>
          <StripeCardForm onSuccess={handleSuccess} />
        </Elements>
      ) : (
        <button
          onClick={startSetup}
          disabled={loading}
          className="btn-primary mt-5 px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
        >
          {loading ? 'Loading...' : 'Add a card'}
        </button>
      )}

      {feedback && <p className="notice-success mt-4 px-4 py-3 text-sm">{feedback}</p>}
    </section>
  )
}

// ── Mock payment method form (local dev only, no Stripe key) ─────────────────

function MockPaymentMethodForm({ onSuccess }: { onSuccess: () => void }) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleMockSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      // 1. Create setup intent → backend assigns mock customer ID and returns it
      const siResult = await apiFetch<{ clientSecret: string; customerId: string }>(
        '/api/v1/account/payment-method/setup-intent',
        { method: 'POST' },
      )
      // 2. Fire mock webhook to simulate Stripe's setup_intent.succeeded callback
      await fetch('/api/v1/webhooks/stripe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: 'setup_intent.succeeded',
          data: {
            object: {
              customer: siResult.customerId,
              payment_method: 'mock_pm_dev_test',
            },
          },
        }),
      })
      onSuccess()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Mock setup failed.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleMockSubmit} className="mt-5 space-y-3">
      <p className="notice-warning px-4 py-3 text-sm">
        Mock mode — Stripe is not configured. Click to simulate a successful card setup locally.
      </p>
      <button
        type="submit"
        disabled={submitting}
        className="btn-primary w-full px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
      >
        {submitting ? 'Activating...' : 'Activate mock card'}
      </button>
      {error && <p className="notice-danger px-4 py-3 text-sm">{error}</p>}
    </form>
  )
}

// ── Main Account page ─────────────────────────────────────────────────────────

export function Account() {
  const [shirtSize, setShirtSize] = useState('M')
  const [sizeFeedback, setSizeFeedback] = useState<string | null>(null)

  const profileQuery = useQuery({
    queryKey: ['me'],
    queryFn: () => apiFetch<UserProfile>('/api/v1/users/me'),
  })

  const saveSize = useMutation({
    mutationFn: (nextSize: string) =>
      apiFetch<UserProfile>('/api/v1/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ shirtSize: nextSize }),
      }),
    onSuccess: async (profile) => {
      setSizeFeedback('Shirt size saved.')
      setShirtSize(profile.shirtSize ?? 'M')
      await queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (error) => {
      setSizeFeedback(error instanceof Error ? error.message : 'Unable to save shirt size.')
    },
  })

  function handleSizeSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSizeFeedback(null)
    saveSize.mutate(shirtSize)
  }

  const profile = profileQuery.data

  useEffect(() => {
    if (profile?.shirtSize) setShirtSize(profile.shirtSize)
  }, [profile?.shirtSize])

  if (profileQuery.isLoading) {
    return <p className="text-[var(--text-secondary)]">Loading account...</p>
  }

  if (profileQuery.isError || !profile) {
    return <p className="text-red-400">Unable to load your account right now.</p>
  }

  return (
    <div className="max-w-3xl space-y-5">
      <div className="mb-6">
        <p className="eyebrow-label mb-3 text-xs font-medium">Account</p>
        <h2 className="text-2xl font-light text-[var(--text-primary)]">My Account</h2>
        <p className="mt-2 text-sm text-[var(--text-secondary)]">
          Finish the setup required before your first live bid can be accepted.
        </p>
      </div>

      <section className="surface-panel p-6">
        <h3 className="text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">Profile</h3>
        <dl className="mt-4 space-y-3 text-sm">
          <AccountRow label="Email" value={profile.email} />
          <AccountRow label="Role" value={profile.role} />
          <AccountRow
            label="Email verification"
            value={profile.emailVerified ? 'Verified' : 'Pending verification'}
          />
        </dl>
        {!profile.emailVerified && (
          <p className="notice-warning mt-4 px-4 py-3 text-sm">
            Verify your email from the inbox link before you place your first bid.
          </p>
        )}
      </section>

      <section className="surface-panel p-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">Shirt Size</h3>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">
              Required because each auction shirt is made to order after the winner is confirmed.
            </p>
          </div>
          <span className="status-upcoming px-3 py-1 text-xs font-medium">
            {profile.shirtSize ?? 'Not set'}
          </span>
        </div>

        <form className="mt-5 flex flex-col gap-3 sm:flex-row" onSubmit={handleSizeSubmit}>
          <select
            value={shirtSize}
            onChange={(event) => setShirtSize(event.target.value)}
            className="field-shell rounded px-4 py-3 text-sm outline-none"
          >
            {shirtSizes.map((size) => (
              <option key={size} value={size}>{size}</option>
            ))}
          </select>
          <button
            type="submit"
            disabled={saveSize.isPending}
            className="btn-primary px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
          >
            {saveSize.isPending ? 'Saving...' : 'Save size'}
          </button>
        </form>
        {sizeFeedback && <p className="notice-success mt-3 px-4 py-3 text-sm">{sizeFeedback}</p>}
      </section>

      <PaymentMethodSection profile={profile} />
    </div>
  )
}

function AccountRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-[var(--border-subtle)] pb-3 last:border-b-0 last:pb-0">
      <dt className="text-[var(--text-secondary)]">{label}</dt>
      <dd className="font-medium text-[var(--text-primary)]">{value}</dd>
    </div>
  )
}
