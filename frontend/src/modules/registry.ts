import type { MediaType, Provider, TrackingStatus } from '../api/client'

export type ProgressUnit = 'MINUTES' | 'EPISODES' | 'CHAPTERS' | 'PAGES'

export type ModuleSlug = 'games' | 'anime' | 'film' | 'books'

/** An external service this module can pull a library from. */
export interface ModuleProvider {
  provider: Provider
  label: string
  blurb: string
  /** What an uploaded export needs in it, for the CSV route that needs no account. */
  csvHint?: string
}

/**
 * A column of the desktop list view. Each type picks its own, because what is worth a column
 * differs: a format for anime, hours for games, an author for books.
 */
export type ListColumn =
  | 'score'
  | 'progress'
  | 'hours'
  | 'format'
  | 'seasons'
  | 'runtime'
  | 'year'
  | 'author'

/**
 * One kind of thing a module tracks. A module can own several, and they do not share a
 * vocabulary: you watch anime and read manga, and calling both "watching" is simply wrong.
 */
export interface MediaTypeDefinition {
  mediaType: MediaType
  label: string
  /** What home calls this shelf; the header nav, library heading and profile use `label`. */
  listLabel: string
  /** Lowercase media type, used in the URL. */
  slug: string
  /** What progress means here — hours played, episodes watched, chapters read. */
  progressLabel: string
  /** The unit progress is counted in, as the backend stores it; a film counts none. */
  progressUnit: ProgressUnit | null
  statusLabels: Record<TrackingStatus, string>
  /** Section order down the page; each community has its own habit. */
  statusOrder: TrackingStatus[]
  searchPlaceholder: string
  /** The columns after the title in the desktop list view, left to right. */
  listColumns: ListColumn[]
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
  /**
   * What an empty library says, split around the word "settings", which the page makes a
   * link: the way in has to be one tap away, not a place the reader is told to go and find.
   */
  emptyHint: { lead: string; tail: string }
  /** Where this module's connect and import controls live in settings. */
  providers: ModuleProvider[]
  /**
   * Whether this module's shelves can be carried out as a CSV. False for games: a Steam
   * library is not a list anyone keeps by hand, and connecting the account brings the whole
   * thing back whenever it is wanted.
   */
  exportsCsv: boolean
}

/**
 * The same five shelves in every module, named with the verb of the medium.
 *
 * <p>Only the two shelves that describe an activity change their wording: you plan to watch a
 * film and to read a book, and you are watching or reading it now. The other three describe
 * where a title stands rather than what you do with it — finished is finished, whether it was
 * played or read — and a module wording those differently only makes the same shelf harder to
 * recognise as you move between them.
 */
const shelves = (planning: string, inProgress: string): Record<TrackingStatus, string> => ({
  PLANNING: planning,
  IN_PROGRESS: inProgress,
  COMPLETED: 'Completed',
  PAUSED: 'On hold',
  DROPPED: 'Dropped',
})

const watching = shelves('Plan to watch', 'Watching')
const reading = shelves('Plan to read', 'Reading')
const playing = shelves('Plan to play', 'Playing')

export const MODULES: ModuleDefinition[] = [
  {
    slug: 'games',
    label: 'Games',
    defaultMediaType: 'GAME',
    types: [
      {
        mediaType: 'GAME',
        label: 'Games',
        listLabel: 'Game List',
        slug: 'games',
        progressLabel: 'Hours played',
        progressUnit: 'MINUTES',
        listColumns: ['score', 'hours', 'year'],
        searchPlaceholder: 'Search games…',
        statusOrder: ['IN_PROGRESS', 'PLANNING', 'COMPLETED', 'PAUSED', 'DROPPED'],
        statusLabels: playing,
      },
    ],
    emptyHint: { lead: 'Nothing tracked yet. Connect Steam in', tail: ', or search for a game.' },
    exportsCsv: false,
    providers: [{
        provider: 'STEAM',
        label: 'Steam',
        blurb: 'Import your games and playtime.',
      }],
  },
  {
    slug: 'anime',
    label: 'Anime & Manga',
    defaultMediaType: 'ANIME',
    types: [
      {
        mediaType: 'ANIME',
        label: 'Anime',
        listLabel: 'Anime List',
        slug: 'anime',
        progressLabel: 'Episodes',
        progressUnit: 'EPISODES',
        listColumns: ['score', 'progress', 'format'],
        searchPlaceholder: 'Search anime…',
        statusOrder: ['IN_PROGRESS', 'COMPLETED', 'PAUSED', 'DROPPED', 'PLANNING'],
        statusLabels: watching,
      },
      {
        mediaType: 'MANGA',
        label: 'Manga',
        listLabel: 'Manga List',
        slug: 'manga',
        progressLabel: 'Chapters',
        progressUnit: 'CHAPTERS',
        listColumns: ['score', 'progress', 'format'],
        searchPlaceholder: 'Search manga…',
        statusOrder: ['IN_PROGRESS', 'COMPLETED', 'PAUSED', 'DROPPED', 'PLANNING'],
        statusLabels: reading,
      },
    ],
    emptyHint: { lead: 'Nothing tracked yet. Connect AniList or MyAnimeList in', tail: '.' },
    exportsCsv: true,
    providers: [
      {
        provider: 'ANILIST',
        label: 'AniList',
        blurb: 'Import your anime and manga lists.',
        csvHint: 'Needs an anilist_id column — an id from anywhere else would match the wrong title.',
      },
      {
        provider: 'MAL',
        label: 'MyAnimeList',
        blurb: 'Import your lists; entries resolve onto their AniList titles.',
        csvHint: 'Needs a MyAnimeList id column, such as series_animedb_id or mal_id.',
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
        listLabel: 'Movie List',
        slug: 'movies',
        progressLabel: 'Progress',
        progressUnit: null,
        listColumns: ['score', 'runtime', 'year'],
        searchPlaceholder: 'Search movies…',
        statusOrder: ['IN_PROGRESS', 'COMPLETED', 'PAUSED', 'DROPPED', 'PLANNING'],
        statusLabels: watching,
      },
      {
        mediaType: 'SHOW',
        label: 'TV',
        listLabel: 'TV List',
        slug: 'tv',
        progressLabel: 'Episodes',
        progressUnit: 'EPISODES',
        listColumns: ['score', 'progress', 'seasons'],
        searchPlaceholder: 'Search shows…',
        statusOrder: ['IN_PROGRESS', 'COMPLETED', 'PAUSED', 'DROPPED', 'PLANNING'],
        statusLabels: watching,
      },
    ],
    emptyHint: { lead: 'Nothing tracked yet. Connect Simkl in', tail: ', or search for a movie.' },
    exportsCsv: true,
    providers: [
      {
        provider: 'SIMKL',
        label: 'Simkl',
        blurb: 'Import your films and shows, with what you are part way through.',
        csvHint: "Needs a TMDB or IMDb id column, both of which Simkl's CSV backup carries.",
      },
    ],
  },
  {
    slug: 'books',
    label: 'Books',
    defaultMediaType: 'BOOK',
    types: [
      {
        mediaType: 'BOOK',
        label: 'Books',
        listLabel: 'Book List',
        slug: 'books',
        progressLabel: 'Pages',
        progressUnit: 'PAGES',
        listColumns: ['score', 'progress', 'author'],
        searchPlaceholder: 'Search books…',
        statusOrder: ['IN_PROGRESS', 'PLANNING', 'COMPLETED', 'PAUSED', 'DROPPED'],
        statusLabels: reading,
      },
    ],
    emptyHint: { lead: 'Nothing tracked yet. Upload a Goodreads export in', tail: '.' },
    exportsCsv: true,
    providers: [
      {
        provider: 'GOODREADS',
        label: 'Goodreads',
        blurb: 'Import your shelves from an export. Goodreads has no API to connect to.',
        csvHint: 'Export yours from goodreads.com/review/import — the file it emails you.',
      },
    ],
  },
]

/**
 * Where a title opens, whether it is one this reader tracks or only a catalogue result.
 *
 * <p>One path for both. An entry used to have a page of its own, holding the controls for
 * editing it; those live in the dialog behind each card's pencil now, and everything else that
 * page showed — the achievements, the review — is on the title's page beside what the source
 * knows about it. That page resolves anything the source knows, tracked or not, which is why
 * this needs no entry to point at.
 */
export const mediaPathFor = (title: { source: string; externalId: string }): string =>
  `/media/${title.source}/${encodeURIComponent(title.externalId)}`

export const moduleBySlug = (slug: string | undefined): ModuleDefinition | undefined =>
  MODULES.find((module) => module.slug === slug)

export const mediaTypesOf = (module: ModuleDefinition): MediaType[] =>
  module.types.map((type) => type.mediaType)

export const moduleForMediaType = (mediaType: MediaType): ModuleDefinition | undefined =>
  MODULES.find((module) => mediaTypesOf(module).includes(mediaType))

export const typeBySlug = (
  module: ModuleDefinition,
  slug: string | undefined,
): MediaTypeDefinition | undefined => module.types.find((type) => type.slug === slug)

export const defaultTypeOf = (module: ModuleDefinition): MediaTypeDefinition =>
  module.types.find((type) => type.mediaType === module.defaultMediaType) ?? module.types[0]

export const typeDefinitionFor = (mediaType: MediaType): MediaTypeDefinition | undefined =>
  MODULES.flatMap((module) => module.types).find((type) => type.mediaType === mediaType)

/**
 * Browse, narrowed to one filter value — a genre, a tag — for the kind of thing named.
 *
 * <p>The page reads every search param it does not own as a filter, so a link into it is a
 * filter already chosen: arriving from "isekai" on a title's page lands on the isekai list
 * rather than on an empty bar to fill in again.
 */
export const browsePathFor = (mediaType: MediaType, field: string, value: string): string =>
  browsePathWith(mediaType, { [field]: value })

/** Browse for a media type with several filters, or an order, already applied. */
export const browsePathWith = (mediaType: MediaType, filters: Record<string, string>): string => {
  const module = moduleForMediaType(mediaType)
  const type = typeDefinitionFor(mediaType)
  if (!module || !type) return '/browse'

  const params = new URLSearchParams({ module: module.slug, type: type.slug, ...filters })
  return `/browse?${params}`
}

/** Words for a status across media, where no one medium's verbs apply. */
export const PLAIN_STATUS: Record<TrackingStatus, string> = {
  PLANNING: 'Planned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  PAUSED: 'On hold',
  DROPPED: 'Dropped',
}

/** The words for one kind of thing, falling back to plain ones for anything unmapped. */
export const statusLabelsFor = (mediaType: MediaType): Record<TrackingStatus, string> =>
  typeDefinitionFor(mediaType)?.statusLabels ?? {
    PLANNING: 'Planned',
    IN_PROGRESS: 'In progress',
    COMPLETED: 'Completed',
    PAUSED: 'Paused',
    DROPPED: 'Dropped',
  }

/**
 * Which media types have a browse filter whose values are the words shown here.
 *
 * <p>AniList files by name, so a genre on this page is the genre a filter takes. TMDB and
 * IGDB file by id and show a name, so the same link would ask for a genre called "Action"
 * where the filter wanted 28 — those stay as plain text until their filters take names.
 */
const FILTERS_BY_NAME: ReadonlySet<MediaType> = new Set(['ANIME', 'MANGA'])

export const filtersTakeNames = (mediaType: MediaType) => FILTERS_BY_NAME.has(mediaType)

/** Where a tag leads: the browse filter holding it, for a media type whose filter takes names. */
export const tagPathFor = (mediaType: MediaType, tag: string): string | undefined =>
  filtersTakeNames(mediaType) ? browsePathFor(mediaType, 'genres', 'tag:' + tag) : undefined
