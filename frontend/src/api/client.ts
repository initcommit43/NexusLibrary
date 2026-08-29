import { reportOutage } from './outages'

const BASE = '/api'

export type User = {
  id: number
  email: string
  username: string
}

export type AuthResponse = {
  accessToken: string
  user: User
}

export type MediaType = 'GAME' | 'MOVIE' | 'SHOW' | 'ANIME' | 'MANGA' | 'BOOK'

export type TrackingStatus = 'PLANNING' | 'IN_PROGRESS' | 'COMPLETED' | 'PAUSED' | 'DROPPED'

export type SearchResult = {
  mediaType: MediaType
  source: string
  externalId: string
  title: string
  coverUrl: string | null
  releaseDate: string | null
  /** What a ranked shelf shows beside the title. Empty for a plain search hit. */
  facets?: Record<string, unknown>
}

/** One row of a module's browse page. Which rows exist is the backend adapter's decision. */
export type BrowseShelf = {
  id: string
  label: string
}

/** One page of a shelf. `hasMore` is all a "next" button needs, and all every source knows. */
export type BrowseResults = {
  items: SearchResult[]
  hasMore: boolean
}

export type FilterOption = {
  value: string
  label: string
}

/**
 * One control on a browse page's filter bar. Which controls exist is the adapter's answer,
 * not this app's — a season is AniList's idea and a platform is IGDB's — so the bar renders
 * whatever list it is handed.
 */
export type FilterField = {
  id: string
  label: string
  kind: 'TEXT' | 'SELECT' | 'MULTI'
  options: FilterOption[]
}

/** The chosen value of every control, by field id. A multi holds several. */
export type FilterValues = Record<string, string[]>

export type TrackedItem = {
  id: number
  mediaType: MediaType
  source: string
  externalId: string
  title: string
  coverUrl: string | null
  releaseDate: string | null
  metadata: Record<string, unknown>
  status: TrackingStatus
  rating: number | null
  progressCurrent: number | null
  progressMax: number | null
  progressUnit: string | null
  startedAt: string | null
  finishedAt: string | null
  progressExtra: Record<string, unknown> | null
  favorite: boolean
  /** Where it sits among the favourites, null while they are in their default order. */
  favoriteRank: number | null
  /** Set when an import put this here rather than the reader adding it by hand. */
  importedFrom: Provider | null
  notes: string | null
}

/**
 * How a reader arranged their favourite rows: the order, and which of them share a band
 * with the row before them.
 */
export type RowArrangement = { order: MediaType[]; paired: MediaType[] }

/** The image at the head of a profile, the title it came from, and how it is framed. */
export type ProfileBanner = {
  imageUrl: string
  title: string
  mediaType: MediaType
  source: string
  externalId: string
  /** The point of the image held in view, as percentages of it; 50/50 is a plain cover crop. */
  focusX: number
  focusY: number
  /** Hundredths: 100 is the image at cover size, 250 is two and a half times it. */
  zoom: number
}

/** Where the image sits inside the strip, which is not a change of which image it is. */
export type BannerFraming = { focusX: number; focusY: number; zoom: number }

export type MediaDetail = {
  mediaType: MediaType
  source: string
  externalId: string
  title: string
  coverUrl: string | null
  releaseDate: string | null
  itemState: string
  metadata: Record<string, unknown>
  /** This reader's entry for it, when they have one. */
  entry: TrackedItem | null
}

export type Provider = 'STEAM' | 'ANILIST' | 'MAL' | 'SIMKL' | 'GOODREADS'

export type ConnectedAccount = {
  provider: Provider
  externalUserId: string
  connectedAt: string
  lastSyncedAt: string | null
}

export type UnmatchedItem = {
  providerItemId: string
  title: string
  reason: string
}

export type ImportReport = {
  /** Set when the module started background work after the import — Steam's achievements. */
  followUpJobId?: string | null
  created: number
  updated: number
  unmatched: UnmatchedItem[]
}

export type ActivityType =
  | 'ADDED'
  | 'STATUS_CHANGE'
  | 'PROGRESS'
  | 'RATED'
  | 'REVIEWED'
  /** A library arriving from a provider: one event for the run, not one per title. */
  | 'IMPORTED'
  /** A later run of the same provider, recorded only where it changed something. */
  | 'SYNCED'

/** One title a run touched, as the row's hover card reads it. */
export type ActivityChange = { title: string; from: string | null; to: string }

/**
 * Something that happened, to a title or to a whole run.
 *
 * <p>A run belongs to no title, so it answers with nulls where an edit answers with a cover
 * and a name, and says what it did in its payload instead.
 */
export type ActivityEntry = {
  id: number
  type: ActivityType
  mediaType: MediaType | null
  title: string | null
  coverUrl: string | null
  externalId: string | null
  payload: {
    from?: string | null
    to?: string | null
    unit?: string
    status?: string
    provider?: Provider
    added?: number
    advanced?: number
    titles?: ActivityChange[]
  }
  createdAt: string
}

export type Review = {
  id: number
  entryId: number
  body: string
  containsSpoilers: boolean
  createdAt: string
  updatedAt: string
}

export type SyncJob = {
  id: string
  kind: 'IMPORT' | 'ACHIEVEMENTS'
  /** Which connection the run belongs to, so its progress shows under that one. */
  provider: Provider | null
  /** Which stretch of an import the count belongs to; null for work with only one. */
  phase: 'FETCHING' | 'MATCHING' | 'IMPORTING' | null
  state: 'RUNNING' | 'COMPLETE' | 'FAILED' | 'CANCELLED'
  total: number
  processed: number
  changed: number
  message: string | null
  /** Named when the failure was an upstream outage, so the banner can rise from a job too. */
  unavailableService: string | null
  /** What the run produced, once it has — an import's counts and unmatched titles. */
  report: ImportReport | null
  /** Work that started when this one finished, such as Steam's achievements. */
  followUpJobId: string | null
}

export type AchievementCatalogueEntry = {
  id: string
  name: string | null
  description: string | null
  icon: string | null
  lockedIcon: string | null
  hidden: boolean
}

export type AchievementProgress = {
  unlocked: string[]
  unlockedAt: Record<string, number>
  total: number
}

export type TrackPayload = {
  source: string
  externalId: string
  status: TrackingStatus
}

export type UpdateEntryPayload = Partial<{
  status: TrackingStatus
  rating: number
  progressCurrent: number
  progressMax: number
  progressUnit: string
  startedAt: string
  finishedAt: string
  favorite: boolean
  notes: string
}>

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

// The access token is deliberately module state, never localStorage: an XSS payload
// can read storage but cannot read a closure variable it has no reference to.
let accessToken: string | null = null
let onSessionLost: (() => void) | null = null

export const setAccessToken = (token: string | null) => {
  accessToken = token
}

export const onSessionLostHandler = (handler: (() => void) | null) => {
  onSessionLost = handler
}

// Concurrent 401s must trigger exactly one refresh, not one per in-flight request.
let refreshInFlight: Promise<string | null> | null = null

const parseError = async (res: Response): Promise<ApiError> => {
  let message = 'Something went wrong. Please try again.'
  let fieldErrors: Record<string, string> = {}
  try {
    const body = await res.json()
    if (typeof body.message === 'string') message = body.message
    if (body.fieldErrors && typeof body.fieldErrors === 'object') fieldErrors = body.fieldErrors
    // The server names the service when a failure is an upstream outage; every such
    // sighting feeds the one banner, whatever feature happened to hit it first.
    if (typeof body.unavailableService === 'string') reportOutage(body.unavailableService)
  } catch {
    // Non-JSON body (proxy error, gateway timeout) — the default message stands.
  }
  return new ApiError(res.status, message, fieldErrors)
}

const refreshAccessToken = (): Promise<string | null> => {
  refreshInFlight ??= fetch(`${BASE}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  })
    .then(async (res) => {
      if (!res.ok) return null
      const body: AuthResponse = await res.json()
      accessToken = body.accessToken
      return body.accessToken
    })
    .catch(() => null)
    .finally(() => {
      refreshInFlight = null
    })

  return refreshInFlight
}

/**
 * One authenticated call, up to the point where the body starts mattering. Everything that
 * has to happen to every request — the token, the one silent retry after a refresh, the
 * error shape — lives here, so a caller wanting a file rather than JSON does not have to
 * carry a second copy of it.
 */
const send = async (path: string, init: RequestInit = {}, allowRetry = true): Promise<Response> => {
  const headers = new Headers(init.headers)
  // FormData sets its own content type, boundary and all; overriding it makes the upload
  // unparseable on the other end.
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const res = await fetch(BASE + path, { ...init, headers, credentials: 'include' })

  if (res.status === 401 && allowRetry) {
    const renewed = await refreshAccessToken()
    if (renewed) return send(path, init, false)
    accessToken = null
    onSessionLost?.()
  }

  if (!res.ok) throw await parseError(res)
  return res
}

const request = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  const res = await send(path, init)
  if (res.status === 204) return undefined as T

  // An endpoint answering "nothing" — no job is running — returns 200 with no body at all.
  // Handing that to res.json() throws, and a caller that treats a failed poll as no news
  // would then sit on its last value forever rather than noticing the run had finished.
  const body = await res.text()
  return (body ? JSON.parse(body) : null) as T
}

/**
 * A background job that failed against a dead upstream feeds the same banner as a failed
 * request: the import is where an outage is most likely to be met first.
 */
const trackJobOutage = <T extends SyncJob | null>(job: T): T => {
  if (job?.state === 'FAILED' && job.unavailableService) reportOutage(job.unavailableService)
  return job
}

export type ExportedCsv = { filename: string; blob: Blob }

/** The filename out of a Content-Disposition, quoted or bare. */
const filenameFrom = (header: string | null): string | null => {
  const match = header?.match(/filename="?([^";]+)"?/i)
  return match ? match[1] : null
}

export const api = {
  register: (payload: { email: string; username: string; password: string }) =>
    request<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),

  login: (payload: { email: string; password: string }) =>
    request<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),

  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  me: () => request<User>('/auth/me'),

  health: () => request<{ status: string }>('/health'),

  searchCatalog: (mediaType: MediaType, query: string) =>
    request<SearchResult[]>(
      `/catalog/search?mediaType=${mediaType}&q=${encodeURIComponent(query)}`,
    ),

  availableModules: () => request<MediaType[]>('/catalog/modules'),

  updateProfile: (payload: { email?: string; username?: string }) =>
    request<User>('/settings/account', { method: 'PATCH', body: JSON.stringify(payload) }),

  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/settings/account/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),

  deleteAccount: (password: string) =>
    request<void>('/settings/account', {
      method: 'DELETE',
      body: JSON.stringify({ password }),
    }),

  /** The media types this reader switched off. Everything absent from it is on. */
  disabledModules: () =>
    request<{ disabled: MediaType[] }>('/settings/modules').then((body) => body.disabled),

  setDisabledModules: (disabled: MediaType[]) =>
    request<{ disabled: MediaType[] }>('/settings/modules', {
      method: 'PUT',
      body: JSON.stringify({ disabled }),
    }).then((body) => body.disabled),

  browseShelves: (mediaType: MediaType) =>
    request<BrowseShelf[]>(`/catalog/shelves?mediaType=${mediaType}`),

  browse: (mediaType: MediaType, shelf: string, page = 1) =>
    request<BrowseResults>(
      `/catalog/browse?mediaType=${mediaType}&shelf=${encodeURIComponent(shelf)}&page=${page}`,
    ),

  browseFilters: (mediaType: MediaType) =>
    request<FilterField[]>(`/catalog/filters?mediaType=${mediaType}`),

  discover: (mediaType: MediaType, values: FilterValues, page = 1) => {
    const params = new URLSearchParams({ mediaType, page: String(page) })
    // Repeated rather than joined: a multi-select is several values of one name, and a genre
    // is free to contain the character any separator would have to pick.
    for (const [field, chosen] of Object.entries(values)) {
      for (const value of chosen) {
        if (value) params.append(field, value)
      }
    }
    return request<BrowseResults>(`/catalog/discover?${params}`)
  },

  media: (source: string, externalId: string) =>
    request<MediaDetail>(`/catalog/media/${source}/${encodeURIComponent(externalId)}`),

  /** Fetched apart from the page: the first reader of an unimported game waits on Steam. */
  mediaAchievements: (source: string, externalId: string) =>
    request<AchievementCatalogueEntry[]>(
      `/catalog/media/${source}/${encodeURIComponent(externalId)}/achievements`,
    ),

  listEntries: () => request<TrackedItem[]>('/entries'),

  getEntry: (id: number) => request<TrackedItem>(`/entries/${id}`),

  createEntry: (payload: TrackPayload) =>
    request<TrackedItem>('/entries', { method: 'POST', body: JSON.stringify(payload) }),

  updateEntry: (id: number, payload: UpdateEntryPayload) =>
    request<TrackedItem>(`/entries/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),

  deleteEntry: (id: number) => request<void>(`/entries/${id}`, { method: 'DELETE' }),

  /** The whole arrangement, first to last, rather than one card's new position. */
  reorderFavourites: (entryIds: number[]) =>
    request<TrackedItem[]>('/entries/favourites/order', {
      method: 'PUT',
      body: JSON.stringify({ entryIds }),
    }),

  favouriteRowOrder: () => request<RowArrangement>('/settings/favourite-rows'),

  /** Every row the reader has placed, in order; the rest keep the app's own. */
  replaceFavouriteRowOrder: (order: MediaType[], paired: MediaType[] = []) =>
    request<RowArrangement>('/settings/favourite-rows', {
      method: 'PUT',
      body: JSON.stringify({ order, paired }),
    }),

  /** Null when the reader has chosen none, which is a bare profile head rather than an error. */
  profileBanner: () => request<ProfileBanner | null>('/settings/profile-banner'),

  /**
   * Takes the entry rather than an image: the server reads the banner out of that title's
   * detail, so a profile can only ever wear art from something in the reader's own library.
   */
  chooseProfileBanner: (entryId: number) =>
    request<ProfileBanner>('/settings/profile-banner', {
      method: 'PUT',
      body: JSON.stringify({ entryId }),
    }),

  frameProfileBanner: (framing: BannerFraming) =>
    request<ProfileBanner>('/settings/profile-banner', {
      method: 'PATCH',
      body: JSON.stringify(framing),
    }),

  clearProfileBanner: () => request<void>('/settings/profile-banner', { method: 'DELETE' }),

  listIntegrations: () => request<ConnectedAccount[]>('/integrations'),

  steamAuthorizeUrl: () =>
    request<{ url: string }>('/integrations/steam/authorize', { method: 'POST' }),

  anilistAuthorizeUrl: () =>
    request<{ url: string }>('/integrations/anilist/authorize', { method: 'POST' }),

  completeAniListConnect: (code: string) =>
    request<ConnectedAccount>('/integrations/anilist/callback', {
      method: 'POST',
      body: JSON.stringify({ code }),
    }),

  malAuthorizeUrl: () =>
    request<{ url: string }>('/integrations/mal/authorize', { method: 'POST' }),

  /** OAuth like AniList's, so a private MAL list imports the same as a public one. */
  completeMalConnect: (code: string) =>
    request<ConnectedAccount>('/integrations/mal/callback', {
      method: 'POST',
      body: JSON.stringify({ code }),
    }),

  simklAuthorizeUrl: () =>
    request<{ url: string }>('/integrations/simkl/authorize', { method: 'POST' }),

  /** Simkl keeps a status per title, so an imported shelf arrives as the reader arranged it. */
  completeSimklConnect: (code: string) =>
    request<ConnectedAccount>('/integrations/simkl/callback', {
      method: 'POST',
      body: JSON.stringify({ code }),
    }),

  completeSteamConnect: (params: Record<string, string>) =>
    request<ConnectedAccount>('/integrations/steam/callback', {
      method: 'POST',
      body: JSON.stringify({ params }),
    }),

  /** Answers immediately with a job to watch: an import is minutes of background work. */
  importLibrary: (provider: Provider) =>
    request<SyncJob>(`/integrations/${provider}/import`, { method: 'POST' }),

  /**
   * The same import from an exported file rather than a connected account. Answers with a
   * job to watch, exactly as the account import does.
   */
  importCsv: (provider: Provider, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return request<SyncJob>(`/integrations/${provider}/import/csv`, { method: 'POST', body })
  },

  /**
   * One shelf as a file. Read as a response rather than parsed: the CSV is the body, and
   * the name to save it under is a header the server sets.
   */
  exportCsv: async (mediaType: MediaType): Promise<ExportedCsv> => {
    const res = await send(`/exports/${mediaType}`)
    return {
      filename: filenameFrom(res.headers.get('Content-Disposition')) ?? `nexus-${mediaType.toLowerCase()}.csv`,
      blob: await res.blob(),
    }
  },

  /** Everything held about this reader, as a file — the same shape the export endpoint sends. */
  exportAccount: async (): Promise<ExportedCsv> => {
    const res = await send('/settings/account/export')
    return {
      filename:
        filenameFrom(res.headers.get('Content-Disposition')) ?? 'nexus-data.json',
      blob: await res.blob(),
    }
  },

  disconnect: (provider: Provider) =>
    request<void>(`/integrations/${provider}`, { method: 'DELETE' }),

  syncJob: (jobId: string) => request<SyncJob>(`/integrations/jobs/${jobId}`).then(trackJobOutage),

  /** Whatever this reader has running, for the indicator that follows them around. */
  currentJob: () => request<SyncJob | null>('/integrations/jobs/current').then(trackJobOutage),

  cancelJob: (jobId: string) =>
    request<SyncJob>(`/integrations/jobs/${jobId}`, { method: 'DELETE' }),

  activityFeed: (limit = 50) => request<ActivityEntry[]>(`/activity?limit=${limit}`),

  getReview: (entryId: number) => request<Review>(`/entries/${entryId}/review`),

  writeReview: (entryId: number, body: string, containsSpoilers: boolean) =>
    request<Review>(`/entries/${entryId}/review`, {
      method: 'PUT',
      body: JSON.stringify({ body, containsSpoilers }),
    }),

  deleteReview: (entryId: number) =>
    request<void>(`/entries/${entryId}/review`, { method: 'DELETE' }),

  restoreSession: async (): Promise<AuthResponse | null> => {
    const token = await refreshAccessToken()
    if (!token) return null
    const user = await request<User>('/auth/me')
    return { accessToken: token, user }
  },
}
