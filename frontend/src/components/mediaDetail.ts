/**
 * Readers for AniList's detail payload.
 *
 * <p>It is stored exactly as the source sent it — nested edges, shouted enum names — so the
 * shapes are read here rather than being normalised on the way in. That keeps the cache a
 * faithful copy of what AniList said, and leaves this the only place that has to know how
 * AniList says it.
 */

export interface Person {
  id: string
  name: string
  image: string | null
  role: string | null
  /** A paragraph about them, where the source keeps one. Only the author card shows it. */
  bio?: string | null
}

export interface CharacterRole {
  character: Person
  voiceActor: Person | null
}

export interface MediaTag {
  name: string
  rank: number
  spoiler: boolean
}

export interface Distribution {
  label: string
  amount: number
}

export interface ExternalLink {
  site: string
  url: string
  language: string | null
}

const record = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}

const array = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() ? value : null

/** "MAIN" reads as "Main", "SERIES_COMPOSITION" as "Series composition". */
export const humanise = (raw: unknown): string | null => {
  const value = text(raw)
  if (!value) return null
  const words = value.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

const person = (node: unknown, role: unknown): Person | null => {
  const found = record(node)
  if (!found.id) return null
  return {
    id: String(found.id),
    name: text(record(found.name).full) ?? String(found.id),
    image: text(record(found.image).medium),
    role: humanise(role),
  }
}

export const readCharacters = (detail: Record<string, unknown>): CharacterRole[] =>
  array(record(detail.characters).edges).flatMap((raw) => {
    const edge = record(raw)
    const character = person(edge.node, edge.role)
    if (!character) return []
    // Japanese voice actors only were requested, so at most one comes back.
    const voice = array(edge.voiceActors)[0]
    return [{ character, voiceActor: voice ? person(voice, 'Japanese') : null }]
  })

export const readStaff = (detail: Record<string, unknown>): Person[] =>
  array(record(detail.staff).edges).flatMap((raw) => {
    const edge = record(raw)
    const member = person(edge.node, edge.role)
    return member ? [member] : []
  })

export const readTags = (detail: Record<string, unknown>): MediaTag[] =>
  array(detail.tags).flatMap((raw) => {
    const tag = record(raw)
    const name = text(tag.name)
    if (!name) return []
    return [
      {
        name,
        rank: typeof tag.rank === 'number' ? tag.rank : 0,
        spoiler: tag.isMediaSpoiler === true,
      },
    ]
  })

export const readStatusDistribution = (detail: Record<string, unknown>): Distribution[] =>
  array(record(detail.stats).statusDistribution).flatMap((raw) => {
    const row = record(raw)
    const label = humanise(row.status)
    if (!label || typeof row.amount !== 'number') return []
    return [{ label, amount: row.amount }]
  })

export const readScoreDistribution = (detail: Record<string, unknown>): Distribution[] =>
  array(record(detail.stats).scoreDistribution).flatMap((raw) => {
    const row = record(raw)
    if (typeof row.score !== 'number' || typeof row.amount !== 'number') return []
    return [{ label: String(row.score), amount: row.amount }]
  })

export const readLinks = (detail: Record<string, unknown>): ExternalLink[] =>
  array(detail.externalLinks).flatMap((raw) => {
    const link = record(raw)
    const url = text(link.url)
    const site = text(link.site)
    if (!url || !site) return []
    return [{ site, url, language: text(link.language) }]
  })

/** "#70 Most Popular 2017" — AniList assembles these from parts. */
export const readRankings = (detail: Record<string, unknown>): string[] =>
  array(detail.rankings).flatMap((raw) => {
    const ranking = record(raw)
    const context = text(ranking.context)
    if (typeof ranking.rank !== 'number' || !context) return []
    const when = ranking.allTime === true ? '' : [text(ranking.season), ranking.year].filter(Boolean).join(' ')
    return [`#${ranking.rank} ${context}${when ? ` ${when}` : ''}`]
  })

export interface NextEpisode {
  episode: number
  airingAt: number
}

/**
 * When the next episode lands, as an absolute time.
 *
 * <p>AniList also offers a countdown, but a countdown cached for a day is wrong by a day —
 * the timestamp is what survives being stored, and the counting happens on screen.
 */
export const readNextEpisode = (detail: Record<string, unknown>): NextEpisode | null => {
  const next = record(detail.nextAiringEpisode)
  if (typeof next.episode !== 'number' || typeof next.airingAt !== 'number') return null
  return { episode: next.episode, airingAt: next.airingAt }
}

/** "3d 4h", "12h 30m", "8m" — enough to know when, without pretending to a precision. */
export const countdown = (airingAt: number, now: number = Date.now()): string | null => {
  const seconds = airingAt - Math.floor(now / 1000)
  if (seconds <= 0) return null

  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)

  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${minutes}m`
  return `${minutes}m`
}

export const readTrailer = (detail: Record<string, unknown>): string | null => {
  const trailer = record(detail.trailer)
  const id = text(trailer.id)
  if (!id) return null
  // Only YouTube gets an embed; anything else is left alone rather than guessed at.
  return trailer.site === 'youtube' ? `https://www.youtube.com/embed/${id}` : null
}
