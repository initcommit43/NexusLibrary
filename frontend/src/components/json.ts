/**
 * Reading the untyped payloads a source cached.
 *
 * <p>Detail arrives as whatever the source sent and is stored that way, so every reader of it
 * asks the same three questions: is this text, is this an object, is this a list. Written once
 * here rather than at the top of each source's reader — five copies of "a blank string is not
 * text" is five chances for one of them to disagree.
 */

/** A non-blank string, or null. Whitespace is not a title, a summary, or a name. */
export const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() ? value : null

/** An object to read keys off, or an empty one — so a caller never has to null-check first. */
export const record = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}

/** A list to walk, or an empty one. */
export const array = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])
