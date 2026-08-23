import type { MediaType, Provider, TrackingStatus } from '../api/client'

export type ModuleSlug = 'games' | 'anime' | 'film' | 'books'

/** An external service this module can pull a library from. */
export interface ModuleProvider {
  provider: Provider
  label: string
  blurb: string
}

/**
 * One kind of thing a module tracks. A module can own several, and they do not share a
 * vocabulary: you watch anime and read manga, and calling both "watching" is simply wrong.
 */
export interface MediaTypeDefinition {
  mediaType: MediaType
  label: string
  statusLabels: Record<TrackingStatus, string>
  searchPlaceholder: string
}

/**
 * What a module contributes to the shared UI, mirroring the backend's adapter registry:
 * core pages read this and never branch on a media type of their own.
 */
export interface ModuleDefinition {
  slug: ModuleSlug
  label: string
  types: MediaTypeDefinition[]
  /** What the library and search open on when the module owns several types. */
  defaultMediaType: MediaType
  emptyHint: string
  /** Where this module's connect and import controls live in settings. */
  providers: ModuleProvider[]
}

const watching = {
  PLANNING: 'Plan to watch',
  IN_PROGRESS: 'Watching',
  COMPLETED: 'Completed',
  PAUSED: 'On hold',
  DROPPED: 'Dropped',
} satisfies Record<TrackingStatus, string>

export const MODULES: ModuleDefinition[] = [
  {
    slug: 'games',
    label: 'Games',
    defaultMediaType: 'GAME',
    types: [
      {
        mediaType: 'GAME',
        label: 'Games',
        searchPlaceholder: 'Search games…',
        statusLabels: {
          PLANNING: 'Backlog',
          IN_PROGRESS: 'Playing',
          COMPLETED: 'Completed',
          PAUSED: 'Paused',
          DROPPED: 'Dropped',
        },
      },
    ],
    emptyHint: 'Nothing tracked yet. Connect Steam in settings, or search for a game.',
    providers: [{ provider: 'STEAM', label: 'Steam', blurb: 'Import your games and playtime.' }],
  },
  {
    slug: 'anime',
    label: 'Anime & Manga',
    defaultMediaType: 'ANIME',
    types: [
      {
        mediaType: 'ANIME',
        label: 'Anime',
        searchPlaceholder: 'Search anime…',
        statusLabels: watching,
      },
      {
        mediaType: 'MANGA',
        label: 'Manga',
        searchPlaceholder: 'Search manga…',
        statusLabels: {
          PLANNING: 'Plan to read',
          IN_PROGRESS: 'Reading',
          COMPLETED: 'Completed',
          PAUSED: 'On hold',
          DROPPED: 'Dropped',
        },
      },
    ],
    emptyHint: 'Nothing tracked yet. Connect AniList or MyAnimeList in settings.',
    providers: [
      { provider: 'ANILIST', label: 'AniList', blurb: 'Import your anime and manga lists.' },
      {
        provider: 'MAL',
        label: 'MyAnimeList',
        blurb: 'Import your lists; entries resolve onto their AniList titles.',
      },
    ],
  },
  {
    slug: 'film',
    label: 'Movies & TV',
    defaultMediaType: 'MOVIE',
    types: [
      {
        mediaType: 'MOVIE',
        label: 'Movies',
        searchPlaceholder: 'Search films…',
        statusLabels: { ...watching, PLANNING: 'Watchlist', COMPLETED: 'Watched' },
      },
      {
        mediaType: 'SHOW',
        label: 'TV',
        searchPlaceholder: 'Search shows…',
        statusLabels: { ...watching, PLANNING: 'Watchlist', COMPLETED: 'Watched' },
      },
    ],
    emptyHint: 'Nothing tracked yet. Connect Trakt in settings.',
    providers: [{ provider: 'TRAKT', label: 'Trakt', blurb: 'Import your watched films and shows.' }],
  },
  {
    slug: 'books',
    label: 'Books',
    defaultMediaType: 'BOOK',
    types: [
      {
        mediaType: 'BOOK',
        label: 'Books',
        searchPlaceholder: 'Search books…',
        statusLabels: {
          PLANNING: 'Want to read',
          IN_PROGRESS: 'Reading',
          COMPLETED: 'Read',
          PAUSED: 'On hold',
          DROPPED: 'Abandoned',
        },
      },
    ],
    emptyHint: 'Nothing tracked yet. Upload a Goodreads export in settings.',
    providers: [],
  },
]

export const moduleBySlug = (slug: string | undefined): ModuleDefinition | undefined =>
  MODULES.find((module) => module.slug === slug)

export const mediaTypesOf = (module: ModuleDefinition): MediaType[] =>
  module.types.map((type) => type.mediaType)

export const moduleForMediaType = (mediaType: MediaType): ModuleDefinition | undefined =>
  MODULES.find((module) => mediaTypesOf(module).includes(mediaType))

export const typeDefinitionFor = (mediaType: MediaType): MediaTypeDefinition | undefined =>
  MODULES.flatMap((module) => module.types).find((type) => type.mediaType === mediaType)

/** The words for one kind of thing, falling back to plain ones for anything unmapped. */
export const statusLabelsFor = (mediaType: MediaType): Record<TrackingStatus, string> =>
  typeDefinitionFor(mediaType)?.statusLabels ?? {
    PLANNING: 'Planned',
    IN_PROGRESS: 'In progress',
    COMPLETED: 'Completed',
    PAUSED: 'Paused',
    DROPPED: 'Dropped',
  }
