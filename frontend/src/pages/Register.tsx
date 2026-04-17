import { Link } from 'react-router-dom'

export function Register() {
  return (
    <div className="mx-auto max-w-sm">
      <h2 className="mb-6 text-2xl font-semibold text-neutral-900">Create Account</h2>
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
        <div>
          <label className="mb-1 block text-sm font-medium text-neutral-700">Shirt Size</label>
          <select className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm outline-none focus:border-neutral-500">
            <option value="">Select size</option>
            {['XS', 'S', 'M', 'L', 'XL', 'XXL'].map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <p className="mt-1 text-xs text-neutral-400">Required before you can place any bids.</p>
        </div>
        <button
          type="submit"
          className="rounded-md bg-neutral-900 py-2 text-sm font-medium text-white hover:bg-neutral-700"
        >
          Create Account
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-neutral-500">
        Already have an account?{' '}
        <Link to="/login" className="underline hover:text-neutral-900">
          Sign In
        </Link>
      </p>
    </div>
  )
}
