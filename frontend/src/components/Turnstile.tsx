import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'

const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

type TurnstileApi = {
  render: (el: HTMLElement, options: Record<string, unknown>) => string
  remove: (widgetId: string) => void
}

declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

let scriptLoad: Promise<void> | null = null

/**
 * One <script> per page however many widgets ask for it, and one promise so a second asker
 * waits on the first load instead of starting its own. A failed load clears the promise, or
 * every later attempt would inherit the failure.
 */
const loadScript = () => {
  if (!scriptLoad) {
    scriptLoad = new Promise<void>((resolve, reject) => {
      const el = document.createElement('script')
      el.src = SCRIPT_SRC
      el.async = true
      el.onload = () => resolve()
      el.onerror = () => {
        scriptLoad = null
        reject(new Error('Turnstile script did not load'))
      }
      document.head.appendChild(el)
    })
  }
  return scriptLoad
}

type Props = {
  /** A fresh token, or '' when it expires or errors and the reader has to solve again. */
  onToken: (token: string) => void
}

export const Turnstile = ({ onToken }: Props) => {
  const holder = useRef<HTMLDivElement>(null)
  const [failed, setFailed] = useState(false)

  // Held in a ref so the effect below can keep an empty dependency list: the widget is
  // rendered imperatively and must not be torn down and rebuilt whenever the parent
  // re-renders with a new inline callback. Written in an effect rather than during render,
  // which is the rule refs follow.
  const latest = useRef(onToken)
  useEffect(() => {
    latest.current = onToken
  }, [onToken])

  useEffect(() => {
    let widgetId: string | undefined
    let cancelled = false

    const start = async () => {
      const { turnstileSiteKey } = await api.publicConfig()
      // No key configured means the server is not checking either, so show nothing rather
      // than an obstacle that proves nothing.
      if (!turnstileSiteKey || cancelled) return

      await loadScript()
      if (cancelled || !holder.current || !window.turnstile) return

      widgetId = window.turnstile.render(holder.current, {
        sitekey: turnstileSiteKey,
        theme: 'auto',
        callback: (token: string) => latest.current(token),
        'expired-callback': () => latest.current(''),
        'error-callback': () => latest.current(''),
      })
    }

    start().catch(() => {
      if (!cancelled) setFailed(true)
    })

    return () => {
      cancelled = true
      if (widgetId && window.turnstile) {
        window.turnstile.remove(widgetId)
      }
    }
  }, [])

  if (failed) {
    return (
      <p className="alert" role="alert">
        The bot check could not load, so this form cannot be sent. A content blocker or a
        network that filters Cloudflare will do that.
      </p>
    )
  }

  return <div ref={holder} className="bot-check" />
}
