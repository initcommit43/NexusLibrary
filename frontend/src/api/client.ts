import { reportOutage } from './outages'

// Versioned because the web app is no longer the only client: it ships with the backend and
// always speaks the current version, but an installed phone build keeps whatever it shipped
// with. Bumping this means bumping ApiPaths.VERSION on the backend to match.
const BASE = '/api/v1'

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
  /** Whether the source files this as 18+. What the blur setting applies to. */
  adult: boolean
  /** What a ranked shelf shows beside the title. Empty for a plain search hit. */
  facets?: Record<string, unknown>
}

/** One row of a module's browse page. Which rows exist is the backend adapter's decision. */
export type BrowseShelf = {
  id: string
  label: string
  /** Whether the module wants this row on its home page as well as on browse. */
  onHome: boolean
  /** Whether the row belongs on browse; a home row is not automatically a browse category. */
  onBrowse: boolean
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
  adult: boolean
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
  /** When the entry last changed here — an edit, or an import that moved something. */
  updatedAt: string
  /**
   * When anything last happened to this title — an episode logged on the service it came
   * from, a change made here. Null for a title nothing has happened to yet.
   *
   * <p>Distinct from `updatedAt`, which is when the row was last written: an import writes a
   * whole library at one moment and says nothing about when the reader was last at any of it.
   */
  lastActivityAt: string | null
}

/**
 * How a reader arranged their favourite rows: the order, and which of them share a band
 * with the row before them.
 */
export type RowArrangement = { order: MediaType[]; paired: MediaType[] }

/** One day of a reader's history: how many titles they started or finished on it. */
export type ActivityDay = { date: string; amount: number }

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

/** The character standing for a reader, the title they are from, and how they are framed. */
export type ProfilePicture = {
  imageUrl: string
  characterName: string
  title: string
  mediaType: MediaType
  source: string
  externalId: string
  focusX: number
  focusY: number
  zoom: number
}

/**
 * Where an image sits inside the frame showing it, which is not a change of which image it is.
 * A strip and a circle crop the same way, so the banner and the picture share this.
 */
export type Framing = { focusX: number; focusY: number; zoom: number }

/** What this reader sees of 18+ titles, with the age question already answered by the server. */
export type ContentPreferences = {
  /** Whether 18+ titles appear at all. Always false on an account not old enough for them. */
  showAdult: boolean
  /** Whether their covers are drawn behind a blur until hovered. */
  blurAdult: boolean
  /** Whether this account may turn `showAdult` on. */
  adultAllowed: boolean
  /** False for an account made before registration asked, which may add it once. */
  dateOfBirthSet: boolean
}

export type MediaDetail = {
  mediaType: MediaType
  source: string
  externalId: string
  title: string
  coverUrl: string | null
  releaseDate: string | null
  itemState: string
  adult: boolean
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
  /** Something a provider recorded rather than this app — an episode watched on AniList. */
  | 'EXTERNAL'

/** One title a run touched, as the row's hover card reads it. */
export type ActivityChange = { title: string; from: string | null; to: string }

/**
 * Something that happened, to a title or to a whole run.
 *
 * <p>A run belongs to no title, so it answers with nulls where an edit answers with a cover
 * and a name, and says what it did in its payload instead.
 */
export type ActivityEntry = {
  /** Prefixed by which half of the feed it came from: this app's own, or an import's. */
  id: string
  type: ActivityType
  mediaType: MediaType | null
  title: string | null
  coverUrl: string | null
  /** Whether the blur setting applies to this cover. False for a row about a run. */
  adult: boolean
  /** With the id beside it, this is the way from a row in the feed to the title's own page. */
  source: string | null
  externalId: string | null
  payload: {
    from?: string | null
    to?: string | null
    unit?: string
    status?: string
    /** How far an imported event got, in the provider's own words: "5", or "5 - 7". */
    progress?: string | null
    provider?: Provider
    added?: number
    advanced?: number
    titles?: ActivityChange[]
  }
  createdAt: string
}

/** What happened to a title while the reader was away. */
export type NotificationEntry = {
  id: number
  type: 'EPISODE_AIRED' | 'TITLE_ADDED' | 'RELEASE_STARTED'
  mediaType: MediaType
  title: string
  coverUrl: string | null
  adult: boolean
  source: string
  externalId: string
  payload: { episode?: number; season?: number; title?: string }
  /** Whether it has been seen. The one thing that makes a row look new. */
  read: boolean
  createdAt: string
}

/**
 * What every notification call asks with.
 *
 * <p>Written once because the three of them have to agree: reading one answers with the panel
 * around it, and a read that asked a different question than the load did would hand back a
 * different list than the reader is looking at.
 */
const waitingQuery = (limit: number, mediaTypes: MediaType[]) =>
  `limit=${limit}${mediaTypes.length === 0 ? '' : `&mediaTypes=${mediaTypes.join(',')}`}`

/** What is waiting, and how much of it is new. */
export type Waiting = {
  items: NotificationEntry[]
  unread: number
}

/** A studio, and one page of what it made. */
export type StudioWorks = {
  name: string | null
  items: SearchResult[]
  hasMore: boolean
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
  kind: 'IMPORT' | 'ACHIEVEMENTS' | 'ACTIVITY' | 'NOTIFICATIONS' | 'ACTIVITY'
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

/**
 * What to show a reader when a call failed.
 *
 * <p>The server writes a message for every failure it knows about, and that message is always
 * better than a generic one — it says which title, which column, which service. Anything that
 * is not an {@link ApiError} came from the network stack or from a bug, neither of which has
 * anything a reader can act on, so those get the caller's own words instead.
 */
export const errorMessage = (err: unknown, fallback: string): string =>
  err instanceof ApiError ? err.message : fallback

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

/** What the server's site gate answers the API with while the site password is unmet. */
const SITE_LOCKED = 'This site is locked.'

const isSiteLocked = (res: Response): Promise<boolean> =>
  res
    .clone()
    .json()
    .then((body) => body?.message === SITE_LOCKED)
    .catch(() => false)

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

  // The site password ran out under an open tab. No token fixes that, so go to the password
  // page and come back here, rather than refreshing into a signed-out app that cannot load.
  if (res.status === 401 && (await isSiteLocked(res))) {
    window.location.assign(
      `/site-gate?next=${encodeURIComponent(window.location.pathname + window.location.search)}`,
    )
    throw new ApiError(401, SITE_LOCKED)
  }

  if (res.status === 401 && allowRetry) {
    const renewed = await refreshAccessToken()
    if (renewed) return send(path, init, false)
    accessToken = null
    onSessionLost?.()
  }

  if (!res.ok) throw await parseError(res)
  return res
}

export type PublicConfig = {
  /** Empty when the challenge is switched off, which a local run without keys is. */
  turnstileSiteKey: string
}

let publicConfig: Promise<PublicConfig> | null = null

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
 *
 * <p>A run that finished is also the one way the library changes without this client asking
 * for it: an import writes entries server-side long after the call that started it returned.
 * Noticing that here is what stops a held copy outliving the titles it is missing.
 */
const trackJobOutage = <T extends SyncJob | null>(job: T): T => {
  if (job?.state === 'FAILED' && job.unavailableService) reportOutage(job.unavailableService)
  if (job?.state === 'COMPLETE' && job.changed > 0) entries = null
  return job
}

/*
 * What has already been fetched, so arriving somewhere a second time is instant.
 *
 * Every page keeps its data in its own state and React Router throws that away on the way
 * out, so home and the library — which ask for the same entries — were each refetching the
 * whole library on every visit, and a there-and-back paid for it three times.
 *
 * The promise is cached rather than the value: two pages mounting at once then share one
 * request instead of racing. A rejected one is dropped so a failure is retried rather than
 * remembered, and anything that changes what the answer would be clears it below.
 */
let entries: Promise<TrackedItem[]> | null = null
const shelves = new Map<MediaType, Promise<BrowseShelf[]>>()
const filterBars = new Map<MediaType, Promise<FilterField[]>>()

/**
 * Everything remembered about who is signed in.
 *
 * <p>Called on every entry into and out of a session. These responses are scoped to one
 * account, so carrying them across a sign-out would show the next reader the last one's
 * library — the one thing this app is not allowed to do.
 */
const forgetSession = () => {
  entries = null
  shelves.clear()
  filterBars.clear()
}

/** Their library changed, so the copy of it is no longer the answer. */
const forgetEntries = <T>(value: T): T => {
  entries = null
  return value
}

export type ExportedCsv = { filename: string; blob: Blob }

/** The filename out of a Content-Disposition, quoted or bare. */
const filenameFrom = (header: string | null): string | null => {
  const match = header?.match(/filename="?([^";]+)"?/i)
  return match ? match[1] : null
}

/**
 * Which client this is, on every request that opens a session.
 *
 * The server requires it rather than defaulting: a caller that says nothing about itself
 * would have to be guessed at, and guessing browser hands a native client a session with no
 * refresh token in it - working until its access token expires, then gone.
 */
const WEB_CLIENT = 'WEB'

export const api = {
  /**
   * @param acceptedTerms whether the reader ticked the box. Travels rather than being
   *   assumed server-side: which version they accepted is recorded against the account, and
   *   an account created without it is one nobody can show consent for.
   */
  /**
   * The settings a signed-out browser needs. Asked for once per page load and then shared:
   * the sign-up form and the widget inside it both want the same answer.
   */
  publicConfig: () => (publicConfig ??= request<PublicConfig>('/config')),

  register: (payload: {
    email: string
    username: string
    password: string
    /** ISO yyyy-mm-dd, straight off the date input. */
    dateOfBirth: string
    acceptedTerms: boolean
    turnstileToken: string
  }) =>
    request<AuthResponse | null>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ ...payload, client: WEB_CLIENT }),
    }).then((auth) => {
      forgetSession()
      return auth
    }),

  /** Follows a confirmation link. The token travels in the body, not the address bar. */
  verifyEmail: (token: string) =>
    request<void>('/auth/verify-email', { method: 'POST', body: JSON.stringify({ token }) }),

  /**
   * Asks for another confirmation link. Answers the same whether or not the address has an
   * account, so there is nothing here to show the reader either way.
   */
  resendVerification: (email: string) =>
    request<void>('/auth/verify-email/resend', { method: 'POST', body: JSON.stringify({ email }) }),

  /** `login` is a username or an email address; the server works out which. */
  login: (payload: { login: string; password: string }) =>
    request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ ...payload, client: WEB_CLIENT }),
    }).then((auth) => {
      forgetSession()
      return auth
    }),

  /*
   * Cleared whether or not the server answers. A sign-out that failed on the network still
   * ends the session in this tab, and the held library must not outlive it.
   */
  logout: () =>
    request<void>('/auth/logout', { method: 'POST' }).finally(forgetSession),

  /**
   * Asks for a reset link. Answers the same whether or not the address has an account, so
   * there is nothing here to tell the caller which it was — and nothing to show the reader.
   */
  requestPasswordReset: (email: string, turnstileToken: string) =>
    request<void>('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email, turnstileToken }),
    }),

  /**
   * Spends a mailed link. No session comes back: the reset ends every one the account had,
   * including whichever this browser was holding, and signing in with the new password is
   * what starts the next.
   */
  resetPassword: (token: string, password: string) =>
    request<void>('/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, password }) }),

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
      body: JSON.stringify({ currentPassword, newPassword, client: WEB_CLIENT }),
    }),

  // The account is gone; nothing fetched under it may survive in this tab.
  deleteAccount: (password: string) =>
    request<void>('/settings/account', {
      method: 'DELETE',
      body: JSON.stringify({ password }),
    }).finally(forgetSession),

  /** The media types this reader switched off. Everything absent from it is on. */
  disabledModules: () =>
    request<{ disabled: MediaType[] }>('/settings/modules').then((body) => body.disabled),

  setDisabledModules: (disabled: MediaType[]) =>
    request<{ disabled: MediaType[] }>('/settings/modules', {
      method: 'PUT',
      body: JSON.stringify({ disabled }),
    }).then((body) => body.disabled),

  contentPreferences: () => request<ContentPreferences>('/settings/content'),

  /** Changes the switches the change names and leaves the other alone. */
  setContentPreferences: (change: { showAdult?: boolean; blurAdult?: boolean }) =>
    request<ContentPreferences>('/settings/content', {
      method: 'PATCH',
      body: JSON.stringify(change),
    }),

  /** For an account made before registration asked. Set once; the server refuses a second. */
  setDateOfBirth: (dateOfBirth: string) =>
    request<ContentPreferences>('/settings/account/date-of-birth', {
      method: 'PUT',
      body: JSON.stringify({ dateOfBirth }),
    }),

  /*
   * Held per media type: home asks for one set per type in the module, so the anime module
   * alone was two requests every time it was opened. A shelf is the same list for everyone
   * and the server already caches it globally — this stops the browser asking again for an
   * answer it is holding.
   */
  browseShelves: (mediaType: MediaType) => {
    const held = shelves.get(mediaType)
    if (held) return held

    const pending = request<BrowseShelf[]>(`/catalog/shelves?mediaType=${mediaType}`).catch(
      (err) => {
        shelves.delete(mediaType)
        throw err
      },
    )
    shelves.set(mediaType, pending)
    return pending
  },

  browse: (mediaType: MediaType, shelf: string, page = 1) =>
    request<BrowseResults>(
      `/catalog/browse?mediaType=${mediaType}&shelf=${encodeURIComponent(shelf)}&page=${page}`,
    ),

  // Held per media type for the same reason as the shelves: switching type or module and back
  // asked again for option lists that are the same for everyone and do not change in a session.
  browseFilters: (mediaType: MediaType) => {
    const held = filterBars.get(mediaType)
    if (held) return held

    const pending = request<FilterField[]>(`/catalog/filters?mediaType=${mediaType}`).catch(
      (err) => {
        filterBars.delete(mediaType)
        throw err
      },
    )
    filterBars.set(mediaType, pending)
    return pending
  },

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

  listEntries: () =>
    (entries ??= request<TrackedItem[]>('/entries').catch((err) => {
      entries = null
      throw err
    })),

  createEntry: (payload: TrackPayload) =>
    request<TrackedItem>('/entries', { method: 'POST', body: JSON.stringify(payload) }).then(
      forgetEntries,
    ),

  updateEntry: (id: number, payload: UpdateEntryPayload) =>
    request<TrackedItem>(`/entries/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    }).then(forgetEntries),

  deleteEntry: (id: number) =>
    request<void>(`/entries/${id}`, { method: 'DELETE' }).then(forgetEntries),

  /**
   * Empties whole shelves. The mediums are always named — the server refuses an empty list
   * rather than reading it as "all", so nothing here may quietly default to everything.
   */
  clearLibrary: (mediaTypes: MediaType[]) =>
    request<{ entries: number; activity: number }>('/entries/clear', {
      method: 'POST',
      body: JSON.stringify({ mediaTypes }),
    }).then(forgetEntries),

  /** The whole arrangement, first to last, rather than one card's new position. */
  reorderFavourites: (entryIds: number[]) =>
    request<TrackedItem[]>('/entries/favourites/order', {
      method: 'PUT',
      body: JSON.stringify({ entryIds }),
    }).then(forgetEntries),

  favouriteRowOrder: () => request<RowArrangement>('/settings/favourite-rows'),

  /** Every row the reader has placed, in order; the rest keep the app's own. */
  replaceFavouriteRowOrder: (order: MediaType[], paired: MediaType[] = []) =>
    request<RowArrangement>('/settings/favourite-rows', {
      method: 'PUT',
      body: JSON.stringify({ order, paired }),
    }),

  /** Null when the reader has chosen none, which is a bare profile head rather than an error. */
  /** Only the days that saw something; the map draws its own blanks. */
  /** Days that saw anything; no media types asked for means every one that keeps dates. */
  activityHistory: (weeks: number, mediaTypes: MediaType[] = []) =>
    request<ActivityDay[]>(
      `/activity/history?weeks=${weeks}${
        mediaTypes.length === 0 ? '' : `&mediaTypes=${mediaTypes.join(',')}`
      }`,
    ),

  /** Forgets one event. The entry it was about is untouched: the shelf is the state. */
  forgetActivity: (activityId: string) =>
    request<void>(`/activity/${activityId}`, { method: 'DELETE' }),

  /** What one studio made, newest first, a page at a time. */
  studioWorks: (source: string, studioId: string, page = 1) =>
    request<StudioWorks>(
      `/catalog/studios/${source}/${encodeURIComponent(studioId)}?page=${page}`,
    ),

  /** What is waiting, for one module's media types; no types asked for means every one. */
  notifications: (limit = 50, mediaTypes: MediaType[] = []) =>
    request<Waiting>(`/notifications?${waitingQuery(limit, mediaTypes)}`),

  /** Says the reader has seen one; answers with the panel, whose count has changed with it. */
  readNotification: (id: number, limit = 50, mediaTypes: MediaType[] = []) =>
    request<Waiting>(`/notifications/${id}/read?${waitingQuery(limit, mediaTypes)}`, {
      method: 'POST',
    }),

  /** Says the reader has seen the lot; answers with what is left, which is nothing new. */
  readAllNotifications: (limit = 50, mediaTypes: MediaType[] = []) =>
    request<Waiting>(`/notifications/read?${waitingQuery(limit, mediaTypes)}`, { method: 'POST' }),

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

  frameProfileBanner: (framing: Framing) =>
    request<ProfileBanner>('/settings/profile-banner', {
      method: 'PATCH',
      body: JSON.stringify(framing),
    }),

  clearProfileBanner: () => request<void>('/settings/profile-banner', { method: 'DELETE' }),

  profilePicture: () => request<ProfilePicture | null>('/settings/profile-picture'),

  /**
   * Takes the entry and a character within it, never an image: the server reads the portrait
   * out of that title's detail, so a profile can only ever wear a face from the reader's own
   * library. Only AniList titles carry characters at all.
   */
  chooseProfilePicture: (entryId: number, characterId: string) =>
    request<ProfilePicture>('/settings/profile-picture', {
      method: 'PUT',
      body: JSON.stringify({ entryId, characterId }),
    }),

  frameProfilePicture: (framing: Framing) =>
    request<ProfilePicture>('/settings/profile-picture', {
      method: 'PATCH',
      body: JSON.stringify(framing),
    }),

  clearProfilePicture: () => request<void>('/settings/profile-picture', { method: 'DELETE' }),

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

  /**
   * A reader's AniList history: what they did, and what happened to what they keep. Answers
   * immediately with a job to watch, because a history walk is minutes of background work.
   */
  importAniListActivity: () =>
    request<SyncJob>('/integrations/anilist/activity', { method: 'POST' }),

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
