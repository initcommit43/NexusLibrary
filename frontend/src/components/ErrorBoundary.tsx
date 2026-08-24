import { Component, type ReactNode } from 'react'

/**
 * The floor under the whole app: a render crash lands here instead of unmounting React
 * and leaving a blank page — which is what a bad import report once did.
 *
 * <p>It only catches crashes in rendering. A rejected fetch or a failed event handler
 * never unmounts anything, so those stay the business of the code that made them; this
 * exists for the one kind of failure that otherwise takes everything else down with it.
 *
 * <p>"Try again" re-renders the same tree, which is enough when the crash came from a
 * moment's bad state — a poll that has since moved on. The library link is the way out
 * when it was not: a full navigation, fresh state, no router involved, since the router
 * may be part of what crashed.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, { crashed: boolean }> {
  state = { crashed: false }

  static getDerivedStateFromError() {
    return { crashed: true }
  }

  componentDidCatch(error: unknown) {
    // The one place the detail survives to; the reader's screen is not where it belongs.
    console.error('The app crashed while rendering', error)
  }

  render() {
    if (!this.state.crashed) return this.props.children

    return (
      <div className="page-centered" role="alert">
        <div className="card crash-card">
          <h1>Something went wrong</h1>
          <p className="muted">
            The page hit an error it could not recover from. Nothing you tracked is
            affected — your library lives on the server, not in this tab.
          </p>
          <div className="crash-actions">
            <button type="button" onClick={() => this.setState({ crashed: false })}>
              Try again
            </button>
            <a href="/">Back to your library</a>
          </div>
        </div>
      </div>
    )
  }
}
