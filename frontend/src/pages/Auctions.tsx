import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useState, useEffect } from 'react'
import { apiFetch } from '../lib/api'

interface Auction {
  id: string
  slug: string
  title: string
  description: string | null
  startingBid: number
  currentBid: number | null
  startsAt: string
  endsAt: string
  status: 'LIVE' | 'SCHEDULED' | 'ENDED' | 'CANCELLED'
}

function useCountdown(target: string): string {
  const [label, setLabel] = useState('')

  useEffect(() => {
    function tick() {
      const diff = new Date(target).getTime() - Date.now()
      if (diff <= 0) { setLabel('Ended'); return }
      const d = Math.floor(diff / 86_400_000)
      const h = Math.floor((diff % 86_400_000) / 3_600_000)
      const m = Math.floor((diff % 3_600_000) / 60_000)
      if (d > 0) setLabel(`${d}d ${h}h`)
      else if (h > 0) setLabel(`${h}h ${m}m`)
      else setLabel(`${m}m`)
    }
    tick()
    const id = setInterval(tick, 60_000)
    return () => clearInterval(id)
  }, [target])

  return label
}

function StatusBadge({ status }: { status: Auction['status'] }) {
  if (status === 'LIVE') {
    return (
      <span className="shrink-0 flex items-center gap-1 rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">
        <span className="h-1.5 w-1.5 rounded-full bg-green-500 animate-pulse" />
        Live
      </span>
    )
  }
  if (status === 'SCHEDULED') {
    return (
      <span className="shrink-0 rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-neutral-500">
        Upcoming
      </span>
    )
  }
  return (
    <span className="shrink-0 rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-neutral-400">
      Ended
    </span>
  )
}

function AuctionCard({ auction }: { auction: Auction }) {
  const isActive = auction.status === 'LIVE' || auction.status === 'SCHEDULED'
  const countdown = useCountdown(auction.status === 'LIVE' ? auction.endsAt : auction.startsAt)
  const displayBid = auction.currentBid ?? auction.startingBid

  return (
    <Link
      to={`/auctions/${auction.slug}`}
      className="group flex flex-col gap-3 rounded-lg border border-neutral-200 bg-white p-5 transition-colors hover:border-neutral-400"
    >
      <div className="flex items-start justify-between gap-2">
        <p className="font-medium leading-snug text-neutral-900 group-hover:text-neutral-700">
          {auction.title}
        </p>
        <StatusBadge status={auction.status} />
      </div>

      {auction.description && (
        <p className="line-clamp-2 text-xs text-neutral-400">{auction.description}</p>
      )}

      <div className="mt-auto flex items-end justify-between text-sm">
        <div>
          <p className="text-xs text-neutral-400">
            {auction.currentBid ? 'Current bid' : 'Starting bid'}
          </p>
          <p className="font-semibold text-neutral-900">${displayBid.toFixed(2)}</p>
        </div>
        {isActive && (
          <div className="text-right">
            <p className="text-xs text-neutral-400">
              {auction.status === 'LIVE' ? 'Ends in' : 'Starts in'}
            </p>
            <p className="font-medium text-neutral-700">{countdown}</p>
          </div>
        )}
      </div>
    </Link>
  )
}

function Section({ title, auctions }: { title: string; auctions: Auction[] }) {
  if (auctions.length === 0) return null
  return (
    <section className="mb-10">
      <h3 className="mb-4 text-xs font-semibold uppercase tracking-widest text-neutral-400">
        {title}
      </h3>
      <div className="grid gap-4 sm:grid-cols-2">
        {auctions.map(a => <AuctionCard key={a.id} auction={a} />)}
      </div>
    </section>
  )
}

export function Auctions() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['auctions'],
    queryFn: () => apiFetch<Auction[]>('/api/v1/auctions'),
  })

  if (isLoading) return <p className="text-neutral-500">Loading auctions…</p>
  if (isError) return <p className="text-red-500">Failed to load auctions.</p>

  const live = data?.filter(a => a.status === 'LIVE') ?? []
  const upcoming = data?.filter(a => a.status === 'SCHEDULED') ?? []
  const ended = data?.filter(a => a.status === 'ENDED') ?? []

  return (
    <div className="max-w-4xl">
      <h2 className="mb-8 text-2xl font-semibold text-neutral-900">Auctions</h2>

      {data?.length === 0 && (
        <p className="text-neutral-500">No auctions yet. Check back soon.</p>
      )}

      <Section title="Live Now" auctions={live} />
      <Section title="Upcoming" auctions={upcoming} />
      <Section title="Past Auctions" auctions={ended} />
    </div>
  )
}
