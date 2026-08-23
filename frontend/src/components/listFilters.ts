import type { TrackedItem, TrackingStatus } from '../api/client'

export type SortKey = 'TITLE' | 'SCORE' | 'PROGRESS' | 'GENRE' | 'UPDATED'

export interface ListFilters {
  query: string
  status: TrackingStatus | 'ALL'
  format: string
  genre: string
  sort: SortKey
}

export const EMPTY_FILTERS: ListFilters = {
  query: '',
  status: 'ALL',
  format: '',
  genre: '',
  sort: 'TITLE',
}

/** The progress option is named by the media type, since it counts different things. */
export const SORT_LABELS: Record<Exclude<SortKey, 'PROGRESS'>, string> = {
  TITLE: 'Title',
  SCORE: 'Score',
  GENRE: 'Genre',
  UPDATED: 'Recently updated',
}

export const SORT_ORDER: SortKey[] = ['TITLE', 'SCORE', 'PROGRESS', 'GENRE', 'UPDATED']

export const firstGenre = (entry: TrackedItem): string => {
  const genres = entry.metadata.genres
  return Array.isArray(genres) && typeof genres[0] === 'string' ? genres[0] : ''
}

/** Distinct values actually present on the shelf: an option nothing matches is noise. */
export const distinct = (entries: TrackedItem[], read: (entry: TrackedItem) => unknown): string[] => {
  const found = new Set<string>()
  for (const entry of entries) {
    const value = read(entry)
    if (typeof value === 'string' && value) found.add(value)
    if (Array.isArray(value)) value.filter((v) => typeof v === 'string').forEach((v) => found.add(v))
  }
  return [...found].sort()
}
