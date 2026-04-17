export function Admin() {
  return (
    <div>
      <h2 className="mb-6 text-2xl font-semibold text-neutral-900">Admin Dashboard</h2>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[
          { title: 'Auctions', desc: 'Create, schedule, and cancel auctions.' },
          { title: 'Users', desc: 'View and manage registered users.' },
          { title: 'Bids', desc: 'Monitor bid activity.' },
          { title: 'Orders', desc: 'Post-auction fulfillment (Shopify integration pending).' },
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
