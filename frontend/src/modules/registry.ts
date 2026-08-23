import type { MediaType, TrackingStatus } from '../api/client'

export type ModuleSlug = 'games' | 'anime' | 'film' | 'books'

/**
 * What a module contributes to the shared UI, mirroring the backend's adapter registry:
 * core pages read this and never branch on a media type of their own.
 */
export interface ModuleDefinition {
  slug: ModuleSlug
  label: string
  /** A module can own more than one: AniList is canonical for anime and manga alike. */
  mediaTypes: MediaType[]
  /** What search opens on when the module owns several types. */
  defaultMediaType: MediaType
  /** The domain is shared; the words are not. */
  statusLabels: Record<TrackingStatus, string>
  emptyHint: string
}

export const MODULES: ModuleDefinition[] = [
  {
    slug: 'games',
    label: 'Games',
    mediaTypes: ['GAME'],
    defaultMediaType: 'GAME',
    statusLabels: {
      PLANNING: 'Backlog',
      IN_PROGRESS: 'Playing',
      COMPLETED: 'Completed',
      PAUSED: 'Paused',
      DROPPED: 'Dropped',
    },
    emptyHint: 'Nothing tracked yet. Connect Steam in settings, or find a game to get started.',
  },
  {
    slug: 'anime',
    label: 'Anime & Manga',
    mediaTypes: ['ANIME', 'MANGA'],
    defaultMediaType: 'ANIME',
    statusLabels: {
      PLANNING: 'Plan to watch',
      IN_PROGRESS: 'Watching',
      COMPLETED: 'Completed',
      PAUSED: 'On hold',
      DROPPED: 'Dropped',
    },
    emptyHint: 'Nothing tracked yet. Connect AniList or MyAnimeList in settings to fill this in.',
  },
  {
    slug: 'film',
    label: 'Movies & TV',
    mediaTypes: ['MOVIE', 'SHOW'],
    defaultMediaType: 'MOVIE',
    statusLabels: {
      PLANNING: 'Watchlist',
      IN_PROGRESS: 'Watching',
      COMPLETED: 'Watched',
      PAUSED: 'On hold',
      DROPPED: 'Dropped',
    },
    emptyHint: 'Nothing tracked yet. Connect Trakt in settings to fill this in.',
  },
  {
    slug: 'books',
    label: 'Books',
    mediaTypes: ['BOOK'],
    defaultMediaType: 'BOOK',
    statusLabels: {
      PLANNING: 'Want to read',
      IN_PROGRESS: 'Reading',
      COMPLETED: 'Read',
      PAUSED: 'On hold',
      DROPPED: 'Abandoned',
    },
    emptyHint: 'Nothing tracked yet. Upload a Goodreads export in settings to fill this in.',
  },
]

export const moduleBySlug = (slug: string | undefined): ModuleDefinition | undefined =>
  MODULES.find((module) => module.slug === slug)

export const moduleForMediaType = (mediaType: MediaType): ModuleDefinition | undefined =>
  MODULES.find((module) => module.mediaTypes.includes(mediaType))
