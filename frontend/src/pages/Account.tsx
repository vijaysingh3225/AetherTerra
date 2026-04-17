export function Account() {
  return (
    <div className="max-w-lg">
      <h2 className="mb-6 text-2xl font-semibold text-neutral-900">My Account</h2>
      <div className="flex flex-col gap-4">
        <section className="rounded-lg border border-neutral-200 bg-white p-5">
          <h3 className="font-medium text-neutral-800">Profile</h3>
          <p className="mt-1 text-sm text-neutral-500">Manage your email and password.</p>
        </section>
        <section className="rounded-lg border border-neutral-200 bg-white p-5">
          <h3 className="font-medium text-neutral-800">Shirt Size</h3>
          <p className="mt-1 text-sm text-neutral-500">Required before placing your first bid.</p>
        </section>
        <section className="rounded-lg border border-neutral-200 bg-white p-5">
          <h3 className="font-medium text-neutral-800">Payment Method</h3>
          <p className="mt-1 text-sm text-neutral-500">
            {/* Stripe saved payment method — integration pending */}
            Saved payment methods will appear here.
          </p>
        </section>
        <section className="rounded-lg border border-neutral-200 bg-white p-5">
          <h3 className="font-medium text-neutral-800">Bid History</h3>
          <p className="mt-1 text-sm text-neutral-500">Your past and active bids.</p>
        </section>
      </div>
    </div>
  )
}
