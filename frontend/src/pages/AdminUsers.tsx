import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import { apiFetch } from '../lib/api'

interface UserSummary {
  id: string
  email: string
  role: 'BUYER' | 'ADMIN'
  shirtSize: string | null
  emailVerified: boolean
  createdAt: string
}

interface SpringPage<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function AdminUsers() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    if (!user) navigate('/login')
    else if (user.role !== 'ADMIN') navigate('/')
  }, [user, navigate])

  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (search) params.set('search', search)
  if (role) params.set('role', role)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'users', search, role, page],
    queryFn: () => apiFetch<SpringPage<UserSummary>>(`/api/v1/admin/users?${params}`),
    enabled: user?.role === 'ADMIN',
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/admin/users/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })

  const handleDelete = (id: string, email: string) => {
    if (window.confirm(`Delete user "${email}"? This cannot be undone.`)) {
      deleteMutation.mutate(id)
    }
  }

  if (!user || user.role !== 'ADMIN') return null

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <button
          onClick={() => navigate('/admin')}
          className="accent-link text-sm transition-colors"
        >
          Back to Dashboard
        </button>
        <span className="text-[var(--text-tertiary)]">/</span>
        <h2 className="text-2xl font-semibold text-[var(--text-primary)]">Users</h2>
      </div>

      <div className="mb-4 flex flex-wrap gap-3">
        <input
          type="text"
          placeholder="Search by email..."
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          className="field-shell w-64 rounded-xl px-3 py-2 text-sm outline-none"
        />
        <select
          value={role}
          onChange={(e) => { setRole(e.target.value); setPage(0) }}
          className="field-shell rounded-xl px-3 py-2 text-sm outline-none"
        >
          <option value="">All roles</option>
          <option value="BUYER">Buyer</option>
          <option value="ADMIN">Admin</option>
        </select>
      </div>

      {deleteMutation.isError && (
        <p className="notice-danger mb-3 rounded-xl px-4 py-2 text-sm">
          {(deleteMutation.error as Error).message}
        </p>
      )}

      {isLoading && <p className="text-[var(--text-secondary)]">Loading...</p>}
      {isError && <p className="text-red-400">Failed to load users.</p>}

      {data && (
        <>
          <div className="surface-panel overflow-x-auto rounded-2xl">
            <table className="w-full text-sm">
              <thead className="bg-[rgba(11,13,16,0.55)] text-left">
                <tr>
                  <th className="px-4 py-3 font-medium text-[var(--text-secondary)]">Email</th>
                  <th className="px-4 py-3 font-medium text-[var(--text-secondary)]">Role</th>
                  <th className="px-4 py-3 font-medium text-[var(--text-secondary)]">Shirt Size</th>
                  <th className="px-4 py-3 font-medium text-[var(--text-secondary)]">Verified</th>
                  <th className="px-4 py-3 font-medium text-[var(--text-secondary)]">Joined</th>
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {data.content.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-10 text-center text-[var(--text-secondary)]">
                      No users found.
                    </td>
                  </tr>
                ) : (
                  data.content.map((u) => (
                    <tr key={u.id} className="bg-transparent transition-colors hover:bg-[rgba(111,168,220,0.05)]">
                      <td className="px-4 py-3 text-[var(--text-primary)]">{u.email}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block rounded-full px-2.5 py-1 text-xs font-medium ${
                            u.role === 'ADMIN'
                              ? 'status-upcoming'
                              : 'status-ended'
                          }`}
                        >
                          {u.role}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">{u.shirtSize ?? '-'}</td>
                      <td className="px-4 py-3">
                        {u.emailVerified ? (
                          <span className="text-[var(--sage)] font-medium">Yes</span>
                        ) : (
                          <span className="text-[var(--text-tertiary)]">No</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-[var(--text-secondary)]">
                        {new Date(u.createdAt).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {u.role !== 'ADMIN' && (
                          <button
                            onClick={() => handleDelete(u.id, u.email)}
                            disabled={deleteMutation.isPending}
                            className="text-xs font-medium text-[var(--champagne)] transition-colors hover:text-[var(--gold-soft)] disabled:opacity-50"
                          >
                            Delete
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex items-center justify-between text-sm text-[var(--text-secondary)]">
            <span>
              {data.totalElements} user{data.totalElements !== 1 ? 's' : ''}
            </span>
            {data.totalPages > 1 && (
              <div className="flex items-center gap-2">
                <button
                  disabled={data.number === 0}
                  onClick={() => setPage((p) => p - 1)}
                  className="btn-secondary rounded-lg px-3 py-1 disabled:opacity-40"
                >
                  Previous
                </button>
                <span>
                  {data.number + 1} / {data.totalPages}
                </span>
                <button
                  disabled={data.number + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  className="btn-secondary rounded-lg px-3 py-1 disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
