import { Link } from 'react-router-dom'

export function Home() {
  return (
    <div className="flex flex-col items-center gap-8 py-20 text-center">
      <div className="surface-tint w-full max-w-4xl rounded-[2rem] px-8 py-14">
        <p className="eyebrow-label text-xs font-semibold">One Of One Auction House</p>
        <h1 className="mt-5 text-5xl font-semibold tracking-tight text-[var(--text-primary)]">
          One of a kind. <br /> Made for you.
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg leading-8 text-[var(--text-secondary)]">
          Aether Terra crafts 1-of-1 shirts that only come to life after a winning bid. The
          experience stays dark, restrained, and premium so the work carries the attention.
        </p>
        <div className="mt-8 flex justify-center">
          <Link
            to="/auctions"
            className="btn-primary rounded-2xl px-6 py-3 text-sm font-semibold transition-all"
          >
            View Live Auctions
          </Link>
        </div>
      </div>
    </div>
  )
}
