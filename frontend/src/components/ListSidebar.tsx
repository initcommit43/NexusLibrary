import type { TrackedItem, TrackingStatus } from '../api/client'
import { SORT_LABELS, SORT_ORDER, distinct, type ListFilters, type SortKey } from './listFilters'
import type { MediaTypeDefinition } from '../modules/registry'

export const ListSidebar = ({
  type,
  entries,
  filters,
  onChange,
}: {
  type: MediaTypeDefinition
  entries: TrackedItem[]
  filters: ListFilters
  onChange: (next: ListFilters) => void
}) => {
  const set = <K extends keyof ListFilters>(key: K, value: ListFilters[K]) =>
    onChange({ ...filters, [key]: value })

  const countIn = (status: TrackingStatus | 'ALL') =>
    status === 'ALL' ? entries.length : entries.filter((entry) => entry.status === status).length

  const formats = distinct(entries, (entry) => entry.metadata.format)
  const genres = distinct(entries, (entry) => entry.metadata.genres)
  // Only games carry these, so the control appears for games and nowhere else.
  const platforms = distinct(entries, (entry) => entry.metadata.platforms)

  return (
    <aside className="list-sidebar">
      <input
        type="search"
        value={filters.query}
        placeholder="Filter"
        aria-label="Filter by title"
        onChange={(e) => set('query', e.target.value)}
      />

      <section>
        <h2>Lists</h2>
        <ul className="list-links">
          {(['ALL', ...type.statusOrder] as const).map((status) => (
            <li key={status}>
              <button
                type="button"
                className={filters.status === status ? 'list-link active' : 'list-link'}
                onClick={() => set('status', status)}
              >
                <span>{status === 'ALL' ? 'All' : type.statusLabels[status]}</span>
                <span className="muted">{countIn(status)}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2>Filters</h2>

        {formats.length > 0 && (
          <label className="field">
            <span>Format</span>
            <select value={filters.format} onChange={(e) => set('format', e.target.value)}>
              <option value="">Any</option>
              {formats.map((format) => (
                <option key={format} value={format}>
                  {format}
                </option>
              ))}
            </select>
          </label>
        )}

        {genres.length > 0 && (
          <label className="field">
            <span>Genre</span>
            <select value={filters.genre} onChange={(e) => set('genre', e.target.value)}>
              <option value="">Any</option>
              {genres.map((genre) => (
                <option key={genre} value={genre}>
                  {genre}
                </option>
              ))}
            </select>
          </label>
        )}

        {platforms.length > 0 && (
          <label className="field">
            <span>Platform</span>
            <select value={filters.platform} onChange={(e) => set('platform', e.target.value)}>
              <option value="">Any</option>
              {platforms.map((platform) => (
                <option key={platform} value={platform}>
                  {platform}
                </option>
              ))}
            </select>
          </label>
        )}

        <label className="field">
          <span>Sort</span>
          <select value={filters.sort} onChange={(e) => set('sort', e.target.value as SortKey)}>
            {SORT_ORDER.map((key) => (
              <option key={key} value={key}>
                {key === 'PROGRESS' ? type.progressLabel : SORT_LABELS[key]}
              </option>
            ))}
          </select>
        </label>
      </section>
    </aside>
  )
}
