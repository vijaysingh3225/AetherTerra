import { Link } from 'react-router-dom'

export function Home() {
  return (
    <div className="flex flex-col items-center gap-8 py-20 text-center">
      <div className="surface-tint w-full max-w-4xl px-8 py-16">
        <p className="eyebrow-label text-xs font-medium">One Of One Auction House</p>
        <h1 className="mt-6 text-5xl font-light tracking-tight text-[var(--text-primary)]">
          One of a kind.<br />Made for you.
        </h1>
        <p className="mx-auto mt-5 max-w-xl text-base leading-8 text-[var(--text-secondary)]">
          Aether Terra crafts 1-of-1 shirts that only come to life after a winning bid. The
          experience stays dark, restrained, and precise so the work carries all the attention.
        </p>
        <div className="mt-10 flex justify-center">
          <Link
            to="/auctions"
            className="btn-primary rounded px-7 py-3 text-sm font-medium tracking-wide transition-all"
          >
            View Live Auctions
          </Link>
        </div>
      </div>
    </div>
  )
}
