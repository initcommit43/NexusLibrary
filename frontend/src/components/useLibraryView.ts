import { useState } from 'react'

export type LibraryView = 'grid' | 'list'

const STORAGE_KEY = 'nexus-library-view'

const read = (): LibraryView => {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'list' ? 'list' : 'grid'
  } catch {
    return 'grid'
  }
}

/**
 * Covers or rows for the library, remembered on this device.
 *
 * <p>One choice for every module and every width: someone who reads their shelves as a list
 * does so on each of them, and a phone and a desktop are separate devices with their own
 * storage anyway.
 */
export const useLibraryView = (): [LibraryView, (next: LibraryView) => void] => {
  const [view, setView] = useState(read)

  const choose = (next: LibraryView) => {
    setView(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Blocked storage still switches the view; it just is not remembered past the visit.
    }
  }

  return [view, choose]
}
