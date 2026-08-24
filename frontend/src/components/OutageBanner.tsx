import { dismissOutage, useOutages } from '../api/outages'

/**
 * One banner for a provider being down, raised the moment anything sights the outage —
 * a failed search, a failed import, whichever came first. The alternative was each
 * feature reporting the same dead service in its own words in its own corner.
 *
 * <p>It says what stops working and why, and nothing else: the failing feature already
 * shows its own message with the service's words. Dismissing it holds until the next
 * sighting; with no new sightings it withdraws by itself, since "was down a moment ago"
 * is not a claim worth keeping on screen.
 */
export const OutageBanner = () => {
  const outages = useOutages()
  if (outages.length === 0) return null

  return (
    <div className="outage-banners">
      {outages.map(({ service }) => (
        <div key={service} className="outage-banner" role="status">
          <span>
            {service} is not responding right now. Anything that depends on it — search,
            imports, syncs — will fail until it recovers.
          </span>
          <button
            type="button"
            className="ghost icon-button"
            aria-label={`Dismiss the ${service} outage notice`}
            title="Dismiss"
            onClick={() => dismissOutage(service)}
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  )
}
