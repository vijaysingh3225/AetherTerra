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
    return <p className="text-neutral-500">Loading account...</p>
  }

  if (profileQuery.isError || !profile) {
    return <p className="text-red-500">Unable to load your account right now.</p>
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h2 className="text-2xl font-semibold text-neutral-900">My Account</h2>
        <p className="mt-2 text-sm text-neutral-500">
          Finish the setup required before your first live bid can be accepted.
        </p>
      </div>

      <section className="rounded-3xl border border-neutral-200 bg-white p-6">
        <h3 className="text-lg font-semibold text-neutral-900">Profile</h3>
        <dl className="mt-4 space-y-3 text-sm">
          <AccountRow label="Email" value={profile.email} />
          <AccountRow label="Role" value={profile.role} />
          <AccountRow
            label="Email verification"
            value={profile.emailVerified ? 'Verified' : 'Pending verification'}
          />
        </dl>
        {!profile.emailVerified && (
          <p className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            Verify your email from the inbox link before you place your first bid.
          </p>
        )}
      </section>

      <section className="rounded-3xl border border-neutral-200 bg-white p-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Shirt Size</h3>
            <p className="mt-1 text-sm text-neutral-500">
              Required because each auction shirt is made to order after the winner is confirmed.
            </p>
          </div>
          <span className="rounded-full border border-neutral-200 bg-neutral-50 px-3 py-1 text-xs font-medium text-neutral-600">
            {profile.shirtSize ?? 'Not set'}
          </span>
        </div>

        <form className="mt-5 flex flex-col gap-3 sm:flex-row" onSubmit={handleSizeSubmit}>
          <select
            value={shirtSize}
            onChange={(event) => setShirtSize(event.target.value)}
            className="rounded-2xl border border-neutral-300 bg-white px-4 py-3 text-sm text-neutral-900 outline-none focus:border-neutral-500"
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
            className="rounded-2xl bg-neutral-900 px-4 py-3 text-sm font-medium text-white transition hover:bg-neutral-700 disabled:cursor-not-allowed disabled:bg-neutral-400"
          >
            {saveSize.isPending ? 'Saving...' : 'Save size'}
          </button>
        </form>
      </section>

      <section className="rounded-3xl border border-neutral-200 bg-white p-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Payment Method</h3>
            <p className="mt-1 text-sm text-neutral-500">
              Stripe is still pending, so this stores placeholder card details for bid eligibility.
            </p>
          </div>
          <span className="rounded-full border border-neutral-200 bg-neutral-50 px-3 py-1 text-xs font-medium text-neutral-600">
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
            className="rounded-2xl border border-neutral-300 bg-white px-4 py-3 text-sm text-neutral-900 outline-none focus:border-neutral-500"
          />
          <input
            type="text"
            inputMode="numeric"
            maxLength={4}
            value={last4}
            onChange={(event) => setLast4(event.target.value.replace(/\D/g, '').slice(0, 4))}
            placeholder="Last 4"
            className="rounded-2xl border border-neutral-300 bg-white px-4 py-3 text-sm text-neutral-900 outline-none focus:border-neutral-500"
          />
          <button
            type="submit"
            disabled={savePaymentMethod.isPending}
            className="rounded-2xl bg-neutral-900 px-4 py-3 text-sm font-medium text-white transition hover:bg-neutral-700 disabled:cursor-not-allowed disabled:bg-neutral-400"
          >
            {savePaymentMethod.isPending ? 'Saving...' : 'Save card'}
          </button>
        </form>
      </section>

      {feedback && (
        <p className="text-sm text-neutral-600">{feedback}</p>
      )}
    </div>
  )
}

function AccountRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-neutral-100 pb-3 last:border-b-0 last:pb-0">
      <dt className="text-neutral-500">{label}</dt>
      <dd className="font-medium text-neutral-900">{value}</dd>
    </div>
  )
}
