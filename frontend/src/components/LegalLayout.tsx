import { Link } from 'react-router-dom'
import { Footer } from './Footer'

/**
 * The shell the legal pages share.
 *
 * <p>Deliberately not {@link AppShell}: these pages are outside ProtectedRoute, so they are
 * read by people who are not signed in and by people who never will be. AppShell wants a
 * current module, a search scope and an account menu, none of which mean anything to a
 * reader who arrived here from a link in a sign-up form.
 *
 * <p>One column, measured for reading rather than for cards. Legal copy is prose and long
 * lines of it are what makes a policy go unread.
 */
export const LegalLayout = ({
  title,
  updated,
  children,
}: {
  title: string
  /** When the text last changed, which is the first thing a returning reader looks for. */
  updated?: string
  children: React.ReactNode
}) => (
  <div className="legal-shell">
    <header className="legal-header">
      <Link to="/" className="brand">
        <img src="/pwa-192x192.png" alt="" width={28} height={28} />
        <span>Nexus</span>
      </Link>
    </header>

    <main className="legal-main">
      <h1>{title}</h1>
      {updated && <p className="legal-updated">Last updated {updated}</p>}
      {children}
    </main>

    <Footer />
  </div>
)
