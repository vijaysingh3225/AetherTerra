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
    <div className="surface-panel p-6">
      <p className="text-xs uppercase tracking-widest text-[var(--text-tertiary)]">{label}</p>
      <p className="mt-3 text-3xl font-light text-[var(--text-primary)]">{value}</p>
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
        <p className="eyebrow-label mb-3 text-xs font-medium">Control Panel</p>
        <h2 className="text-2xl font-light text-[var(--text-primary)]">Admin Dashboard</h2>
        <p className="mt-1 text-sm text-[var(--text-secondary)]">Signed in as {user.email}</p>
      </div>

      {isLoading && <p className="text-[var(--text-secondary)]">Loading stats...</p>}
      {isError && <p className="text-red-400">Failed to load dashboard.</p>}

      {data && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Total Users" value={data.totalUsers} />
          <StatCard label="Live Auctions" value={data.liveAuctions} />
          <StatCard label="Total Bids" value={data.totalBids} />
          <StatCard label="Pending Fulfillments" value={data.pendingFulfillments} />
        </div>
      )}

      <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {[
          { title: 'Auctions', desc: 'Create, schedule, and cancel auctions.', href: '/admin/auctions' },
          { title: 'Users', desc: 'View and manage registered users.', href: '/admin/users' },
          { title: 'Bids', desc: 'Monitor bid activity.', href: null },
          { title: 'Orders', desc: 'Post-auction fulfillment — Shopify integration pending.', href: null },
        ].map((card) =>
          card.href ? (
            <Link
              key={card.title}
              to={card.href}
              className="surface-panel p-5 transition-all hover:border-[rgba(200,136,10,0.30)]"
            >
              <h3 className="text-sm font-medium text-[var(--text-primary)]">{card.title}</h3>
              <p className="mt-1 text-xs text-[var(--text-secondary)]">{card.desc}</p>
            </Link>
          ) : (
            <div key={card.title} className="surface-panel p-5 opacity-50">
              <h3 className="text-sm font-medium text-[var(--text-primary)]">{card.title}</h3>
              <p className="mt-1 text-xs text-[var(--text-secondary)]">{card.desc}</p>
            </div>
          )
        )}
      </div>
    </div>
  )
}
