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
  const displayBid = auction?.currentBid ?? auction?.startingBid ?? 0
  const countdownTarget = auction?.status === 'SCHEDULED' ? auction.startsAt : auction?.endsAt
  const countdown = useCountdown(countdownTarget ?? new Date().toISOString())
  const suggestedBid = auction ? ((auction.currentBid ?? auction.startingBid) + 5).toFixed(2) : ''
  const canBid = Boolean(user) && auction?.status === 'LIVE'

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

  if (detailQuery.isLoading) {
    return <p className="text-neutral-500">Loading auction…</p>
  }

  if (detailQuery.isError || !auction) {
    return <p className="text-red-500">Auction not found.</p>
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1.4fr_0.9fr]">
      <section className="space-y-6">
        <div className="rounded-3xl border border-neutral-200 bg-[linear-gradient(135deg,#f8fafc_0%,#ffffff_55%,#f5f5f4_100%)] p-8">
          <p className="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-400">
            Auction Detail
          </p>
          <div className="mt-4 flex flex-wrap items-start justify-between gap-4">
            <div className="max-w-2xl">
              <h1 className="text-3xl font-semibold tracking-tight text-neutral-900">
                {auction.title}
              </h1>
              <p className="mt-3 text-sm leading-7 text-neutral-600">
                {auction.description ?? 'No description available yet.'}
              </p>
            </div>
            <span className="rounded-full border border-neutral-200 bg-white px-3 py-1 text-xs font-medium text-neutral-600">
              {auction.status}
            </span>
          </div>

          <div className="mt-8 grid gap-4 sm:grid-cols-3">
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

        <section className="rounded-3xl border border-neutral-200 bg-white p-6">
          <div className="flex items-center justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold text-neutral-900">Bid History</h2>
              <p className="mt-1 text-sm text-neutral-500">
                Public auction activity, newest bids first.
              </p>
            </div>
          </div>

          {bidsQuery.isLoading && <p className="mt-6 text-sm text-neutral-500">Loading bid history…</p>}
          {bidsQuery.isError && <p className="mt-6 text-sm text-red-500">Unable to load bid history.</p>}

          {!bidsQuery.isLoading && !bidsQuery.isError && bids.length === 0 && (
            <p className="mt-6 text-sm text-neutral-500">No bids yet. The first bid sets the pace.</p>
          )}

          {bids.length > 0 && (
            <div className="mt-6 space-y-3">
              {bids.map((bid) => (
                <div
                  key={bid.id}
                  className="flex items-center justify-between rounded-2xl border border-neutral-100 bg-neutral-50 px-4 py-3"
                >
                  <div>
                    <p className="font-medium text-neutral-900">{formatMoney(bid.amount)}</p>
                    <p className="text-xs text-neutral-500">{bid.bidder}</p>
                  </div>
                  <p className="text-xs uppercase tracking-wide text-neutral-400">
                    {formatTimestamp(bid.placedAt)}
                  </p>
                </div>
              ))}
            </div>
          )}
        </section>
      </section>

      <aside className="space-y-6">
        <section className="rounded-3xl border border-neutral-200 bg-white p-6">
          <h2 className="text-lg font-semibold text-neutral-900">Place A Bid</h2>
          <p className="mt-2 text-sm leading-6 text-neutral-500">
            Submit an amount above the current bid. Account requirement checks will be layered in next.
          </p>

          <dl className="mt-6 space-y-3 text-sm">
            <InfoRow label="Starts" value={formatTimestamp(auction.startsAt)} />
            <InfoRow label="Ends" value={formatTimestamp(auction.endsAt)} />
            <InfoRow label="Current" value={formatMoney(displayBid)} />
            <InfoRow label="Suggested" value={suggestedBid ? formatMoney(Number(suggestedBid)) : '—'} />
          </dl>

          <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
            <label className="block">
              <span className="mb-2 block text-sm font-medium text-neutral-700">Your bid</span>
              <input
                type="number"
                min="0"
                step="0.01"
                placeholder={suggestedBid}
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                disabled={!canBid || placeBid.isPending}
                className="w-full rounded-2xl border border-neutral-300 bg-white px-4 py-3 text-neutral-900 outline-none transition focus:border-neutral-500 disabled:cursor-not-allowed disabled:bg-neutral-100"
              />
            </label>

            <button
              type="submit"
              disabled={!canBid || placeBid.isPending}
              className="w-full rounded-2xl bg-neutral-900 px-4 py-3 text-sm font-medium text-white transition hover:bg-neutral-700 disabled:cursor-not-allowed disabled:bg-neutral-400"
            >
              {placeBid.isPending ? 'Placing bid…' : 'Place Bid'}
            </button>
          </form>

          {feedback && (
            <p className={`mt-4 text-sm ${placeBid.isError ? 'text-red-500' : 'text-green-600'}`}>
              {feedback}
            </p>
          )}

          {!user && (
            <p className="mt-4 text-sm text-neutral-500">
              <Link to="/login" className="font-medium text-neutral-900 underline underline-offset-4">
                Sign in
              </Link>{' '}
              to place a bid.
            </p>
          )}

          {user && auction.status !== 'LIVE' && (
            <p className="mt-4 text-sm text-neutral-500">
              Bidding opens only while an auction is live.
            </p>
          )}

          <div className="mt-6 rounded-2xl border border-dashed border-neutral-200 bg-neutral-50 px-4 py-4 text-sm text-neutral-500">
            Required before bidding in the final flow: verified email, shirt size selected, and a saved payment method.
          </div>
        </section>
      </aside>
    </div>
  )
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-neutral-200 bg-white px-4 py-4">
      <p className="text-xs uppercase tracking-[0.2em] text-neutral-400">{label}</p>
      <p className="mt-2 text-xl font-semibold text-neutral-900">{value}</p>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-neutral-100 pb-3 last:border-b-0 last:pb-0">
      <dt className="text-neutral-500">{label}</dt>
      <dd className="font-medium text-neutral-900">{value}</dd>
    </div>
  )
}
