import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { MediaType } from '../api/client'
import type { RelatedTitle } from './detailView'

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
  'Source',
  'Adaptation',
  'Parent',
  'Prequel',
  'Sequel',
  'Alternative',
  'Side Story',
  'Spin Off',
  'Summary',
  'Compilation',
  'Contains',
  'DLC',
  'Expansion',
  'Character',
  'Related',
  'Other',
]

const rankOf = (relation: string): number => {
  const found = RELATION_ORDER.indexOf(relation)
  // Anything a source adds later sorts after what is named here rather than before it.
  return found === -1 ? RELATION_ORDER.length : found
}

/** By kind first, and oldest first inside it, so a run of side stories still reads in order. */
const ordered = (relations: RelatedTitle[]): RelatedTitle[] =>
  [...relations].sort((a, b) => {
    const kind = rankOf(a.relation) - rankOf(b.relation)
    if (kind !== 0) return kind
    if (!a.year) return b.year ? 1 : a.title.localeCompare(b.title)
    if (!b.year) return -1
    return Number(a.year) - Number(b.year) || a.title.localeCompare(b.title)
  })

export const MediaRelations = ({
  relations,
  source,
  mediaType,
}: {
  relations: RelatedTitle[]
  source: string
  mediaType: MediaType
}) => {
  const [showAll, setShowAll] = useState(false)
  const ranked = ordered(relations)
  if (ranked.length === 0) return null

  /*
   * A long-running series drags a dozen printed spin-offs behind it, and none of them are
   * what someone on the anime page came to find. Its own side is shown in full, and the
   * other side only where the two actually join — the book it came from, the series made
   * of it. The rest is a click away rather than gone.
   */
  const ownSide = mediaType === 'ANIME' ? 'ANIME' : 'MANGA'
  const belongs = (relation: RelatedTitle) =>
    !relation.type || relation.type === ownSide || CROSSING_RELATIONS.has(relation.relation)

  const shown = showAll ? ranked : ranked.filter(belongs)
  const hidden = ranked.length - ranked.filter(belongs).length

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
            {relation.cover ? (
              <img src={relation.cover} alt="" loading="lazy" />
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

/**
 * How many suggestions a row holds.
 *
 * <p>Seven, because that is what the screenshots above settle on — a source hands over
 * eight and the first becomes the page's banner — and two rows of different lengths under
 * one another is the asymmetry this panel is trying to avoid.
 */
const ROW_COUNT = 7

/**
 * Titles like this one rather than part of it.
 *
 * <p>Kept apart from relations deliberately. A sequel belongs to the same work and a
 * suggestion does not, and listing them together says The Witcher 3 is related to Skyrim.
 */
export const MediaRecommendations = ({
  recommendations,
  source,
}: {
  recommendations: RelatedTitle[]
  source: string
}) => {
  if (recommendations.length === 0) return null

  return (
    <section className="status-section">
      <h2>You might also like</h2>
      <div className="relation-grid one-row">
        {recommendations.slice(0, ROW_COUNT).map((title) => (
          <Link key={title.id} className="relation-card" to={`/media/${source}/${title.id}`}>
            {title.cover ? (
              <img src={title.cover} alt="" loading="lazy" />
            ) : (
              <div className="cover-placeholder" aria-hidden="true" />
            )}
            <span className="relation-title" title={title.title}>
              {title.title}
            </span>
          </Link>
        ))}
      </div>
    </section>
  )
}
