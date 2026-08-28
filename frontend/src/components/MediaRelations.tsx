import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { MediaType } from '../api/client'

/**
 * The relation types that join the two sides of a work: the book a series was adapted from,
 * and the series made out of it. Everything else — spin-offs, side stories, alternatives —
 * belongs to whichever side it was made for.
 */
const CROSSING_RELATIONS = new Set(['Source', 'Adaptation'])

/**
 * The order a series reads in: what it came from, then what came before and after, then the
 * things made alongside it, and last the ones that merely share a face.
 *
 * <p>This is what makes a crossover legible. A long-running series is related to titles it
 * has nothing to do with — One Piece lists Dragon Ball Z, because the two share a special —
 * and sorted by date that lands among the sequels looking like a mistake. Sorted by kind, it
 * sits under Character with everything else that is one.
 */
const RELATION_ORDER = [
  'SOURCE',
  'ADAPTATION',
  'PARENT',
  'PREQUEL',
  'SEQUEL',
  'ALTERNATIVE',
  'SIDE_STORY',
  'SPIN_OFF',
  'SUMMARY',
  'COMPILATION',
  'CONTAINS',
  'CHARACTER',
  'OTHER',
]

const rankOf = (raw: unknown): number => {
  const found = typeof raw === 'string' ? RELATION_ORDER.indexOf(raw) : -1
  // Anything AniList adds later sorts after what is named here rather than before it.
  return found === -1 ? RELATION_ORDER.length : found
}

/** "SIDE_STORY" reads as "Side Story"; AniList's own labels are shouted constants. */
const relationLabel = (raw: unknown): string => {
  if (typeof raw !== 'string' || !raw) return 'Related'
  return raw
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

interface RelatedMedia {
  id: string
  title: string
  coverUrl: string | null
  relation: string
  rank: number
  /** ANIME or MANGA — AniList files light novels under MANGA, by format. */
  type: string | null
  format: string | null
  year: string | null
}

const readTitle = (node: Record<string, unknown>): string => {
  const title = node.title as Record<string, unknown> | undefined
  for (const key of ['english', 'romaji', 'native']) {
    const value = title?.[key]
    if (typeof value === 'string' && value.trim()) return value
  }
  return String(node.id ?? 'Unknown')
}

/** AniList nests relations as edges; anything without a usable node is dropped. */
export const readRelations = (detail: Record<string, unknown>): RelatedMedia[] => {
  const relations = detail.relations as { edges?: unknown } | undefined
  const edges = Array.isArray(relations?.edges) ? relations.edges : []

  const related = edges.flatMap((raw) => {
    if (typeof raw !== 'object' || raw === null) return []
    const edge = raw as Record<string, unknown>
    const node = edge.node as Record<string, unknown> | undefined
    if (!node?.id) return []

    const cover = node.coverImage as Record<string, unknown> | undefined
    const start = node.startDate as Record<string, unknown> | undefined

    return [
      {
        id: String(node.id),
        title: readTitle(node),
        coverUrl: typeof cover?.large === 'string' ? cover.large : null,
        relation: relationLabel(edge.relationType),
        rank: rankOf(edge.relationType),
        type: typeof node.type === 'string' ? node.type : null,
        format: typeof node.format === 'string' ? node.format : null,
        year: typeof start?.year === 'number' ? String(start.year) : null,
      },
    ]
  })

  // By kind first, and oldest first inside it: a run of side stories still reads in the
  // order it came out. Anything undated sorts last within its kind rather than leading it.
  return related.sort((a, b) => {
    if (a.rank !== b.rank) return a.rank - b.rank
    if (!a.year) return b.year ? 1 : a.title.localeCompare(b.title)
    if (!b.year) return -1
    return Number(a.year) - Number(b.year) || a.title.localeCompare(b.title)
  })
}

/**
 * Sequels, side stories and adaptations, each linking to its own page whether or not it is
 * on a shelf — following a series through its parts is the whole point of the section.
 */
export const MediaRelations = ({
  detail,
  source,
  mediaType,
}: {
  detail: Record<string, unknown>
  source: string
  mediaType: MediaType
}) => {
  const [showAll, setShowAll] = useState(false)
  const relations = readRelations(detail)
  if (relations.length === 0) return null

  /*
   * A long-running series drags a dozen printed spin-offs behind it, and none of them are
   * what someone on the anime page came to find. Its own side is shown in full, and the
   * other side only where the two actually join — the book it came from, the series made
   * of it. The rest is a click away rather than gone.
   */
  const ownSide = mediaType === 'ANIME' ? 'ANIME' : 'MANGA'
  const belongs = (relation: RelatedMedia) =>
    !relation.type || relation.type === ownSide || CROSSING_RELATIONS.has(relation.relation)

  const shown = showAll ? relations : relations.filter(belongs)
  const hidden = relations.length - relations.filter(belongs).length

  return (
    <section className="status-section">
      <h2>
        Relations
        {hidden > 0 && (
          <button type="button" className="ghost small section-action" onClick={() => setShowAll((v) => !v)}>
            {showAll ? 'Show fewer' : `Show ${hidden} more`}
          </button>
        )}
      </h2>
      <div className="relation-grid">
        {shown.map((relation) => (
          <Link
            key={`${relation.relation}-${relation.id}`}
            className="relation-card"
            to={`/media/${source}/${relation.id}`}
          >
            {relation.coverUrl ? (
              <img src={relation.coverUrl} alt="" loading="lazy" />
            ) : (
              <div className="cover-placeholder" aria-hidden="true" />
            )}
            <span className="relation-type">{relation.relation}</span>
            <span className="relation-title" title={relation.title}>
              {relation.title}
            </span>
            <span className="muted">
              {[relation.format, relation.year].filter(Boolean).join(' · ')}
            </span>
          </Link>
        ))}
      </div>
    </section>
  )
}
