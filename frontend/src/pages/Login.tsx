import { Link } from 'react-router-dom'

export function Login() {
  return (
    <div className="mx-auto max-w-sm">
      <h2 className="mb-6 text-2xl font-semibold text-neutral-900">Sign In</h2>
      <form className="flex flex-col gap-4">
        <div>
          <label className="mb-1 block text-sm font-medium text-neutral-700">Email</label>
          <input
            type="email"
            className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
            placeholder="you@example.com"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-neutral-700">Password</label>
          <input
            type="password"
            className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500"
          />
        </div>
        <button
          type="submit"
          className="rounded-md bg-neutral-900 py-2 text-sm font-medium text-white hover:bg-neutral-700"
        >
          Sign In
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-neutral-500">
        Don't have an account?{' '}
        <Link to="/register" className="underline hover:text-neutral-900">
          Register
        </Link>
      </p>
    </div>
  )
}
