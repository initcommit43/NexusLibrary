import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, api, type MediaDetail, type TrackedItem } from '../api/client'
import { AppShell } from '../components/AppShell'
import { EntryEditDialog } from '../components/EntryEditDialog'
import { MediaFacts } from '../components/MediaFacts'
import { MediaRelations } from '../components/MediaRelations'
import { statusLabelsFor, moduleForMediaType } from '../modules/registry'

/** AniList writes synopses in HTML; the tags are markup, not something to print. */
const plainText = (html: unknown): string | null => {
  if (typeof html !== 'string' || !html.trim()) return null
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/&amp;/g, '&')
    .trim()
}

export const MediaPage = () => {
  const { source, externalId } = useParams()
  // Keyed by the title being shown, so following a relation drops the previous one without
  // a second render pass to clear it.
  const [loaded, setLoaded] = useState<{ key: string; media: MediaDetail } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState<TrackedItem | null>(null)

  const key = `${source}/${externalId}`
  const media = loaded?.key === key ? loaded.media : null

  const load = useCallback(() => {
    if (!source || !externalId) return
    api
      .media(source, externalId)
      .then((next) => {
        setError(null)
        setLoaded({ key: `${source}/${externalId}`, media: next })
      })
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load that title.'),
      )
  }, [source, externalId])

  useEffect(load, [load])

  const track = async () => {
    if (!media) return
    setBusy(true)
    setError(null)
    try {
      await api.createEntry({
        source: media.source,
        externalId: media.externalId,
        status: 'PLANNING',
      })
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add that to your list.')
    } finally {
      setBusy(false)
    }
  }

  if (error && !media) {
    return (
      <AppShell>
        <p className="alert" role="alert">
          {error}
        </p>
      </AppShell>
    )
  }

  if (!media) {
    return (
      <AppShell>
        <p className="muted">Loading…</p>
      </AppShell>
    )
  }

  const detail = (media.metadata.detail ?? {}) as Record<string, unknown>
  const banner = typeof detail.bannerImage === 'string' ? detail.bannerImage : null
  const summary = plainText(media.metadata.summary)
  const module = moduleForMediaType(media.mediaType)
  const entry = media.entry

  return (
    <AppShell module={module}>
      {banner && (
        <div className="media-banner" style={{ backgroundImage: `url(${banner})` }} aria-hidden="true" />
      )}

      <div className="media-head">
        {media.coverUrl ? (
          <img className="media-cover" src={media.coverUrl} alt="" />
        ) : (
          <div className="media-cover cover-placeholder" aria-hidden="true" />
        )}

        <div className="media-intro">
          <h1>{media.title}</h1>
          {summary && <p className="media-summary">{summary}</p>}

          <div className="media-actions">
            {entry ? (
              <>
                <button type="button" onClick={() => setEditing(entry)}>
                  {statusLabelsFor(media.mediaType)[entry.status]}
                </button>
                <span className="muted">On your list</span>
              </>
            ) : (
              <button type="button" disabled={busy} onClick={() => void track()}>
                {busy ? 'Adding…' : 'Add to list'}
              </button>
            )}
          </div>
        </div>
      </div>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      <div className="media-layout">
        <MediaFacts media={media} />

        <div className="media-main">
          <MediaRelations detail={detail} source={media.source} />
        </div>
      </div>

      {editing && (
        <EntryEditDialog
          entry={editing}
          onClose={() => setEditing(null)}
          onSaved={() => load()}
          onDeleted={() => load()}
        />
      )}
    </AppShell>
  )
}
