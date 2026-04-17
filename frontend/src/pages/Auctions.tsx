import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'

interface Auction {
  id: string
  slug: string
  title: string
  currentBid: number
  endsAt: string
  status: string
}

export function Auctions() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['auctions'],
    queryFn: () => apiFetch<Auction[]>('/api/v1/auctions'),
  })

  if (isLoading) return <p className="text-neutral-500">Loading auctions…</p>
  if (isError) return <p className="text-red-500">Failed to load auctions.</p>

  return (
    <div>
      <h2 className="mb-6 text-2xl font-semibold text-neutral-900">Live Auctions</h2>
      {data && data.length === 0 && (
        <p className="text-neutral-500">No active auctions right now. Check back soon.</p>
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {data?.map((auction) => (
          <Link
            key={auction.id}
            to={`/auctions/${auction.slug}`}
            className="rounded-lg border border-neutral-200 bg-white p-5 hover:border-neutral-400 transition-colors"
          >
            <p className="font-medium text-neutral-900">{auction.title}</p>
            <p className="mt-1 text-sm text-neutral-500">
              Current bid: <span className="font-semibold text-neutral-800">${auction.currentBid}</span>
            </p>
            <p className="mt-1 text-xs text-neutral-400">
              Ends: {new Date(auction.endsAt).toLocaleString()}
            </p>
          </Link>
        ))}
      </div>
    </div>
  )
}
