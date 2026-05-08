import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import { apiFetch } from '../lib/api'

interface AuctionAdmin {
  id: string
  slug: string
  title: string
  description: string | null
  status: 'SCHEDULED' | 'LIVE' | 'ENDED' | 'CANCELLED'
  startingBid: number
  currentBid: number | null
  startsAt: string
  endsAt: string
  createdById: string
  createdAt: string
  updatedAt: string
  bidCount: number
}

interface AuctionOrder {
  id: string
  auctionId: string
  userId: string
  amount: number
  currency: string
  shirtSize: string | null
  provider: string
  mockProvider: boolean
  providerOrderId: string | null
  checkoutUrl: string | null
  status: string
  paymentDueAt: string | null
  paidAt: string | null
  expiredAt: string | null
  createdAt: string
}

interface SpringPage<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

interface FormState {
  title: string
  description: string
  startingBid: string
  startsAt: string
  endsAt: string
  status: string
}

function toDatetimeLocal(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function defaultStartsAt(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  d.setMinutes(0, 0, 0)
  return toDatetimeLocal(d.toISOString())
}

function defaultEndsAt(): string {
  const d = new Date()
  d.setDate(d.getDate() + 3)
  d.setMinutes(0, 0, 0)
  return toDatetimeLocal(d.toISOString())
}

function StatusBadge({ status }: { status: string }) {
  const cls =
    status === 'LIVE' ? 'status-live'
    : status === 'SCHEDULED' ? 'status-upcoming'
    : 'status-ended'
  return (
    <span className={`inline-block px-2.5 py-1 text-xs font-medium ${cls}`}>
      {status}
    </span>
  )
}

export function AdminAuctions() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [statusFilter, setStatusFilter] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [mode, setMode] = useState<'list' | 'create' | 'edit'>('list')
  const [editTarget, setEditTarget] = useState<AuctionAdmin | null>(null)
  const [orderAuctionId, setOrderAuctionId] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>({
    title: '', description: '', startingBid: '',
    startsAt: defaultStartsAt(), endsAt: defaultEndsAt(), status: '',
  })
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    if (!user) navigate('/login')
    else if (user.role !== 'ADMIN') navigate('/')
  }, [user, navigate])

  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (statusFilter) params.set('status', statusFilter)
  if (search) params.set('search', search)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'auctions', statusFilter, search, page],
    queryFn: () => apiFetch<SpringPage<AuctionAdmin>>(`/api/v1/admin/auctions?${params}`),
    enabled: user?.role === 'ADMIN',
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'auctions'] })

  const saveMutation = useMutation({
    mutationFn: (payload: object) =>
      editTarget === null
        ? apiFetch<AuctionAdmin>('/api/v1/admin/auctions', { method: 'POST', body: JSON.stringify(payload) })
        : apiFetch<AuctionAdmin>(`/api/v1/admin/auctions/${editTarget.id}`, { method: 'PATCH', body: JSON.stringify(payload) }),
    onSuccess: () => { invalidate(); setMode('list') },
    onError: (e: Error) => setFormError(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/api/v1/admin/auctions/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
    onError: (e: Error) => alert(e.message),
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) =>
      apiFetch<AuctionAdmin>(`/api/v1/admin/auctions/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status: 'CANCELLED' }),
      }),
    onSuccess: invalidate,
    onError: (e: Error) => alert(e.message),
  })

  function openCreate() {
    setEditTarget(null)
    setForm({
      title: '', description: '', startingBid: '',
      startsAt: defaultStartsAt(), endsAt: defaultEndsAt(), status: 'SCHEDULED',
    })
    setFormError(null)
    setMode('create')
  }

  function openEdit(a: AuctionAdmin) {
    setEditTarget(a)
    setForm({
      title: a.title,
      description: a.description ?? '',
      startingBid: String(a.startingBid),
      startsAt: toDatetimeLocal(a.startsAt),
      endsAt: toDatetimeLocal(a.endsAt),
      status: a.status,
    })
    setFormError(null)
    setMode('edit')
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    const payload: Record<string, unknown> = {
      title: form.title,
      description: form.description || null,
      startingBid: parseFloat(form.startingBid),
      startsAt: new Date(form.startsAt).toISOString(),
      endsAt: new Date(form.endsAt).toISOString(),
    }
    if (mode === 'edit') payload.status = form.status
    saveMutation.mutate(payload)
  }

  function handleCancel(id: string, title: string) {
    if (window.confirm(`Cancel auction "${title}"? This cannot be undone.`)) {
      cancelMutation.mutate(id)
    }
  }

  function handleDelete(id: string, title: string) {
    if (window.confirm(`Delete auction "${title}"? This cannot be undone.`)) {
      deleteMutation.mutate(id)
    }
  }

  const canEdit = (a: AuctionAdmin) => a.status === 'SCHEDULED'
  const canCancel = (a: AuctionAdmin) => a.status === 'SCHEDULED' || a.status === 'LIVE'
  const canDelete = (a: AuctionAdmin) => a.status === 'SCHEDULED' && a.bidCount === 0

  const orderQuery = useQuery({
    queryKey: ['admin', 'auction-order', orderAuctionId],
    queryFn: () => apiFetch<AuctionOrder>(`/api/v1/admin/auctions/${orderAuctionId}/order`),
    enabled: orderAuctionId !== null,
    retry: false,
  })

  if (!user || user.role !== 'ADMIN') return null

  const showForm = mode === 'create' || mode === 'edit'

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <button onClick={() => navigate('/admin')} className="accent-link text-sm transition-colors">
          Dashboard
        </button>
        <span className="text-[var(--text-tertiary)]">/</span>
        <h2 className="text-2xl font-light text-[var(--text-primary)]">Auctions</h2>
      </div>

      {showForm && (
        <form onSubmit={handleSubmit} className="surface-panel mb-6 p-6">
          <h3 className="mb-4 text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">
            {mode === 'create' ? 'New Auction' : `Edit — ${editTarget?.title}`}
          </h3>

          {formError && (
            <p className="notice-danger mb-4 px-4 py-2 text-sm">{formError}</p>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="mb-1 block text-xs text-[var(--text-secondary)]">Title</label>
              <input
                required
                value={form.title}
                onChange={(e) => setForm(f => ({ ...f, title: e.target.value }))}
                className="field-shell w-full rounded px-3 py-2 text-sm outline-none"
                placeholder="e.g. Indigo Cascade 001"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="mb-1 block text-xs text-[var(--text-secondary)]">Description</label>
              <textarea
                rows={3}
                value={form.description}
                onChange={(e) => setForm(f => ({ ...f, description: e.target.value }))}
                className="field-shell w-full resize-none rounded px-3 py-2 text-sm outline-none"
                placeholder="Optional description of the shirt design..."
              />
            </div>

            <div>
              <label className="mb-1 block text-xs text-[var(--text-secondary)]">Starting Bid ($)</label>
              <input
                required
                type="number"
                min="0.01"
                step="0.01"
                value={form.startingBid}
                onChange={(e) => setForm(f => ({ ...f, startingBid: e.target.value }))}
                className="field-shell w-full rounded px-3 py-2 text-sm outline-none"
                placeholder="50.00"
              />
            </div>

            {mode === 'edit' && (
              <div>
                <label className="mb-1 block text-xs text-[var(--text-secondary)]">Status</label>
                <select
                  value={form.status}
                  onChange={(e) => setForm(f => ({ ...f, status: e.target.value }))}
                  className="field-shell w-full rounded px-3 py-2 text-sm outline-none"
                >
                  <option value="SCHEDULED">SCHEDULED</option>
                  <option value="LIVE">LIVE</option>
                  <option value="CANCELLED">CANCELLED</option>
                </select>
              </div>
            )}

            <div>
              <label className="mb-1 block text-xs text-[var(--text-secondary)]">Starts At</label>
              <input
                required
                type="datetime-local"
                value={form.startsAt}
                onChange={(e) => setForm(f => ({ ...f, startsAt: e.target.value }))}
                className="field-shell w-full rounded px-3 py-2 text-sm outline-none"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs text-[var(--text-secondary)]">Ends At</label>
              <input
                required
                type="datetime-local"
                value={form.endsAt}
                onChange={(e) => setForm(f => ({ ...f, endsAt: e.target.value }))}
                className="field-shell w-full rounded px-3 py-2 text-sm outline-none"
              />
            </div>
          </div>

          <div className="mt-5 flex gap-3">
            <button
              type="submit"
              disabled={saveMutation.isPending}
              className="btn-primary px-5 py-2 text-sm font-medium disabled:opacity-50"
            >
              {saveMutation.isPending ? 'Saving...' : mode === 'create' ? 'Create Auction' : 'Save Changes'}
            </button>
            <button
              type="button"
              onClick={() => setMode('list')}
              className="btn-secondary px-5 py-2 text-sm"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {!showForm && (
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <input
            type="text"
            placeholder="Search by title..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0) }}
            className="field-shell w-56 rounded px-3 py-2 text-sm outline-none"
          />
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}
            className="field-shell rounded px-3 py-2 text-sm outline-none"
          >
            <option value="">All statuses</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="LIVE">Live</option>
            <option value="ENDED">Ended</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
          <button
            onClick={openCreate}
            className="btn-primary ml-auto px-4 py-2 text-sm font-medium"
          >
            New Auction
          </button>
        </div>
      )}

      {isLoading && <p className="text-[var(--text-secondary)]">Loading...</p>}
      {isError && <p className="text-red-400">Failed to load auctions.</p>}

      {data && !showForm && (
        <>
          <div className="surface-panel overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-[var(--border-subtle)] text-left">
                <tr>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider text-[var(--text-tertiary)]">Title</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider text-[var(--text-tertiary)]">Status</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider text-[var(--text-tertiary)]">Starting Bid</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider text-[var(--text-tertiary)]">Current Bid</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider text-[var(--text-tertiary)]">Bids</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider text-[var(--text-tertiary)]">Starts At</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider text-[var(--text-tertiary)]">Ends At</th>
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {data.content.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-10 text-center text-[var(--text-secondary)]">
                      No auctions found.
                    </td>
                  </tr>
                ) : (
                  data.content.map((a) => (
                    <tr key={a.id} className="transition-colors hover:bg-[rgba(200,136,10,0.05)]">
                      <td className="px-4 py-3">
                        <p className="font-medium text-[var(--text-primary)]">{a.title}</p>
                        <p className="text-xs text-[var(--text-tertiary)]">{a.slug}</p>
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={a.status} />
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">
                        ${Number(a.startingBid).toFixed(2)}
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">
                        {a.currentBid != null ? `$${Number(a.currentBid).toFixed(2)}` : '—'}
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{a.bidCount}</td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">
                        {new Date(a.startsAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">
                        {new Date(a.endsAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-3">
                          {canEdit(a) && (
                            <button
                              onClick={() => openEdit(a)}
                              className="text-xs font-medium text-[var(--accent)] transition-colors hover:text-[var(--accent-hover)]"
                            >
                              Edit
                            </button>
                          )}
                          {canCancel(a) && (
                            <button
                              onClick={() => handleCancel(a.id, a.title)}
                              disabled={cancelMutation.isPending}
                              className="text-xs font-medium text-[var(--warning-text)] transition-colors hover:opacity-80 disabled:opacity-50"
                            >
                              Cancel
                            </button>
                          )}
                          {canDelete(a) && (
                            <button
                              onClick={() => handleDelete(a.id, a.title)}
                              disabled={deleteMutation.isPending}
                              className="text-xs font-medium text-[var(--text-tertiary)] transition-colors hover:text-[var(--danger-text)] disabled:opacity-50"
                            >
                              Delete
                            </button>
                          )}
                          {a.status === 'ENDED' && (
                            <button
                              onClick={() => setOrderAuctionId(a.id)}
                              className="text-xs font-medium text-[var(--accent)] transition-colors hover:opacity-80"
                            >
                              Order
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex items-center justify-between text-sm text-[var(--text-secondary)]">
            <span>
              {data.totalElements} auction{data.totalElements !== 1 ? 's' : ''}
            </span>
            {data.totalPages > 1 && (
              <div className="flex items-center gap-2">
                <button
                  disabled={data.number === 0}
                  onClick={() => setPage((p) => p - 1)}
                  className="btn-secondary px-3 py-1 text-xs disabled:opacity-40"
                >
                  Previous
                </button>
                <span>{data.number + 1} / {data.totalPages}</span>
                <button
                  disabled={data.number + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  className="btn-secondary px-3 py-1 text-xs disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        </>
      )}

      {orderAuctionId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="surface-panel w-full max-w-lg p-6">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-sm font-medium uppercase tracking-widest text-[var(--text-secondary)]">Winner Order</h3>
              <button
                onClick={() => setOrderAuctionId(null)}
                className="text-xs text-[var(--text-tertiary)] hover:text-[var(--text-primary)]"
              >
                Close
              </button>
            </div>

            {orderQuery.isLoading && <p className="text-sm text-[var(--text-secondary)]">Loading order...</p>}
            {orderQuery.isError && <p className="text-sm text-[var(--text-tertiary)]">No order found for this auction.</p>}

            {orderQuery.data && (
              <dl className="space-y-3 text-sm">
                <OrderRow label="Status" value={orderQuery.data.status} />
                <OrderRow label="Amount" value={`$${Number(orderQuery.data.amount).toFixed(2)} ${orderQuery.data.currency}`} />
                <OrderRow label="Size" value={orderQuery.data.shirtSize ?? '—'} />
                <OrderRow
                  label="Provider"
                  value={`${orderQuery.data.provider}${orderQuery.data.mockProvider ? ' (mock)' : ''}`}
                />
                {orderQuery.data.providerOrderId && (
                  <OrderRow label="Provider Ref" value={orderQuery.data.providerOrderId} />
                )}
                {orderQuery.data.checkoutUrl && (
                  <div className="flex items-center justify-between gap-4 border-b border-[var(--border-subtle)] pb-3">
                    <dt className="text-[var(--text-secondary)]">Checkout URL</dt>
                    <dd>
                      <a
                        href={orderQuery.data.checkoutUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="accent-link text-xs underline underline-offset-4"
                      >
                        Open link
                      </a>
                    </dd>
                  </div>
                )}
                {orderQuery.data.paymentDueAt && (
                  <OrderRow
                    label="Payment Due"
                    value={new Date(orderQuery.data.paymentDueAt).toLocaleString()}
                  />
                )}
                {orderQuery.data.paidAt && (
                  <OrderRow
                    label="Paid At"
                    value={new Date(orderQuery.data.paidAt).toLocaleString()}
                  />
                )}
                {orderQuery.data.expiredAt && (
                  <OrderRow
                    label="Expired At"
                    value={new Date(orderQuery.data.expiredAt).toLocaleString()}
                  />
                )}
                <OrderRow label="Created" value={new Date(orderQuery.data.createdAt).toLocaleString()} />
              </dl>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function OrderRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-[var(--border-subtle)] pb-3 last:border-b-0 last:pb-0">
      <dt className="text-[var(--text-secondary)]">{label}</dt>
      <dd className="font-medium text-[var(--text-primary)]">{value}</dd>
    </div>
  )
}
