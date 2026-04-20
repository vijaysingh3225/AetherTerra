import { useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useAuth } from '../context/AuthContext'
import { apiFetch } from '../lib/api'

interface DashboardStats {
  totalUsers: number
  liveAuctions: number
  totalBids: number
  pendingFulfillments: number
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="surface-panel rounded-2xl p-6">
      <p className="text-sm text-[var(--text-secondary)]">{label}</p>
      <p className="mt-2 text-3xl font-semibold text-[var(--text-primary)]">{value}</p>
    </div>
  )
}

export function Admin() {
  const { user } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!user) navigate('/login')
    else if (user.role !== 'ADMIN') navigate('/')
  }, [user, navigate])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => apiFetch<DashboardStats>('/api/v1/admin/dashboard'),
    enabled: user?.role === 'ADMIN',
  })

  if (!user || user.role !== 'ADMIN') return null

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-2xl font-semibold text-[var(--text-primary)]">Admin Dashboard</h2>
        <p className="mt-1 text-sm text-[var(--text-secondary)]">Signed in as {user.email}</p>
      </div>

      {isLoading && <p className="text-[var(--text-secondary)]">Loading stats...</p>}
      {isError && <p className="text-red-400">Failed to load dashboard.</p>}

      {data && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Total Users" value={data.totalUsers} />
          <StatCard label="Live Auctions" value={data.liveAuctions} />
          <StatCard label="Total Bids" value={data.totalBids} />
          <StatCard label="Pending Fulfillments" value={data.pendingFulfillments} />
        </div>
      )}

      <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[
          { title: 'Auctions', desc: 'Create, schedule, and cancel auctions.', href: null },
          { title: 'Users', desc: 'View and manage registered users.', href: '/admin/users' },
          { title: 'Bids', desc: 'Monitor bid activity.', href: null },
          { title: 'Orders', desc: 'Post-auction fulfillment - Shopify integration pending.', href: null },
        ].map((card) =>
          card.href ? (
            <Link
              key={card.title}
              to={card.href}
              className="surface-panel rounded-2xl p-5 transition-all hover:border-[rgba(111,168,220,0.34)] hover:shadow-[0_18px_38px_rgba(0,0,0,0.24)]"
            >
              <h3 className="font-medium text-[var(--text-primary)]">{card.title}</h3>
              <p className="mt-1 text-sm text-[var(--text-secondary)]">{card.desc}</p>
            </Link>
          ) : (
            <div key={card.title} className="surface-panel rounded-2xl p-5 opacity-65">
              <h3 className="font-medium text-[var(--text-primary)]">{card.title}</h3>
              <p className="mt-1 text-sm text-[var(--text-secondary)]">{card.desc}</p>
            </div>
          )
        )}
      </div>
    </div>
  )
}
