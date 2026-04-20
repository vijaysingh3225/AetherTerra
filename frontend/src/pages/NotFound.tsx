import { Link } from 'react-router-dom'

export function NotFound() {
  return (
    <div className="flex flex-col items-center gap-4 py-20 text-center">
      <p className="text-6xl font-light tracking-tight text-[var(--accent)]">404</p>
      <p className="text-lg text-[var(--text-secondary)]">Page not found.</p>
      <Link to="/" className="accent-link text-sm underline underline-offset-4">
        Back to home
      </Link>
    </div>
  )
}
