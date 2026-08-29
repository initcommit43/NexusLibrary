/**
 * Readers for TMDB's detail payload.
 *
 * <p>People where a game has companies: TMDB knows faces, and the page shows them — the cast
 * with the part each played, the crew with the job. Everything else ends at the same view the
 * other sources end at; this is the only place that has to know how TMDB says any of it.
 */
import type { MediaDetailView, RelatedTitle, Score } from './detailView'
import { emptyView } from './detailView'
import type { MediaTag, Person } from './mediaDetail'

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() ? value : null

const record = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}

const array = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])

/** TMDB rates out of ten; every other score on this page is a percentage. */
const percent = (value: unknown): string | null =>
  typeof value === 'number' && value > 0 ? `${Math.round(value * 10)}%` : null

const votes = (value: unknown): string | null =>
  typeof value === 'number' && value > 0 ? `${value.toLocaleString()} votes` : null

const readPeople = (detail: Record<string, unknown>, key: string): Person[] =>
  array(detail[key]).flatMap((raw) => {
    const person = record(raw)
    const name = text(person.name)
    if (!name) return []
    // Two people can share a name, and a name is all TMDB gives the tile to key on; the role
    // is what separates an actor from the director of the same name.
    return [{ id: `${name}-${text(person.role) ?? ''}`, name, image: text(person.image), role: text(person.role) }]
  })

/**
 * What a film is about, in the shape tags are read.
 *
 * <p>TMDB's keywords carry no weight — a film either is about heists or it is not — so they
 * come through unranked, the way IGDB's themes do, and the panel shows no percentage.
 */
const readKeywords = (detail: Record<string, unknown>): MediaTag[] =>
  array(detail.keywords).flatMap((raw) => {
    const name = text(raw)
    return name ? [{ name, rank: 0, spoiler: false }] : []
  })

const readRecommendations = (detail: Record<string, unknown>): RelatedTitle[] =>
  array(detail.recommendations).flatMap((raw) => {
    const like = record(raw)
    const title = text(like.name)
    const id = text(like.id)
    if (!title || !id) return []
    return [
      { id, title, cover: text(like.cover), relation: 'Similar', type: null, format: null, year: text(like.year) },
    ]
  })

const readLinks = (detail: Record<string, unknown>) =>
  array(detail.links).flatMap((raw) => {
    const link = record(raw)
    const url = text(link.url)
    if (!url) return []
    return [{ site: text(link.site) ?? 'Website', url, language: null }]
  })

const readScores = (detail: Record<string, unknown>): Score[] => {
  const rating = percent(detail.voteAverage)
  return rating ? [{ label: 'Viewers', value: rating, hint: votes(detail.voteCount) }] : []
}

/** The first video TMDB lists, which is the trailer once trailers are sorted ahead. */
const readTrailer = (detail: Record<string, unknown>): string | null => {
  const first = record(array(detail.videos)[0])
  const id = text(first.id)
  return id ? `https://www.youtube.com/embed/${id}` : null
}

export const readFilmDetail = (detail: Record<string, unknown>): MediaDetailView => {
  const backdrops = array(detail.backdrops).flatMap((raw) => {
    const url = text(raw)
    return url ? [url] : []
  })

  return {
    ...emptyView,
    // The widest still TMDB has is what a film has instead of the banner an anime carries.
    banner: backdrops[0] ?? null,
    trailer: readTrailer(detail),
    summaryExtra: text(detail.tagline),
    cast: readPeople(detail, 'cast'),
    staff: readPeople(detail, 'crew'),
    tags: readKeywords(detail),
    links: readLinks(detail),
    recommendations: readRecommendations(detail),
    gallery: backdrops.slice(1),
    scores: readScores(detail),
  }
}
