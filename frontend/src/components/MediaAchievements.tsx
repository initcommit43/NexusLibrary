import { useEffect, useState } from 'react'
import { api, type AchievementCatalogueEntry, type MediaDetail } from '../api/client'
import { AchievementList } from './AchievementList'
import { achievementCatalogue, achievementProgress } from './achievements'

/**
 * A game's achievements on its own page, tracked or not.
 *
 * <p>The list is on the shared item as soon as anyone has synced the game, and that copy is
 * shown straight away. A game nobody here has imported has none, and it is fetched on its
 * own rather than with the page: the request behind it goes to Steam, and the rest of the
 * page should not wait on it. Failing is quiet for the same reason — no achievements is the
 * normal answer for plenty of games, and an error about one is worse than the silence.
 */
export const MediaAchievements = ({ media }: { media: MediaDetail }) => {
  // Keyed by the title it was fetched for, so following a relation shows nothing rather
  // than the previous game's achievements while the next fetch is in flight.
  const [loaded, setLoaded] = useState<{ key: string; catalogue: AchievementCatalogueEntry[] } | null>(
    null,
  )

  const { mediaType, source, externalId } = media
  const cached = achievementCatalogue(media)
  const known = cached.length > 0
  const key = `${source}/${externalId}`

  useEffect(() => {
    if (mediaType !== 'GAME' || known) return

    let current = true
    api
      .mediaAchievements(source, externalId)
      .then((list) => {
        if (current) setLoaded({ key: `${source}/${externalId}`, catalogue: list })
      })
      .catch(() => {})

    return () => {
      current = false
    }
  }, [mediaType, source, externalId, known])

  if (mediaType !== 'GAME') return null

  const catalogue = known ? cached : loaded?.key === key ? loaded.catalogue : []

  return (
    <AchievementList
      catalogue={catalogue}
      progress={media.entry ? achievementProgress(media.entry) : null}
    />
  )
}
