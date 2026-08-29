import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, api, type MediaDetail, type MediaType, type TrackedItem } from '../api/client'
import { AppShell } from '../components/AppShell'
import { EntryEditDialog } from '../components/EntryEditDialog'
import { MediaAchievements } from '../components/MediaAchievements'
import { MediaFacts } from '../components/MediaFacts'
import { StatusMenu } from '../components/StatusMenu'
import { MediaRecommendations, MediaRelations } from '../components/MediaRelations'
import {
  MediaCharacters,
  MediaGallery,
  MediaLinks,
  MediaScores,
  MediaStaff,
  MediaStats,
  MediaTags,
  MediaTrailer,
} from '../components/MediaPanels'
import { readDetail } from '../components/detailView'
import { moduleForMediaType } from '../modules/registry'

/** AniList writes synopses in HTML, and pads them with blank lines it does not mean. */
const plainText = (html: unknown): string | null => {
  if (typeof html !== 'string' || !html.trim()) return null
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

/** What the people behind a title are called, which is not the same word in every medium. */
const creditsLabel = (mediaType: MediaType): string => {
  switch (mediaType) {
    case 'GAME':
      return 'Made by'
    case 'MOVIE':
    case 'SHOW':
      return 'Crew'
    default:
      return 'Staff'
  }
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
  // Read once, by whichever source wrote it; the panels below never learn which that was.
  const view = readDetail(media.source, detail)
  const banner = view.banner
  const summary = plainText(media.metadata.summary)
  const module = moduleForMediaType(media.mediaType)
  const entry = media.entry

  return (
    <AppShell module={module}>
      {/* Banner and head are one block, so the cover can ride the banner's lower edge
          without fighting the page column's own spacing. */}
      <div className={banner ? 'media-hero has-banner' : 'media-hero'}>
        {banner && (
          <div
            className="media-banner"
            style={{ backgroundImage: `url(${banner})` }}
            aria-hidden="true"
          />
        )}

        <div className="media-head">
          <div className="media-cover-column">
            {media.coverUrl ? (
              <img className="media-cover" src={media.coverUrl} alt="" />
            ) : (
              <div className="media-cover cover-placeholder" aria-hidden="true" />
            )}

            <div className="media-actions">
              {entry ? (
                <StatusMenu
                  entry={entry}
                  onChanged={load}
                  onOpenEditor={() => setEditing(entry)}
                />
              ) : (
                <button type="button" disabled={busy} onClick={() => void track()}>
                  {busy ? 'Adding…' : 'Add to list'}
                </button>
              )}
            </div>
          </div>

          <div className="media-intro">
            <h1>{media.title}</h1>
            {summary && <p className="media-summary">{summary}</p>}
            {view.summaryExtra && <p className="media-summary muted">{view.summaryExtra}</p>}
          </div>
        </div>
      </div>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      <div className="media-layout">
        <div className="media-side">
          <MediaFacts media={media} />
          <MediaScores scores={view.scores} />
          <MediaTags tags={view.tags} />
          <MediaLinks links={view.links} />
        </div>

        <div className="media-main">
          <MediaRelations
            relations={view.relations}
            source={media.source}
            mediaType={media.mediaType}
          />
          <MediaCharacters characters={view.characters} />
          <MediaStaff staff={view.cast} title="Cast" />
          <MediaStaff staff={view.staff} title={creditsLabel(media.mediaType)} />
          <MediaAchievements media={media} />
          <MediaTrailer trailer={view.trailer} />
          <MediaGallery images={view.gallery} />
          <MediaRecommendations recommendations={view.recommendations} source={media.source} />
          <MediaStats statuses={view.statusDistribution} scores={view.scoreDistribution} />
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
