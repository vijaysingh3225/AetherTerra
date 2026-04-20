import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

export function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <header className="border-b border-[var(--border-subtle)] bg-[rgba(11,13,16,0.82)] backdrop-blur-xl">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link to="/" className="text-xl font-semibold tracking-tight text-[var(--text-primary)]">
          Aether Terra
        </Link>
        <nav className="flex items-center gap-6 text-sm text-[var(--text-secondary)]">
          <NavLink
            to="/auctions"
            className={({ isActive }) =>
              isActive
                ? 'font-medium text-[var(--aether-blue)]'
                : 'transition-colors hover:text-[var(--text-primary)]'
            }
          >
            Auctions
          </NavLink>

          {user ? (
            <>
              {user.role === 'ADMIN' && (
                <NavLink
                  to="/admin"
                  className={({ isActive }) =>
                    isActive
                      ? 'font-medium text-[var(--aether-blue)]'
                      : 'transition-colors hover:text-[var(--text-primary)]'
                  }
                >
                  Admin
                </NavLink>
              )}
              <NavLink
                to="/account"
                className={({ isActive }) =>
                  isActive
                    ? 'font-medium text-[var(--aether-blue)]'
                    : 'transition-colors hover:text-[var(--text-primary)]'
                }
              >
                Account
              </NavLink>
              <button
                onClick={handleLogout}
                className="btn-secondary rounded-xl px-4 py-1.5 transition-colors"
              >
                Sign Out
              </button>
            </>
          ) : (
            <NavLink
              to="/login"
              className="btn-primary rounded-xl px-4 py-1.5 font-medium transition-all"
            >
              Sign In
            </NavLink>
          )}
        </nav>
      </div>
    </header>
  )
}
