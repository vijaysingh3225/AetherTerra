export function Footer() {
  return (
    <footer className="border-t border-neutral-200 bg-white">
      <div className="mx-auto max-w-6xl px-6 py-6 text-center text-sm text-neutral-400">
        &copy; {new Date().getFullYear()} Aether Terra. All rights reserved.
      </div>
    </footer>
  )
}
