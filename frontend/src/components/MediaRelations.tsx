import { Link } from 'react-router-dom'

/** "SIDE_STORY" reads as "Side story"; AniList's own labels are shouted constants. */
const relationLabel = (raw: unknown): string => {
  if (typeof raw !== 'string' || !raw) return 'Related'
  const words = raw.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

interface RelatedMedia {
  id: string
  title: string
  coverUrl: string | null
  relation: string
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
        format: typeof node.format === 'string' ? node.format : null,
        year: typeof start?.year === 'number' ? String(start.year) : null,
      },
    ]
  })

  // Oldest first: a series reads in the order it came out, and what a thing was adapted
  // from necessarily predates it. Anything undated sorts last rather than leading.
  return related.sort((a, b) => {
    if (!a.year) return b.year ? 1 : a.title.localeCompare(b.title)
    if (!b.year) return -1
    return Number(a.year) - Number(b.year) || a.title.localeCompare(b.title)
  })
}

/**
 * Sequels, side stories and adaptations, each linking to its own page whether or not it is
 * on a shelf — following a series through its parts is the whole point of the section.
 */
export const MediaRelations = ({ detail, source }: { detail: Record<string, unknown>; source: string }) => {
  const relations = readRelations(detail)
  if (relations.length === 0) return null

  return (
    <section className="status-section">
      <h2>Relations</h2>
      <div className="relation-grid">
        {relations.map((relation) => (
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
