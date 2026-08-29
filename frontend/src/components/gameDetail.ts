/**
 * Readers for IGDB's detail payload.
 *
 * <p>Flat lists where AniList nests edges, and a vocabulary of its own — a game has engines,
 * modes and perspectives where an anime has studios and a season. Both end at the same view;
 * this is the only place that has to know how IGDB says any of it.
 */
import type { MediaDetailView, RelatedTitle, Score } from './detailView'
import { emptyView } from './detailView'
import type { MediaTag, Person } from './mediaDetail'

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() ? value : null

const record = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}

const array = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])

const rounded = (value: unknown): string | null =>
  typeof value === 'number' ? `${Math.round(value)}%` : null

const count = (value: unknown): string | null =>
  typeof value === 'number' ? `${value.toLocaleString()} ratings` : null

/**
 * Who made it, as the people panel reads people.
 *
 * <p>No portraits: IGDB knows companies, not faces, so these come through as names and roles
 * and the tile draws its blank where an image would be.
 */
const readCompanies = (detail: Record<string, unknown>): Person[] =>
  array(detail.companies).flatMap((raw) => {
    const company = record(raw)
    const name = text(company.name)
    if (!name) return []
    return [{ id: name, name, image: null, role: text(company.role) }]
  })

/**
 * What kind of game it is, in the shape tags are read.
 *
 * <p>IGDB does not rank these the way AniList ranks its tags — a theme either applies or it
 * does not — so they carry no percentage and the panel shows none.
 */
const readTags = (detail: Record<string, unknown>): MediaTag[] =>
  (['themes', 'modes', 'perspectives', 'engines'] as const).flatMap((key) =>
    array(detail[key]).flatMap((raw) => {
      const name = text(raw)
      return name ? [{ name, rank: 0, spoiler: false }] : []
    }),
  )

const readRelated = (detail: Record<string, unknown>, key: string, fallback: string): RelatedTitle[] =>
  array(detail[key]).flatMap((raw) => {
    const game = record(raw)
    const title = text(game.name)
    if (!title) return []
    return [
      {
        id: String(game.id ?? title),
        title,
        cover: text(game.cover),
        relation: text(game.relation) ?? fallback,
        type: null,
        format: null,
        year: null,
      },
    ]
  })

const readScores = (detail: Record<string, unknown>): Score[] => {
  const scores: Score[] = []

  const critic = rounded(detail.criticRating)
  if (critic) {
    scores.push({ label: 'Critics', value: critic, hint: count(detail.criticRatingCount) })
  }

  const players = rounded(detail.rating)
  if (players) {
    scores.push({ label: 'Players', value: players, hint: count(detail.ratingCount) })
  }

  return scores
}

const readLinks = (detail: Record<string, unknown>) =>
  array(detail.websites).flatMap((raw) => {
    const site = record(raw)
    const url = text(site.url)
    if (!url) return []
    return [{ site: text(site.site) ?? 'Website', url, language: null }]
  })

/** The first video IGDB lists, which is the one it treats as the trailer. */
const readTrailer = (detail: Record<string, unknown>): string | null => {
  const first = record(array(detail.videos)[0])
  const id = text(first.id)
  return id ? `https://www.youtube.com/embed/${id}` : null
}

export const readGameDetail = (detail: Record<string, unknown>): MediaDetailView => {
  const gallery = array(detail.screenshots).flatMap((raw) => {
    const url = text(raw)
    return url ? [url] : []
  })

  return {
    ...emptyView,
    // A screenshot is the closest thing a game has to the banner an anime carries.
    banner: gallery[0] ?? null,
    trailer: readTrailer(detail),
    summaryExtra: text(detail.storyline),
    staff: readCompanies(detail),
    tags: readTags(detail),
    links: readLinks(detail),
    relations: [
      ...readRelated(detail, 'related', 'Related'),
      ...readRelated(detail, 'similar', 'Similar'),
    ],
    gallery: gallery.slice(1),
    scores: readScores(detail),
  }
}
