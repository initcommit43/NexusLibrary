/**
 * Readers for Open Library's work record.
 *
 * <p>The thinnest of the sources: a book has no cast, no trailer and nothing like a related
 * title, so most of the view stays empty and the panels take themselves off the page. What it
 * does have is what it is about, where it can be read, and a passage from the book itself.
 */
import type { MediaDetailView } from './detailView'
import { emptyView } from './detailView'
import type { MediaTag } from './mediaDetail'

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

export const readBookDetail = (detail: Record<string, unknown>): MediaDetailView => ({
  ...emptyView,
  // A passage from the book, under the synopsis: how it reads is the thing a synopsis cannot
  // say, and it is the only writing here that is the author's own.
  summaryExtra: text(detail.excerpt),
  tags: readSubjects(detail),
  links: readLinks(detail),
  gallery: array(detail.covers).flatMap((raw) => {
    const url = text(raw)
    return url ? [url] : []
  }),
})
