import { Link } from 'react-router-dom'

export function NotFound() {
  return (
    <div className="flex flex-col items-center gap-4 py-20 text-center">
      <p className="text-6xl font-semibold text-neutral-200">404</p>
      <p className="text-lg text-neutral-600">Page not found.</p>
      <Link to="/" className="text-sm underline text-neutral-500 hover:text-neutral-900">
        Back to home
      </Link>
    </div>
  )
}
