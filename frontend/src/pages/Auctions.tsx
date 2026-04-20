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
      if (diff <= 0) {
        setLabel('Ended')
        return
      }
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
      <span className="status-live flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium">
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-[var(--aether-blue)]" />
        Live
      </span>
    )
  }
  if (status === 'SCHEDULED') {
    return (
      <span className="status-upcoming shrink-0 rounded-full px-2.5 py-1 text-xs font-medium">
        Upcoming
      </span>
    )
  }
  return (
    <span className="status-ended shrink-0 rounded-full px-2.5 py-1 text-xs font-medium">
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
      className="surface-card group flex flex-col gap-3 rounded-[1.4rem] p-5 transition-all hover:border-[rgba(111,168,220,0.34)] hover:shadow-[0_20px_42px_rgba(0,0,0,0.24)]"
    >
      <div className="flex items-start justify-between gap-2">
        <p className="font-medium leading-snug text-[var(--text-primary)] transition-colors group-hover:text-[var(--aether-blue)]">
          {auction.title}
        </p>
        <StatusBadge status={auction.status} />
      </div>

      {auction.description && (
        <p className="line-clamp-2 text-xs text-[var(--text-tertiary)]">{auction.description}</p>
      )}

      <div className="mt-auto flex items-end justify-between text-sm">
        <div>
          <p className="text-xs text-[var(--text-tertiary)]">
            {auction.currentBid ? 'Current bid' : 'Starting bid'}
          </p>
          <p className="font-semibold text-[var(--text-primary)]">${displayBid.toFixed(2)}</p>
        </div>
        {isActive && (
          <div className="text-right">
            <p className="text-xs text-[var(--text-tertiary)]">
              {auction.status === 'LIVE' ? 'Ends in' : 'Starts in'}
            </p>
            <p className="font-medium text-[var(--aether-blue)]">{countdown}</p>
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
      <h3 className="eyebrow-label mb-4 text-xs font-semibold">{title}</h3>
      <div className="grid gap-4 sm:grid-cols-2">
        {auctions.map((a) => <AuctionCard key={a.id} auction={a} />)}
      </div>
    </section>
  )
}

export function Auctions() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['auctions'],
    queryFn: () => apiFetch<Auction[]>('/api/v1/auctions'),
  })

  if (isLoading) return <p className="text-[var(--text-secondary)]">Loading auctions...</p>
  if (isError) return <p className="text-red-400">Failed to load auctions.</p>

  const live = data?.filter((a) => a.status === 'LIVE') ?? []
  const upcoming = data?.filter((a) => a.status === 'SCHEDULED') ?? []
  const ended = data?.filter((a) => a.status === 'ENDED') ?? []

  return (
    <div className="max-w-4xl">
      <h2 className="mb-3 text-2xl font-semibold text-[var(--text-primary)]">Auctions</h2>
      <p className="mb-8 max-w-2xl text-sm leading-7 text-[var(--text-secondary)]">
        Live auctions stay bright and active. The rest of the experience stays intentionally dark
        so the bidding moments and shirt drops carry the emphasis.
      </p>

      {data?.length === 0 && (
        <p className="text-[var(--text-secondary)]">No auctions yet. Check back soon.</p>
      )}

      <Section title="Live Now" auctions={live} />
      <Section title="Upcoming" auctions={upcoming} />
      <Section title="Past Auctions" auctions={ended} />
    </div>
  )
}
