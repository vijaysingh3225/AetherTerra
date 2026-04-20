import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

export function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/')
  }

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    isActive
      ? 'text-[var(--accent)] underline underline-offset-4 decoration-[var(--accent)]'
      : 'transition-colors hover:text-[var(--text-primary)]'

  return (
    <header className="border-b border-[var(--border-subtle)] bg-[rgba(7,8,11,0.88)] backdrop-blur-xl">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link
          to="/"
          className="flex items-center gap-2.5 text-xs font-medium tracking-[0.28em] uppercase text-[var(--text-primary)]"
        >
          <span className="text-[var(--accent)]">✦</span>
          Aether Terra
        </Link>
        <nav className="flex items-center gap-6 text-sm text-[var(--text-secondary)]">
          <NavLink to="/auctions" className={linkClass}>
            Auctions
          </NavLink>

          {user ? (
            <>
              {user.role === 'ADMIN' && (
                <NavLink to="/admin" className={linkClass}>
                  Admin
                </NavLink>
              )}
              <NavLink to="/account" className={linkClass}>
                Account
              </NavLink>
              <button
                onClick={handleLogout}
                className="btn-secondary rounded px-4 py-1.5 text-sm transition-colors"
              >
                Sign Out
              </button>
            </>
          ) : (
            <NavLink
              to="/login"
              className="btn-primary rounded px-4 py-1.5 text-sm font-medium transition-all"
            >
              Sign In
            </NavLink>
          )}
        </nav>
      </div>
    </header>
  )
}
