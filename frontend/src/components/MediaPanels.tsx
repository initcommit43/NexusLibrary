import { useState } from 'react'
import type { Distribution, ExternalLink, MediaTag, Person } from './mediaDetail'
import type { CharacterRole } from './mediaDetail'
import type { Score } from './detailView'

const PersonTile = ({ left, right }: { left: Person; right: Person | null }) => (
  <div className="person-card">
    {left.image ? <img src={left.image} alt="" loading="lazy" /> : <div className="person-blank" />}
    <div className="person-text">
      <strong>{left.name}</strong>
      {left.role && <span className="muted">{left.role}</span>}
    </div>

    {right && (
      <>
        <div className="person-text right">
          <strong>{right.name}</strong>
          {right.role && <span className="muted">{right.role}</span>}
        </div>
        {right.image ? (
          <img src={right.image} alt="" loading="lazy" />
        ) : (
          <div className="person-blank" />
        )}
      </>
    )}
  </div>
)

/** A character beside the actor who voices them, which is how both are usually recalled. */
export const MediaCharacters = ({ characters }: { characters: CharacterRole[] }) => {
  if (characters.length === 0) return null

  return (
    <section className="status-section">
      <h2>Characters</h2>
      <div className="person-grid">
        {characters.map(({ character, voiceActor }) => (
          <PersonTile key={character.id} left={character} right={voiceActor} />
        ))}
      </div>
    </section>
  )
}

/** Named for what the medium has: an anime has staff, a game has the companies behind it. */
export const MediaStaff = ({ staff, title }: { staff: Person[]; title: string }) => {
  if (staff.length === 0) return null

  return (
    <section className="status-section">
      <h2>{title}</h2>
      <div className="person-grid">
        {staff.map((member) => (
          <PersonTile key={`${member.id}-${member.role}`} left={member} right={null} />
        ))}
      </div>
    </section>
  )
}

const Bars = ({ rows, title }: { rows: Distribution[]; title: string }) => {
  if (rows.length === 0) return null
  const peak = Math.max(...rows.map((row) => row.amount))

  return (
    <section className="status-section">
      <h2>{title}</h2>
      <div className="bar-chart">
        {rows.map((row) => (
          <div key={row.label} className="bar">
            <span className="bar-value">{row.amount.toLocaleString()}</span>
            <div className="bar-fill" style={{ height: `${Math.round((row.amount / peak) * 100)}%` }} />
            <span className="bar-label muted">{row.label}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

export const MediaStats = ({
  statuses,
  scores,
}: {
  statuses: Distribution[]
  scores: Distribution[]
}) => (
  <>
    <Bars rows={statuses} title="Status distribution" />
    <Bars rows={scores} title="Score distribution" />
  </>
)

/**
 * Ratings stated rather than charted. A source that reports one number and how many people
 * gave it has nothing to draw, and a two-bar chart of that says less than the two numbers do.
 */
export const MediaScores = ({ scores }: { scores: Score[] }) => {
  if (scores.length === 0) return null

  return (
    <section className="status-section">
      <h2>Ratings</h2>
      <ul className="score-list">
        {scores.map((score) => (
          <li key={score.label}>
            <b>{score.value}</b>
            <span>{score.label}</span>
            {score.hint && <span className="muted">{score.hint}</span>}
          </li>
        ))}
      </ul>
    </section>
  )
}

/** Screenshots, for the sources that have them. A game shows itself better than it reads. */
export const MediaGallery = ({ images }: { images: string[] }) => {
  if (images.length === 0) return null

  return (
    <section className="status-section">
      <h2>Screenshots</h2>
      <div className="gallery">
        {images.map((image) => (
          <img key={image} src={image} alt="" loading="lazy" />
        ))}
      </div>
    </section>
  )
}

export const MediaTrailer = ({ trailer }: { trailer: string | null }) => {
  if (!trailer) return null

  return (
    <section className="status-section">
      <h2>Trailer</h2>
      <div className="trailer">
        <iframe src={trailer} title="Trailer" allowFullScreen loading="lazy" />
      </div>
    </section>
  )
}

/**
 * Tags carry how strongly the community thinks each applies, and some of them give the plot
 * away — those stay hidden until asked for, the way the source flags them.
 */
export const MediaTags = ({ tags }: { tags: MediaTag[] }) => {
  const [showSpoilers, setShowSpoilers] = useState(false)
  if (tags.length === 0) return null

  const spoilers = tags.filter((tag) => tag.spoiler)
  const shown = showSpoilers ? tags : tags.filter((tag) => !tag.spoiler)

  return (
    <section className="tag-list">
      <h2>Tags</h2>
      {shown.map((tag) => (
        <div key={tag.name} className="tag-row">
          <span>{tag.name}</span>
          {tag.rank > 0 && <span className="muted">{tag.rank}%</span>}
        </div>
      ))}

      {spoilers.length > 0 && !showSpoilers && (
        <button type="button" className="ghost small" onClick={() => setShowSpoilers(true)}>
          Show {spoilers.length} spoiler {spoilers.length === 1 ? 'tag' : 'tags'}
        </button>
      )}
    </section>
  )
}

export const MediaLinks = ({ links }: { links: ExternalLink[] }) => {
  if (links.length === 0) return null

  return (
    <section className="tag-list">
      <h2>External links</h2>
      {links.map((link) => (
        <a key={link.url} href={link.url} target="_blank" rel="noreferrer noopener">
          {link.site}
          {link.language && <span className="muted"> {link.language}</span>}
        </a>
      ))}
    </section>
  )
}
