import { useParams } from 'react-router-dom'

export function AuctionDetail() {
  const { slug } = useParams<{ slug: string }>()

  return (
    <div className="max-w-2xl">
      <p className="text-sm text-neutral-400 mb-2">Auction</p>
      <h2 className="text-2xl font-semibold text-neutral-900 mb-6">{slug}</h2>

      <div className="rounded-lg border border-neutral-200 bg-white p-6">
        <p className="text-neutral-500">Auction details coming soon.</p>
        <p className="mt-4 text-sm text-neutral-400">
          To bid you must be registered, email-verified, have a saved payment method, and have selected your shirt size.
        </p>
        {/* Bid form placeholder — will integrate Stripe saved payment method before enabling */}
      </div>
    </div>
  )
}
