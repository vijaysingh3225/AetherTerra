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
    <header className="border-b border-neutral-200 bg-white">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link to="/" className="text-xl font-semibold tracking-tight text-neutral-900">
          Aether Terra
        </Link>
        <nav className="flex items-center gap-6 text-sm text-neutral-600">
          <NavLink
            to="/auctions"
            className={({ isActive }) => isActive ? 'text-neutral-900 font-medium' : 'hover:text-neutral-900'}
          >
            Auctions
          </NavLink>

          {user ? (
            <>
              {user.role === 'ADMIN' && (
                <NavLink
                  to="/admin"
                  className={({ isActive }) => isActive ? 'text-neutral-900 font-medium' : 'hover:text-neutral-900'}
                >
                  Admin
                </NavLink>
              )}
              <NavLink
                to="/account"
                className={({ isActive }) => isActive ? 'text-neutral-900 font-medium' : 'hover:text-neutral-900'}
              >
                Account
              </NavLink>
              <button
                onClick={handleLogout}
                className="rounded-md border border-neutral-300 px-4 py-1.5 text-neutral-700 hover:bg-neutral-100"
              >
                Sign Out
              </button>
            </>
          ) : (
            <NavLink
              to="/login"
              className="rounded-md bg-neutral-900 px-4 py-1.5 text-white hover:bg-neutral-700"
            >
              Sign In
            </NavLink>
          )}
        </nav>
      </div>
    </header>
  )
}
