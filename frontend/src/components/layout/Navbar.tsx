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
      : 'text-[#b8a890] transition-colors hover:text-[#e8dfd0]'

  return (
    <header className="border-b border-[#3a2010] bg-[#1c0e04]">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link
          to="/"
          className="font-display flex items-center gap-2.5 text-xs font-medium tracking-[0.28em] uppercase text-[#e8dfd0]"
        >
          <span className="text-[var(--accent)]">⚜</span>
          Aether Terra
        </Link>
        <nav className="flex items-center gap-6 text-sm">
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
                className="font-display border border-[rgba(184,160,128,0.40)] px-4 py-1.5 text-sm tracking-[0.06em] text-[#b8a890] transition-colors hover:border-[var(--accent)] hover:text-[var(--accent)]"
              >
                Sign Out
              </button>
            </>
          ) : (
            <NavLink
              to="/login"
              className="btn-primary px-4 py-1.5 text-sm font-medium transition-all"
            >
              Sign In
            </NavLink>
          )}
        </nav>
      </div>
    </header>
  )
}
