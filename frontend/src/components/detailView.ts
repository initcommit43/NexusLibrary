/**
 * One shape for a title's own page, whatever produced it.
 *
 * <p>Each source is stored as it was sent — nested edges from AniList, flat lists from IGDB —
 * because a cache that rewrites its answers cannot be checked against the source later. So
 * the reading happens here instead: one function per source, all of them ending at the same
 * view, and the panels never learn which source they are showing.
 *
 * <p>Every field is optional in effect: an empty list is how a panel is told it has nothing
 * to say, and it takes itself off the page.
 */
import {
  readCharacters,
  readLinks,
  readScoreDistribution,
  readStaff,
  readStatusDistribution,
  readTags,
  readTrailer,
  type CharacterRole,
  type Distribution,
  type ExternalLink,
  type MediaTag,
  type Person,
} from './mediaDetail'
import { readGameDetail } from './gameDetail'
import { readFilmDetail } from './filmDetail'

/** A title related to this one, from either side of the relation. */
export interface RelatedTitle {
  id: string
  title: string
  cover: string | null
  /** "Sequel", "DLC", "Similar" — the words are the source's, tidied. */
  relation: string
  /** ANIME or MANGA where a source distinguishes them; null where it does not. */
  type: string | null
  format: string | null
  year: string | null
}

/** A number worth stating plainly rather than charting: a rating, a count of reviews. */
export interface Score {
  label: string
  value: string
  hint: string | null
}

export interface MediaDetailView {
  banner: string | null
  trailer: string | null
  summaryExtra: string | null
  characters: CharacterRole[]
  /**
   * The people in front of the camera, kept apart from the ones behind it. A source with only
   * one kind of credit — a game's companies, an anime's studios — leaves this empty and says
   * everything through {@link staff}.
   */
  cast: Person[]
  staff: Person[]
  tags: MediaTag[]
  links: ExternalLink[]
  relations: RelatedTitle[]
  /**
   * Titles like this one, which is a different claim from being part of it. A sequel belongs
   * to the same work; a game that plays similarly is a suggestion, and putting the two in one
   * list says The Witcher 3 is related to Skyrim, which it is not.
   */
  recommendations: RelatedTitle[]
  gallery: string[]
  scores: Score[]
  statusDistribution: Distribution[]
  scoreDistribution: Distribution[]
}

export const emptyView: MediaDetailView = {
  banner: null,
  trailer: null,
  summaryExtra: null,
  characters: [],
  cast: [],
  staff: [],
  tags: [],
  links: [],
  relations: [],
  recommendations: [],
  gallery: [],
  scores: [],
  statusDistribution: [],
  scoreDistribution: [],
}

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() ? value : null

const record = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}

const array = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])

/** AniList nests relations as edges, each carrying the relation and the title it points at. */
const readAniListRelations = (detail: Record<string, unknown>): RelatedTitle[] =>
  array(record(detail.relations).edges).flatMap((raw) => {
    const edge = record(raw)
    const node = record(edge.node)
    if (!node.id) return []

    const titles = record(node.title)
    const title =
      text(titles.english) ?? text(titles.romaji) ?? text(titles.native) ?? String(node.id)

    return [
      {
        id: String(node.id),
        title,
        cover: text(record(node.coverImage).large),
        relation: label(edge.relationType),
        type: text(node.type),
        format: text(node.format),
        year: typeof record(node.startDate).year === 'number' ? String(record(node.startDate).year) : null,
      },
    ]
  })

/** "SIDE_STORY" reads as "Side Story"; the sources shout their enum names. */
export const label = (raw: unknown): string => {
  const value = text(raw)
  if (!value) return 'Related'
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

const readAniListView = (detail: Record<string, unknown>): MediaDetailView => ({
  ...emptyView,
  banner: text(detail.bannerImage),
  trailer: readTrailer(detail),
  characters: readCharacters(detail),
  staff: readStaff(detail),
  tags: readTags(detail),
  links: readLinks(detail),
  relations: readAniListRelations(detail),
  statusDistribution: readStatusDistribution(detail),
  scoreDistribution: readScoreDistribution(detail),
})

/**
 * Which reader a stored detail needs, by the source that wrote it.
 *
 * <p>A source with no reader yet returns the empty view rather than nothing, so its page is
 * the hero and the facts and no empty panels — which is what it showed before either way.
 */
export const readDetail = (source: string, detail: Record<string, unknown>): MediaDetailView => {
  if (Object.keys(detail).length === 0) return emptyView

  switch (source) {
    case 'ANILIST':
      return readAniListView(detail)
    case 'IGDB':
      return readGameDetail(detail)
    case 'TMDB':
      return readFilmDetail(detail)
    default:
      return emptyView
  }
}
