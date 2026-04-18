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
          className="text-sm text-neutral-500 hover:text-neutral-900 transition-colors"
        >
          ← Dashboard
        </button>
        <span className="text-neutral-300">/</span>
        <h2 className="text-2xl font-semibold text-neutral-900">Users</h2>
      </div>

      <div className="mb-4 flex flex-wrap gap-3">
        <input
          type="text"
          placeholder="Search by email…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          className="w-64 rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-neutral-900"
        />
        <select
          value={role}
          onChange={(e) => { setRole(e.target.value); setPage(0) }}
          className="rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-neutral-900"
        >
          <option value="">All roles</option>
          <option value="BUYER">Buyer</option>
          <option value="ADMIN">Admin</option>
        </select>
      </div>

      {deleteMutation.isError && (
        <p className="mb-3 text-sm text-red-500">
          {(deleteMutation.error as Error).message}
        </p>
      )}

      {isLoading && <p className="text-neutral-500">Loading…</p>}
      {isError && <p className="text-red-500">Failed to load users.</p>}

      {data && (
        <>
          <div className="overflow-x-auto rounded-lg border border-neutral-200">
            <table className="w-full text-sm">
              <thead className="bg-neutral-50 text-left">
                <tr>
                  <th className="px-4 py-3 font-medium text-neutral-500">Email</th>
                  <th className="px-4 py-3 font-medium text-neutral-500">Role</th>
                  <th className="px-4 py-3 font-medium text-neutral-500">Shirt Size</th>
                  <th className="px-4 py-3 font-medium text-neutral-500">Verified</th>
                  <th className="px-4 py-3 font-medium text-neutral-500">Joined</th>
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {data.content.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-10 text-center text-neutral-500">
                      No users found.
                    </td>
                  </tr>
                ) : (
                  data.content.map((u) => (
                    <tr key={u.id} className="bg-white hover:bg-neutral-50 transition-colors">
                      <td className="px-4 py-3 text-neutral-900">{u.email}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
                            u.role === 'ADMIN'
                              ? 'bg-violet-100 text-violet-700'
                              : 'bg-neutral-100 text-neutral-600'
                          }`}
                        >
                          {u.role}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-neutral-600">{u.shirtSize ?? '—'}</td>
                      <td className="px-4 py-3">
                        {u.emailVerified ? (
                          <span className="text-emerald-600 font-medium">Yes</span>
                        ) : (
                          <span className="text-neutral-400">No</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-neutral-500">
                        {new Date(u.createdAt).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {u.role !== 'ADMIN' && (
                          <button
                            onClick={() => handleDelete(u.id, u.email)}
                            disabled={deleteMutation.isPending}
                            className="text-xs font-medium text-red-500 hover:text-red-700 disabled:opacity-50 transition-colors"
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

          <div className="mt-4 flex items-center justify-between text-sm text-neutral-500">
            <span>
              {data.totalElements} user{data.totalElements !== 1 ? 's' : ''}
            </span>
            {data.totalPages > 1 && (
              <div className="flex items-center gap-2">
                <button
                  disabled={data.number === 0}
                  onClick={() => setPage((p) => p - 1)}
                  className="rounded border border-neutral-200 px-3 py-1 hover:bg-neutral-50 disabled:opacity-40 transition-colors"
                >
                  Previous
                </button>
                <span>
                  {data.number + 1} / {data.totalPages}
                </span>
                <button
                  disabled={data.number + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  className="rounded border border-neutral-200 px-3 py-1 hover:bg-neutral-50 disabled:opacity-40 transition-colors"
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
