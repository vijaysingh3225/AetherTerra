export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('at_token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(path, { ...options, headers })

  const text = await res.text()
  const json = text ? JSON.parse(text) : {}

  if (!res.ok) {
    throw new Error(json.message || `Request failed (${res.status})`)
  }
  return json.data as T
}
