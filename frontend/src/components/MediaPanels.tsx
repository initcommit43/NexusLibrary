import { useState } from 'react'
import {
  readCharacters,
  readLinks,
  readScoreDistribution,
  readStaff,
  readStatusDistribution,
  readTags,
  readTrailer,
  type Distribution,
  type Person,
} from './mediaDetail'

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
export const MediaCharacters = ({ detail }: { detail: Record<string, unknown> }) => {
  const characters = readCharacters(detail)
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

export const MediaStaff = ({ detail }: { detail: Record<string, unknown> }) => {
  const staff = readStaff(detail)
  if (staff.length === 0) return null

  return (
    <section className="status-section">
      <h2>Staff</h2>
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

export const MediaStats = ({ detail }: { detail: Record<string, unknown> }) => (
  <>
    <Bars rows={readStatusDistribution(detail)} title="Status distribution" />
    <Bars rows={readScoreDistribution(detail)} title="Score distribution" />
  </>
)

export const MediaTrailer = ({ detail }: { detail: Record<string, unknown> }) => {
  const trailer = readTrailer(detail)
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
export const MediaTags = ({ detail }: { detail: Record<string, unknown> }) => {
  const [showSpoilers, setShowSpoilers] = useState(false)
  const tags = readTags(detail)
  if (tags.length === 0) return null

  const spoilers = tags.filter((tag) => tag.spoiler)
  const shown = showSpoilers ? tags : tags.filter((tag) => !tag.spoiler)

  return (
    <section className="tag-list">
      <h2>Tags</h2>
      {shown.map((tag) => (
        <div key={tag.name} className="tag-row">
          <span>{tag.name}</span>
          <span className="muted">{tag.rank}%</span>
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

export const MediaLinks = ({ detail }: { detail: Record<string, unknown> }) => {
  const links = readLinks(detail)
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
