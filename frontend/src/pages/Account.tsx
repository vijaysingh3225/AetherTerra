import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import { queryClient } from '../lib/queryClient'

interface UserProfile {
  email: string
  role: string
  shirtSize: string | null
  emailVerified: boolean
  paymentMethodBrand: string | null
  paymentMethodLast4: string | null
  paymentMethodAddedAt: string | null
}

const shirtSizes = ['XS', 'S', 'M', 'L', 'XL', 'XXL']

export function Account() {
  const [shirtSize, setShirtSize] = useState('M')
  const [brand, setBrand] = useState('')
  const [last4, setLast4] = useState('')
  const [feedback, setFeedback] = useState<string | null>(null)

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
      setFeedback('Shirt size saved.')
      setShirtSize(profile.shirtSize ?? 'M')
      await queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (error) => {
      setFeedback(error instanceof Error ? error.message : 'Unable to save shirt size.')
    },
  })

  const savePaymentMethod = useMutation({
    mutationFn: (payload: { brand: string; last4: string }) =>
      apiFetch<UserProfile>('/api/v1/users/me/payment-method', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: async () => {
      setFeedback('Payment method saved.')
      setBrand('')
      setLast4('')
      await queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (error) => {
      setFeedback(error instanceof Error ? error.message : 'Unable to save payment method.')
    },
  })

  function handleSizeSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFeedback(null)
    saveSize.mutate(shirtSize)
  }

  function handlePaymentSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFeedback(null)
    savePaymentMethod.mutate({ brand: brand.trim(), last4: last4.trim() })
  }

  const profile = profileQuery.data

  useEffect(() => {
    if (profile?.shirtSize) {
      setShirtSize(profile.shirtSize)
    }
  }, [profile?.shirtSize])

  if (profileQuery.isLoading) {
    return <p className="text-[var(--text-secondary)]">Loading account...</p>
  }

  if (profileQuery.isError || !profile) {
    return <p className="text-red-400">Unable to load your account right now.</p>
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h2 className="text-2xl font-semibold text-[var(--text-primary)]">My Account</h2>
        <p className="mt-2 text-sm text-[var(--text-secondary)]">
          Finish the setup required before your first live bid can be accepted.
        </p>
      </div>

      <section className="surface-panel rounded-3xl p-6">
        <h3 className="text-lg font-semibold text-[var(--text-primary)]">Profile</h3>
        <dl className="mt-4 space-y-3 text-sm">
          <AccountRow label="Email" value={profile.email} />
          <AccountRow label="Role" value={profile.role} />
          <AccountRow
            label="Email verification"
            value={profile.emailVerified ? 'Verified' : 'Pending verification'}
          />
        </dl>
        {!profile.emailVerified && (
          <p className="notice-warning mt-4 rounded-2xl px-4 py-3 text-sm">
            Verify your email from the inbox link before you place your first bid.
          </p>
        )}
      </section>

      <section className="surface-panel rounded-3xl p-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-[var(--text-primary)]">Shirt Size</h3>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">
              Required because each auction shirt is made to order after the winner is confirmed.
            </p>
          </div>
          <span className="status-upcoming rounded-full px-3 py-1 text-xs font-medium">
            {profile.shirtSize ?? 'Not set'}
          </span>
        </div>

        <form className="mt-5 flex flex-col gap-3 sm:flex-row" onSubmit={handleSizeSubmit}>
          <select
            value={shirtSize}
            onChange={(event) => setShirtSize(event.target.value)}
            className="field-shell rounded-2xl px-4 py-3 text-sm outline-none"
          >
            {shirtSizes.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
          <button
            type="submit"
            disabled={saveSize.isPending}
            className="btn-primary rounded-2xl px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
          >
            {saveSize.isPending ? 'Saving...' : 'Save size'}
          </button>
        </form>
      </section>

      <section className="surface-panel rounded-3xl p-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-[var(--text-primary)]">Payment Method</h3>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">
              Stripe is still pending, so this stores placeholder card details for bid eligibility.
            </p>
          </div>
          <span className="status-upcoming rounded-full px-3 py-1 text-xs font-medium">
            {profile.paymentMethodBrand && profile.paymentMethodLast4
              ? `${profile.paymentMethodBrand} ending in ${profile.paymentMethodLast4}`
              : 'Not saved'}
          </span>
        </div>

        <form className="mt-5 grid gap-3 sm:grid-cols-[1fr_140px_auto]" onSubmit={handlePaymentSubmit}>
          <input
            type="text"
            value={brand}
            onChange={(event) => setBrand(event.target.value)}
            placeholder="Card brand"
            className="field-shell rounded-2xl px-4 py-3 text-sm outline-none"
          />
          <input
            type="text"
            inputMode="numeric"
            maxLength={4}
            value={last4}
            onChange={(event) => setLast4(event.target.value.replace(/\D/g, '').slice(0, 4))}
            placeholder="Last 4"
            className="field-shell rounded-2xl px-4 py-3 text-sm outline-none"
          />
          <button
            type="submit"
            disabled={savePaymentMethod.isPending}
            className="btn-primary rounded-2xl px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
          >
            {savePaymentMethod.isPending ? 'Saving...' : 'Save card'}
          </button>
        </form>
      </section>

      {feedback && (
        <p className="notice-success rounded-2xl px-4 py-3 text-sm">{feedback}</p>
      )}
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
