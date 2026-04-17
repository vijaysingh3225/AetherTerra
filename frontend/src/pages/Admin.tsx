import { useNavigate } from 'react-router-dom'
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
    <div className="rounded-lg border border-neutral-200 bg-white p-6">
      <p className="text-sm text-neutral-500">{label}</p>
      <p className="mt-2 text-3xl font-semibold text-neutral-900">{value}</p>
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
        <h2 className="text-2xl font-semibold text-neutral-900">Admin Dashboard</h2>
        <p className="mt-1 text-sm text-neutral-500">Signed in as {user.email}</p>
      </div>

      {isLoading && <p className="text-neutral-500">Loading stats…</p>}
      {isError && <p className="text-red-500">Failed to load dashboard.</p>}

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
          { title: 'Auctions', desc: 'Create, schedule, and cancel auctions.' },
          { title: 'Users', desc: 'View and manage registered users.' },
          { title: 'Bids', desc: 'Monitor bid activity.' },
          { title: 'Orders', desc: 'Post-auction fulfillment — Shopify integration pending.' },
        ].map((card) => (
          <div key={card.title} className="rounded-lg border border-neutral-200 bg-white p-5">
            <h3 className="font-medium text-neutral-900">{card.title}</h3>
            <p className="mt-1 text-sm text-neutral-500">{card.desc}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
