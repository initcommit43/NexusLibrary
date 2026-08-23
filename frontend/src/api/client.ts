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
}

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
  progressUnit: string | null
  progressExtra: Record<string, unknown> | null
  favorite: boolean
  notes: string | null
}

export type Provider = 'STEAM' | 'ANILIST' | 'MAL' | 'TRAKT'

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
  created: number
  updated: number
  unmatched: UnmatchedItem[]
}

export type ActivityType = 'ADDED' | 'STATUS_CHANGE' | 'PROGRESS' | 'RATED' | 'REVIEWED'

export type ActivityEntry = {
  id: number
  type: ActivityType
  mediaType: MediaType
  title: string
  coverUrl: string | null
  externalId: string
  payload: { from?: string | null; to?: string | null; unit?: string; status?: string }
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
  state: 'RUNNING' | 'COMPLETE' | 'FAILED'
  total: number
  processed: number
  changed: number
  message: string | null
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
  progressUnit: string
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

const request = async <T>(path: string, init: RequestInit = {}, allowRetry = true): Promise<T> => {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const res = await fetch(BASE + path, { ...init, headers, credentials: 'include' })

  if (res.status === 401 && allowRetry) {
    const renewed = await refreshAccessToken()
    if (renewed) return request<T>(path, init, false)
    accessToken = null
    onSessionLost?.()
  }

  if (!res.ok) throw await parseError(res)
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
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

  listEntries: () => request<TrackedItem[]>('/entries'),

  getEntry: (id: number) => request<TrackedItem>(`/entries/${id}`),

  createEntry: (payload: TrackPayload) =>
    request<TrackedItem>('/entries', { method: 'POST', body: JSON.stringify(payload) }),

  updateEntry: (id: number, payload: UpdateEntryPayload) =>
    request<TrackedItem>(`/entries/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),

  deleteEntry: (id: number) => request<void>(`/entries/${id}`, { method: 'DELETE' }),

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

  completeSteamConnect: (params: Record<string, string>) =>
    request<ConnectedAccount>('/integrations/steam/callback', {
      method: 'POST',
      body: JSON.stringify({ params }),
    }),

  importLibrary: (provider: Provider) =>
    request<ImportReport>(`/integrations/${provider}/import`, { method: 'POST' }),

  disconnect: (provider: Provider) =>
    request<void>(`/integrations/${provider}`, { method: 'DELETE' }),

  syncAchievements: () =>
    request<SyncJob>('/integrations/steam/achievements', { method: 'POST' }),

  syncJob: (jobId: string) => request<SyncJob>(`/integrations/jobs/${jobId}`),

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
