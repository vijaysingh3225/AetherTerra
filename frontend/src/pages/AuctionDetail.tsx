import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import { queryClient } from '../lib/queryClient'
import { useAuth } from '../context/AuthContext'

interface AuctionDetailData {
  id: string
  slug: string
  title: string
  description: string | null
  startingBid: number
  currentBid: number | null
  startsAt: string
  endsAt: string
  status: 'LIVE' | 'SCHEDULED' | 'ENDED' | 'CANCELLED'
  bidCount: number
}

interface BidHistoryItem {
  id: string
  amount: number
  placedAt: string
  bidder: string
}

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

function formatMoney(amount: number) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount)
}

function formatTimestamp(value: string) {
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value))
}

function useCountdown(target: string) {
  const [label, setLabel] = useState('')

  useEffect(() => {
    function tick() {
      const diff = new Date(target).getTime() - Date.now()
      if (diff <= 0) {
        setLabel('Ended')
        return
      }

      const days = Math.floor(diff / 86_400_000)
      const hours = Math.floor((diff % 86_400_000) / 3_600_000)
      const minutes = Math.floor((diff % 3_600_000) / 60_000)

      if (days > 0) setLabel(`${days}d ${hours}h`)
      else if (hours > 0) setLabel(`${hours}h ${minutes}m`)
      else setLabel(`${minutes}m`)
    }

    tick()
    const id = setInterval(tick, 60_000)
    return () => clearInterval(id)
  }, [target])

  return label
}

export function AuctionDetail() {
  const { slug = '' } = useParams<{ slug: string }>()
  const { user } = useAuth()
  const [amount, setAmount] = useState('')
  const [feedback, setFeedback] = useState<string | null>(null)
  const [setupFeedback, setSetupFeedback] = useState<string | null>(null)
  const [shirtSize, setShirtSize] = useState('M')
  const [brand, setBrand] = useState('')
  const [last4, setLast4] = useState('')

  const detailQuery = useQuery({
    queryKey: ['auction', slug],
    queryFn: () => apiFetch<AuctionDetailData>(`/api/v1/auctions/${slug}`),
    enabled: Boolean(slug),
  })

  const bidsQuery = useQuery({
    queryKey: ['auction', slug, 'bids'],
    queryFn: () => apiFetch<BidHistoryItem[]>(`/api/v1/auctions/${slug}/bids`),
    enabled: Boolean(slug),
  })

  const profileQuery = useQuery({
    queryKey: ['me'],
    queryFn: () => apiFetch<UserProfile>('/api/v1/users/me'),
    enabled: Boolean(user),
  })

  const saveSize = useMutation({
    mutationFn: (nextSize: string) =>
      apiFetch<UserProfile>('/api/v1/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ shirtSize: nextSize }),
      }),
    onSuccess: async (profile) => {
      setSetupFeedback('Shirt size saved. You are one step closer to bidding.')
      setShirtSize(profile.shirtSize ?? 'M')
      await queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (error) => {
      setSetupFeedback(error instanceof Error ? error.message : 'Unable to save shirt size.')
    },
  })

  const savePaymentMethod = useMutation({
    mutationFn: (payload: { brand: string; last4: string }) =>
      apiFetch<UserProfile>('/api/v1/users/me/payment-method', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: async () => {
      setSetupFeedback('Payment method saved. Your account is updated for bidding.')
      setBrand('')
      setLast4('')
      await queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (error) => {
      setSetupFeedback(error instanceof Error ? error.message : 'Unable to save payment method.')
    },
  })

  const placeBid = useMutation({
    mutationFn: (nextAmount: number) =>
      apiFetch<BidHistoryItem>(`/api/v1/auctions/${slug}/bids`, {
        method: 'POST',
        body: JSON.stringify({ amount: nextAmount }),
      }),
    onSuccess: async () => {
      setFeedback('Bid placed successfully.')
      setAmount('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['auction', slug] }),
        queryClient.invalidateQueries({ queryKey: ['auction', slug, 'bids'] }),
        queryClient.invalidateQueries({ queryKey: ['auctions'] }),
      ])
    },
    onError: (error) => {
      setFeedback(error instanceof Error ? error.message : 'Unable to place bid.')
    },
  })

  const auction = detailQuery.data
  const bids = bidsQuery.data ?? []
  const profile = profileQuery.data
  const displayBid = auction?.currentBid ?? auction?.startingBid ?? 0
  const countdownTarget = auction?.status === 'SCHEDULED' ? auction.startsAt : auction?.endsAt
  const countdown = useCountdown(countdownTarget ?? new Date().toISOString())
  const suggestedBid = auction ? ((auction.currentBid ?? auction.startingBid) + 5).toFixed(2) : ''

  const requirements = profile
    ? [
        {
          key: 'email',
          label: 'Verified email',
          met: profile.emailVerified,
          hint: 'Check your inbox and open the verification link.',
        },
        {
          key: 'size',
          label: 'Shirt size selected',
          met: Boolean(profile.shirtSize),
          hint: 'Pick the size you want us to make if you win.',
        },
        {
          key: 'payment',
          label: 'Saved payment method',
          met: Boolean(profile.paymentMethodBrand && profile.paymentMethodLast4),
          hint: 'Add a placeholder card now. Stripe comes later.',
        },
      ]
    : []

  const isBidReady = requirements.length > 0 && requirements.every((requirement) => requirement.met)
  const canBid = Boolean(user) && auction?.status === 'LIVE' && isBidReady

  useEffect(() => {
    if (profile?.shirtSize) {
      setShirtSize(profile.shirtSize)
    }
  }, [profile?.shirtSize])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFeedback(null)

    const numericAmount = Number(amount)
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setFeedback('Enter a valid bid amount.')
      return
    }

    placeBid.mutate(numericAmount)
  }

  function handleSizeSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSetupFeedback(null)
    saveSize.mutate(shirtSize)
  }

  function handlePaymentSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSetupFeedback(null)
    savePaymentMethod.mutate({ brand: brand.trim(), last4: last4.trim() })
  }

  if (detailQuery.isLoading) {
    return <p className="text-[var(--text-secondary)]">Loading auction...</p>
  }

  if (detailQuery.isError || !auction) {
    return <p className="text-red-400">Auction not found.</p>
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1.4fr_0.9fr]">
      <section className="space-y-5">
        <div className="surface-tint p-8">
          <p className="eyebrow-label text-xs font-medium">Auction Detail</p>
          <div className="mt-4 flex flex-wrap items-start justify-between gap-4">
            <div className="max-w-2xl">
              <h1 className="text-3xl font-light tracking-tight text-[var(--text-primary)]">
                {auction.title}
              </h1>
              <p className="mt-3 text-sm leading-7 text-[var(--text-secondary)]">
                {auction.description ?? 'No description available yet.'}
              </p>
            </div>
            <span className="status-upcoming px-3 py-1 text-xs font-medium">
              {auction.status}
            </span>
          </div>

          <div className="mt-8 grid gap-3 sm:grid-cols-3">
            <StatCard
              label={auction.currentBid ? 'Current Bid' : 'Starting Bid'}
              value={formatMoney(displayBid)}
            />
            <StatCard
              label={auction.status === 'SCHEDULED' ? 'Starts In' : 'Ends In'}
              value={countdown}
            />
            <StatCard
              label="Bid Activity"
              value={`${auction.bidCount} ${auction.bidCount === 1 ? 'bid' : 'bids'}`}
            />
          </div>
        </div>

        <section className="surface-panel p-6">
          <div>
            <h2 className="text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">Bid History</h2>
            <p className="mt-1 text-xs text-[var(--text-tertiary)]">
              Public auction activity, newest bids first.
            </p>
          </div>

          {bidsQuery.isLoading && <p className="mt-6 text-sm text-[var(--text-secondary)]">Loading bid history...</p>}
          {bidsQuery.isError && <p className="mt-6 text-sm text-red-400">Unable to load bid history.</p>}

          {!bidsQuery.isLoading && !bidsQuery.isError && bids.length === 0 && (
            <p className="mt-6 text-sm text-[var(--text-secondary)]">No bids yet. The first bid sets the pace.</p>
          )}

          {bids.length > 0 && (
            <div className="mt-5 space-y-2">
              {bids.map((bid) => (
                <div
                  key={bid.id}
                  className="surface-card flex items-center justify-between px-4 py-3"
                >
                  <div>
                    <p className="font-medium text-[var(--text-primary)]">{formatMoney(bid.amount)}</p>
                    <p className="text-xs text-[var(--text-secondary)]">{bid.bidder}</p>
                  </div>
                  <p className="text-xs uppercase tracking-wide text-[var(--text-tertiary)]">
                    {formatTimestamp(bid.placedAt)}
                  </p>
                </div>
              ))}
            </div>
          )}
        </section>
      </section>

      <aside className="space-y-5">
        <section className="surface-panel p-6">
          <h2 className="text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">Place A Bid</h2>
          <p className="mt-2 text-xs leading-6 text-[var(--text-tertiary)]">
            Live bidding stays locked until your account meets every pre-bid requirement.
          </p>

          <dl className="mt-6 space-y-3 text-sm">
            <InfoRow label="Starts" value={formatTimestamp(auction.startsAt)} />
            <InfoRow label="Ends" value={formatTimestamp(auction.endsAt)} />
            <InfoRow label="Current" value={formatMoney(displayBid)} />
            <InfoRow label="Suggested" value={suggestedBid ? formatMoney(Number(suggestedBid)) : '—'} />
          </dl>

          {user && profileQuery.isLoading && (
            <p className="mt-4 text-sm text-[var(--text-secondary)]">Checking bid requirements...</p>
          )}

          {user && profile && (
            <div className="mt-6 space-y-2">
              {requirements.map((requirement) => (
                <div
                  key={requirement.key}
                  className={`border px-4 py-3 text-sm ${
                    requirement.met ? 'notice-success' : 'notice-warning'
                  }`}
                >
                  <p className="font-medium">
                    {requirement.met ? 'Ready' : 'Needed'}: {requirement.label}
                  </p>
                  {!requirement.met && <p className="mt-1 text-xs">{requirement.hint}</p>}
                </div>
              ))}
            </div>
          )}

          <form className="mt-6 space-y-3" onSubmit={handleSubmit}>
            <label className="block">
              <span className="mb-2 block text-xs font-medium text-[var(--text-secondary)]">Your bid</span>
              <input
                type="number"
                min="0"
                step="0.01"
                placeholder={suggestedBid}
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                disabled={!canBid || placeBid.isPending}
                className="field-shell w-full rounded px-4 py-3 outline-none disabled:cursor-not-allowed disabled:opacity-50"
              />
            </label>

            <button
              type="submit"
              disabled={!canBid || placeBid.isPending}
              className="btn-primary w-full rounded px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
            >
              {placeBid.isPending ? 'Placing bid...' : 'Place Bid'}
            </button>
          </form>

          {feedback && (
            <p className={`mt-4 px-4 py-3 text-sm ${placeBid.isError ? 'notice-danger' : 'notice-success'}`}>
              {feedback}
            </p>
          )}

          {!user && (
            <p className="mt-4 text-sm text-[var(--text-secondary)]">
              <Link to="/login" className="accent-link font-medium underline underline-offset-4">
                Sign in
              </Link>{' '}
              to place a bid.
            </p>
          )}

          {user && profile && !isBidReady && (
            <p className="mt-4 text-xs text-[var(--text-tertiary)]">
              Finish setup below or manage it from{' '}
              <Link to="/account" className="accent-link underline underline-offset-4">
                your account page
              </Link>
              .
            </p>
          )}

          {user && auction.status !== 'LIVE' && (
            <p className="mt-4 text-xs text-[var(--text-tertiary)]">
              Bidding opens only while an auction is live.
            </p>
          )}
        </section>

        {user && profile && !profile.emailVerified && (
          <section className="notice-warning p-6">
            <h3 className="text-sm font-medium uppercase tracking-widest">Verify Your Email</h3>
            <p className="mt-2 text-xs leading-6">
              Email verification is still required before your first bid can go through. Open the
              verification link sent to {profile.email}.
            </p>
          </section>
        )}

        {user && profile && !profile.shirtSize && (
          <section className="surface-panel p-6">
            <h3 className="text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">Set Shirt Size</h3>
            <p className="mt-2 text-xs leading-6 text-[var(--text-tertiary)]">
              Save the size we should make if you win this auction.
            </p>
            <form className="mt-4 flex flex-col gap-3 sm:flex-row" onSubmit={handleSizeSubmit}>
              <select
                value={shirtSize}
                onChange={(event) => setShirtSize(event.target.value)}
                className="field-shell rounded px-4 py-3 text-sm outline-none"
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
                className="btn-primary rounded px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
              >
                {saveSize.isPending ? 'Saving...' : 'Save size'}
              </button>
            </form>
          </section>
        )}

        {user && profile && !(profile.paymentMethodBrand && profile.paymentMethodLast4) && (
          <section className="surface-panel p-6">
            <h3 className="text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">Save Payment Method</h3>
            <p className="mt-2 text-xs leading-6 text-[var(--text-tertiary)]">
              Add placeholder card details now so your account is bid-ready before Stripe lands.
            </p>
            <form className="mt-4 space-y-3" onSubmit={handlePaymentSubmit}>
              <input
                type="text"
                value={brand}
                onChange={(event) => setBrand(event.target.value)}
                placeholder="Card brand"
                className="field-shell w-full rounded px-4 py-3 text-sm outline-none"
              />
              <input
                type="text"
                inputMode="numeric"
                maxLength={4}
                value={last4}
                onChange={(event) => setLast4(event.target.value.replace(/\D/g, '').slice(0, 4))}
                placeholder="Last 4 digits"
                className="field-shell w-full rounded px-4 py-3 text-sm outline-none"
              />
              <button
                type="submit"
                disabled={savePaymentMethod.isPending}
                className="btn-primary w-full rounded px-4 py-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
              >
                {savePaymentMethod.isPending ? 'Saving...' : 'Save card'}
              </button>
            </form>
          </section>
        )}

        {setupFeedback && (
          <p className="notice-success px-4 py-3 text-sm">{setupFeedback}</p>
        )}
      </aside>
    </div>
  )
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="surface-card px-4 py-4">
      <p className="eyebrow-label text-xs">{label}</p>
      <p className="mt-2 text-xl font-light text-[var(--text-primary)]">{value}</p>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-[var(--border-subtle)] pb-3 last:border-b-0 last:pb-0">
      <dt className="text-xs text-[var(--text-secondary)]">{label}</dt>
      <dd className="font-medium text-[var(--text-primary)]">{value}</dd>
    </div>
  )
}
