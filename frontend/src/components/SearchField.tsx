import type { InputHTMLAttributes } from 'react'

const SearchIcon = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" aria-hidden>
    <circle cx="11" cy="11" r="6.5" strokeWidth="2" />
    <path d="m16 16 4.5 4.5" strokeWidth="2" strokeLinecap="round" />
  </svg>
)

/**
 * A search box with its magnifier inside, as the native app draws one. Every input attribute
 * passes through; {@code label} names it, since the placeholder vanishes as soon as it is typed
 * over.
 */
export const SearchField = ({
  label,
  className,
  ...input
}: { label: string } & InputHTMLAttributes<HTMLInputElement>) => (
  <label className={className ? `search-field ${className}` : 'search-field'}>
    <SearchIcon />
    <input type="search" aria-label={label} {...input} />
  </label>
)
