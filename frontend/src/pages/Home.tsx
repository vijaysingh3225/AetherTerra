import { Link } from 'react-router-dom'

export function Home() {
  return (
    <div className="flex flex-col items-center gap-8 py-20 text-center">
      <h1 className="text-5xl font-semibold tracking-tight text-neutral-900">
        One of a kind. <br /> Made for you.
      </h1>
      <p className="max-w-md text-lg text-neutral-500">
        Aether Terra crafts 1-of-1 shirts, made only after you win. Browse live
        auctions and claim something that's truly yours.
      </p>
      <Link
        to="/auctions"
        className="rounded-md bg-neutral-900 px-6 py-3 text-sm font-medium text-white hover:bg-neutral-700"
      >
        View Live Auctions
      </Link>
    </div>
  )
}
