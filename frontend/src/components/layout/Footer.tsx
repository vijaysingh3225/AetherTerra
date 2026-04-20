export function Footer() {
  return (
    <footer className="border-t border-[var(--border-subtle)] bg-[rgba(11,13,16,0.78)]">
      <div className="mx-auto max-w-6xl px-6 py-6 text-center text-sm text-[var(--text-tertiary)]">
        &copy; {new Date().getFullYear()} Aether Terra. All rights reserved.
      </div>
    </footer>
  )
}
