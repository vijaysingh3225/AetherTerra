import { Link, NavLink } from 'react-router-dom'

export function Navbar() {
  return (
    <header className="border-b border-neutral-200 bg-white">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link to="/" className="text-xl font-semibold tracking-tight text-neutral-900">
          Aether Terra
        </Link>
        <nav className="flex items-center gap-6 text-sm text-neutral-600">
          <NavLink to="/auctions" className={({ isActive }) => isActive ? 'text-neutral-900 font-medium' : 'hover:text-neutral-900'}>
            Auctions
          </NavLink>
          <NavLink to="/account" className={({ isActive }) => isActive ? 'text-neutral-900 font-medium' : 'hover:text-neutral-900'}>
            Account
          </NavLink>
          <NavLink to="/login" className="rounded-md bg-neutral-900 px-4 py-1.5 text-white hover:bg-neutral-700">
            Sign In
          </NavLink>
        </nav>
      </div>
    </header>
  )
}
