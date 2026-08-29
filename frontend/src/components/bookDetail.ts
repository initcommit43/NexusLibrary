/**
 * Readers for Open Library's work record.
 *
 * <p>A different page from the other three by having different panels fill, not by knowing it
 * is a book: no cast, no trailer, nothing like a related title, and in their place the people
 * who wrote it, a passage from it, and what other readers made of it.
 */
import type { MediaDetailView, Score } from './detailView'
import { emptyView } from './detailView'
import type { MediaTag, Person } from './mediaDetail'
import { readScoreDistribution, readStatusDistribution } from './mediaDetail'

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() ? value : null

const record = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}

const array = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])

/**
 * Subjects in the shape tags are read.
 *
 * <p>Open Library's cataloguing does not weight them — a book is about a subject or it is not
 * — so they carry no percentage, as IGDB's themes do not.
 */
const readSubjects = (detail: Record<string, unknown>): MediaTag[] =>
  array(detail.subjects).flatMap((raw) => {
    const name = text(raw)
    return name ? [{ name, rank: 0, spoiler: false }] : []
  })

const readLinks = (detail: Record<string, unknown>) =>
  array(detail.links).flatMap((raw) => {
    const link = record(raw)
    const url = text(link.url)
    if (!url) return []
    return [{ site: text(link.site) ?? 'Website', url, language: null }]
  })

const readAuthors = (detail: Record<string, unknown>): Person[] =>
  array(detail.authors).flatMap((raw) => {
    const author = record(raw)
    const name = text(author.name)
    if (!name) return []
    return [
      { id: name, name, image: text(author.image), role: text(author.lived), bio: text(author.bio) },
    ]
  })

/**
 * Open Library rates out of five; every other source on this page rates out of a hundred, and
 * a panel that changed scale by medium would be read wrong at a glance.
 */
const readScores = (detail: Record<string, unknown>): Score[] => {
  const average = detail.ratingAverage
  if (typeof average !== 'number' || average <= 0) return []

  const count = detail.ratingCount
  return [
    {
      label: 'Readers',
      value: `${Math.round(average * 20)}%`,
      hint: typeof count === 'number' ? `${count.toLocaleString()} ratings` : null,
    },
  ]
}

export const readBookDetail = (detail: Record<string, unknown>): MediaDetailView => ({
  ...emptyView,
  excerpt: text(detail.excerpt),
  authors: readAuthors(detail),
  tags: readSubjects(detail),
  links: readLinks(detail),
  scores: readScores(detail),
  // Written in the shape AniList writes its own, so these read back unchanged.
  statusDistribution: readStatusDistribution(detail),
  scoreDistribution: readScoreDistribution(detail),
})
