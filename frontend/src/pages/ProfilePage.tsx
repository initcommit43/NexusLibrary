import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  api,
  errorMessage,
  type ActivityDay,
  type MediaType,
  type ProfileBanner,
  type ProfilePicture,
  type TrackedItem,
} from '../api/client'
import { ActivityHeatmap } from '../components/ActivityHeatmap'
import { AppShell } from '../components/AppShell'
import { BannerPicker } from '../components/BannerPicker'
import { ProfileAvatar } from '../components/ProfileAvatar'
import { publishOwnPicture } from '../components/ownPicture'
import { ProfileBannerFrame } from '../components/ProfileBannerFrame'
import { Figures } from '../components/Figures'
import { ActivityByMonth, ProfileRings, ShelfTable } from '../components/ProfileStats'
import { SegmentedControl } from '../components/SegmentedControl'
import { statusSplit, summarise, timeSpent } from '../components/stats'
import { FavouriteBands } from '../components/FavouriteBands'
import { FavouriteGrid } from '../components/FavouriteGrid'
import { Grip } from '../components/Grip'
import { ScopePicker } from '../components/ScopePicker'
import { useNarrowScreen } from '../components/useNarrowScreen'
import { useAuth } from '../auth/useAuth'
import { MODULES, mediaPathFor, type ModuleDefinition } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

/**
 * The weeks the map holds, and it always holds this many.
 *
 * <p>Anchored to the week today is in, so the window slides by a whole column the moment a
 * week is out: the oldest week drops off the left as the new one opens on the right. The map
 * is the same table on every day of the year and sits in the same place.
 */
const HISTORY_WEEKS = 26

/*
 * A module the map can say anything about. Steam knows how long a game was played and never
 * when, so a games map would be a blank half-year over a shelf someone lives in — the
 * module is left out of the picker rather than offered and then shown empty.
 */
const keepsDates = (module: ModuleDefinition) =>
  module.types.some((type) => type.mediaType !== 'GAME')

/** Arranged first, in the order they were dragged into; then everything never moved. */
const byRank = (a: TrackedItem, b: TrackedItem): number => {
  if (a.favoriteRank === null) return b.favoriteRank === null ? 0 : 1
  if (b.favoriteRank === null) return -1
  return a.favoriteRank - b.favoriteRank
}

/** Rows the reader placed, in that order; the rest keep the order the app lists them in. */
const byPlacement = (order: MediaType[]) => (a: MediaType, b: MediaType) => {
  const placed = (type: MediaType) => {
    const at = order.indexOf(type)
    return at === -1 ? Number.MAX_SAFE_INTEGER : at
  }
  return placed(a) - placed(b)
}

export const ProfilePage = () => {
  const { user } = useAuth()
  const module = useCurrentModule()
  const narrow = useNarrowScreen()
  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [rowOrder, setRowOrder] = useState<MediaType[]>([])
  /** The rows that share a band with the row before them. */
  const [paired, setPaired] = useState<MediaType[]>([])
  const [banner, setBanner] = useState<ProfileBanner | null>(null)
  const [picture, setPicture] = useState<ProfilePicture | null>(null)
  const [history, setHistory] = useState<ActivityDay[]>([])
  /*
   * The same days over every module, which is what the figures beside the map count. A day
   * that saw an episode, a film and a chapter is one day either way: the query groups by the
   * day itself, so nothing here has to take the same date out three times.
   */
  const [everyDay, setEveryDay] = useState<ActivityDay[]>([])

  /*
   * The map opens on whatever module the header is set to, or on all of them when that one
   * keeps no dates, and then follows the picker rather than the header: someone comparing two
   * modules on this page is not asking to be moved to one of them.
   */
  const mapped = MODULES.filter(keepsDates)
  const opening = mapped.find((candidate) => candidate.slug === module.slug) ?? null
  /** Null is every module at once, which only the wide page offers. */
  const [chosenScope, setScope] = useState<ModuleDefinition | null>(opening)
  // A phone's picker has no "all", so there the map falls back to the first module it offers.
  const scope = narrow ? (chosenScope ?? mapped[0]) : chosenScope
  const [picking, setPicking] = useState(false)
  const [adjusting, setAdjusting] = useState(false)
  const [arranging, setArranging] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Not asked again when the map is pointed elsewhere: the figures are the whole library,
  // and the picker only moves the map.
  useEffect(() => {
    api
      .activityHistory(HISTORY_WEEKS)
      .then(setEveryDay)
      .catch(() => setEveryDay([]))
  }, [])

  useEffect(() => {
    Promise.all([
      api.listEntries(),
      api.favouriteRowOrder(),
      api.profileBanner(),
      api.profilePicture(),
    ])
      .then(([library, arrangement, chosen, face]) => {
        setEntries(library)
        setRowOrder(arrangement.order)
        setPaired(arrangement.paired)
        setBanner(chosen)
        setPicture(face)
      })
      .catch((err) =>
        setError(errorMessage(err, 'Could not load your library.')),
      )
  }, [])

  // Its own fetch, because it is the one thing on the page that is asked again when the
  // reader points the map somewhere else.
  useEffect(() => {
    let current = true

    api
      .activityHistory(
        HISTORY_WEEKS,
        scope?.types.map((type) => type.mediaType) ?? [],
      )
      // A map that will not load leaves the squares blank rather than taking the page down
      // with it: nothing else here depends on it.
      .then((days) => current && setHistory(days))
      .catch(() => current && setHistory([]))

    return () => {
      current = false
    }
  }, [scope])

  const totals = useMemo(() => summarise(entries ?? []), [entries])
  const favouriteCount = useMemo(
    () => (entries ?? []).filter((entry) => entry.favorite).length,
    [entries],
  )

  /*
   * One grid per kind of thing, in the order the app lists them, and only the kinds that
   * have any. Not per module: a module can hold two of them, and anime beside manga is a
   * shelf of two different habits — as films are beside the series you watch weekly.
   *
   * A heading over an empty grid says the kind exists; here it would only say you have
   * nothing in it, which the shelf counts below already say better.
   */
  const favourites = useMemo(() => {
    const marked = (entries ?? []).filter((entry) => entry.favorite)

    return MODULES.flatMap((module) => module.types)
      .sort((a, b) => byPlacement(rowOrder)(a.mediaType, b.mediaType))
      .map((type) => ({
        key: type.mediaType,
        label: type.label,
        marked: marked.filter((entry) => entry.mediaType === type.mediaType).sort(byRank),
      }))
      .filter((group) => group.marked.length > 0)
  }, [entries, rowOrder])

  /**
   * The rows as bands: one across the page, or two sharing it half and half.
   *
   * <p>Read against the rows actually drawn rather than the stored order, so a row stored as
   * sharing with an empty one — which is drawn nowhere — stands on its own rather than
   * beside a gap.
   */
  const bands = useMemo(() => {
    const grouped: (typeof favourites)[] = []

    for (const row of favourites) {
      const band = grouped[grouped.length - 1]
      if (paired.includes(row.key) && band?.length === 1) band.push(row)
      else grouped.push([row])
    }
    return grouped
  }, [favourites, paired])

  /**
   * Writes an arrangement of the bands, the rows with nothing in them included.
   *
   * <p>An empty row is not drawn but keeps its place: it is put back among the bands where
   * it was stored, so unfavouriting the last title in a row and marking another later brings
   * the row back where it was rather than at the end. Between bands, never inside one — a
   * row that came to sit between two that share a band would break the pair on the way back
   * from the server, which reads the pairing off the row before.
   */
  const writeBands = async (arranged: string[][]) => {
    const drawn = arranged.flat() as MediaType[]
    const sharing = arranged.filter((band) => band.length === 2).map((band) => band[1] as MediaType)

    const units: MediaType[][] = arranged.map((band) => band as MediaType[])
    for (const type of rowOrder.filter((row) => !drawn.includes(row))) {
      const before = rowOrder.slice(0, rowOrder.indexOf(type)).filter((row) => drawn.includes(row))

      let seen = 0
      let at = 0
      while (at < units.length && seen < before.length) {
        seen += units[at].filter((row) => drawn.includes(row)).length
        at += 1
      }
      units.splice(at, 0, [type])
    }

    const order = units.flat()
    const held = { order: rowOrder, paired }
    setRowOrder(order)
    setPaired(sharing)

    try {
      const saved = await api.replaceFavouriteRowOrder(order, sharing)
      setRowOrder(saved.order)
      setPaired(saved.paired)
    } catch (err) {
      setError(errorMessage(err, 'Could not save that order.'))
      setRowOrder(held.order)
      setPaired(held.paired)
    }
  }

  /*
   * Every shelf the app has, including the empty ones: a count of zero is the honest answer
   * and the link is how you go and change it.
   *
   * <p>One row rather than a tile of counts and a separate list of links. The two said less
   * between them than the row says on its own, and cost the top of the page to say it.
   */
  const shelves = useMemo(
    () =>
      MODULES.flatMap((module) =>
        module.types.map((type) => {
          const held = (entries ?? []).filter((entry) => entry.mediaType === type.mediaType)
          return {
            key: `${module.slug}/${type.slug}`,
            path: `/library/${module.slug}/${type.slug}`,
            label: type.label,
            summary: summarise(held),
            split: statusSplit(held),
            time: timeSpent(held, type.mediaType),
          }
        }),
      ),
    [entries],
  )

  /**
   * Written as one arrangement of every favourite, not of the module that was dragged.
   *
   * <p>Rank is a position in that whole list, so sending only the grid that moved would
   * hand two of them the same numbers. The grid shows the new order before the request
   * answers; if the write fails the server's own copy is what comes back.
   */
  const reorder = async (key: string, ordered: TrackedItem[]) => {
    const arranged = favourites.flatMap((group) => (group.key === key ? ordered : group.marked))
    const ranks = new Map(arranged.map((entry, rank) => [entry.id, rank]))

    setEntries(
      (held) =>
        held?.map((entry) => {
          const rank = ranks.get(entry.id)
          return rank === undefined ? entry : { ...entry, favoriteRank: rank }
        }) ?? null,
    )

    try {
      const saved = await api.reorderFavourites(arranged.map((entry) => entry.id))
      const byId = new Map(saved.map((entry) => [entry.id, entry]))
      setEntries((held) => held?.map((entry) => byId.get(entry.id) ?? entry) ?? null)
    } catch (err) {
      setError(errorMessage(err, 'Could not save that order.'))
      api.listEntries().then(setEntries).catch(() => {})
    }
  }

  return (
    <AppShell>
      {/* A phone leads with the banner, so the reader's own name is the page's title there. */}
      {!narrow && <h1 className="page-title">Profile</h1>}

      {/*
        * Banner and identity are one block, as on a title's page: the avatar rides the
        * banner's lower edge, so the two read as a head rather than as a picture with a
        * name under it. Contained rather than breaking out to the window — the profile is
        * about the reader, and a full-bleed image would make the picture the page.
        */}
      <section className={banner ? 'profile-head has-profile-banner' : 'profile-head'}>
        {banner && (
          <ProfileBannerFrame
            banner={banner}
            adjusting={adjusting}
            onAdjust={() => setAdjusting(true)}
            onFramed={setBanner}
            onClose={() => setAdjusting(false)}
          />
        )}

        <div className="profile-identity">
          <ProfileAvatar
            picture={picture}
            onChanged={(changed) => {
              setError(null)
              setPicture(changed)
              // The header shows it too, and should not wait for a reload to catch up.
              if (user) publishOwnPicture(user.id, changed)
            }}
            onError={setError}
          />
          <div className="profile-name">
            {narrow ? <h1>{user?.username}</h1> : <h2>{user?.username}</h2>}
            <p className="muted">{user?.email}</p>
          </div>

          {/*
            * Everything about the picture in one corner, tucked under its lower edge as the
            * avatar rides over it on the other side. The credit says where the banner came
            * from rather than leaving it an anonymous backdrop; the button is out of sight
            * until the head is reached for, like the fold control on home — it belongs on
            * the thing it changes, but a profile is not read for its wallpaper. The avatar's
            * own credit is gone: pictures are about to be uploaded rather than picked.
            */}
          <div className="profile-head-aside">
            {banner && (
              <p className="profile-banner-credit muted">
                Banner from <Link to={mediaPathFor(banner)}>{banner.title}</Link>
              </p>
            )}
            <button
              type="button"
              className="banner-button"
              disabled={entries === null}
              onClick={() => {
                setAdjusting(false)
                setPicking(true)
              }}
            >
              {banner ? 'Change banner' : 'Add a banner'}
            </button>
          </div>
        </div>
      </section>

      {/* A phone has no account menu, so settings are reached from the profile they belong to. */}
      <Link className="profile-settings" to="/settings">
        Settings
      </Link>

      {picking && entries !== null && (
        <BannerPicker
          entries={entries}
          chosen={banner}
          // Straight into the crop, as an upload goes: a wide picture dropped into the strip
          // almost never shows the part it was picked for.
          onChosen={(chosen) => {
            setBanner(chosen)
            setAdjusting(true)
          }}
          onCleared={() => setBanner(null)}
          onClose={() => setPicking(false)}
        />
      )}

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries === null && !error && <p className="muted">Loading your library…</p>}

      {entries !== null && !narrow && (
        <>
          <ProfileRings totals={totals} favourites={favouriteCount} />

          <section className="profile-panel">
            <div className="profile-panel-head">
              <div className="profile-panel-title">
                <h2>Activity</h2>
                <span className="profile-panel-meta">
                  {history.length.toLocaleString()} active days · last {HISTORY_WEEKS} weeks
                </span>
              </div>
              <SegmentedControl
                className="profile-scope"
                label="Scope"
                value={scope?.slug ?? 'all'}
                options={[
                  { value: 'all', label: 'All' },
                  ...mapped.map((candidate) => ({ value: candidate.slug, label: candidate.label })),
                ]}
                onChange={(slug) =>
                  setScope(mapped.find((candidate) => candidate.slug === slug) ?? null)
                }
              />
            </div>
            <div className="profile-activity">
              <ActivityHeatmap days={history} weeks={HISTORY_WEEKS} labelled />
              <ActivityByMonth days={history} />
            </div>
          </section>
        </>
      )}

      {entries !== null && narrow && (
        <>
          {/* The native app's sections, which the phone follows rather than the wide page. */}
          <div className="profile-overview">
            <section className="status-section">
              <h2>
                Activity
                {/* The phone's way to the stats, which the account menu carries when wide. */}
                <Link className="section-action profile-stats-link" to="/stats">
                  Stats →
                </Link>
              </h2>
              {/* A phone keeps the heading to the title and the stats, and names the scope under
                  the map it narrows, opposite the scale. */}
              <ActivityHeatmap
                days={history}
                weeks={HISTORY_WEEKS}
                aside={
                  <ScopePicker modules={mapped} current={scope ?? mapped[0]} onChoose={setScope} />
                }
              />
            </section>

            <section className="status-section">
              <h2>At a glance</h2>
              <Figures
                panelled
                figures={[
                  { label: 'tracked', value: totals.tracked.toLocaleString() },
                  { label: 'completed', value: totals.completed.toLocaleString() },
                  { label: 'rated', value: totals.rated.toLocaleString() },
                  {
                    label: 'average score',
                    value: totals.meanScore === null ? '—' : totals.meanScore.toFixed(1),
                  },
                  { label: 'favourites', value: favouriteCount.toLocaleString() },
                  {
                    label: 'active days',
                    value: String(everyDay.length),
                    hint: 'last 7 months',
                  },
                ]}
              />
            </section>
          </div>
        </>
      )}

      {entries !== null && (
        <>
          <section className="status-section">
            <h2>
              Favourites {!narrow && <span className="muted">({favouriteCount})</span>}
              {favourites.length > 0 && (
                <button
                  type="button"
                  className="ghost small section-action"
                  aria-pressed={arranging}
                  onClick={() => setArranging((on) => !on)}
                >
                  {arranging ? (
                    'Done'
                  ) : narrow ? (
                    'Edit'
                  ) : (
                    <>
                      <Grip />
                      Arrange
                    </>
                  )}
                </button>
              )}
            </h2>

            {/* Said once, where it is needed: the mode is what the button already named. */}
            {arranging && (
              <p className="arrange-hint">
                Drag a cover to reorder it. Drag a row by its handle and the grid of places it
                can go opens: a free cell takes the row, a taken one trades places with it, and
                two rows in one band share it half and half.
              </p>
            )}

            {favourites.length === 0 ? (
              <p className="muted">
                Nothing marked yet. Open an entry and use the heart in its editor to keep it
                here.
              </p>
            ) : (
              <FavouriteBands
                arranging={arranging}
                onArrange={(next) => void writeBands(next)}
                bands={bands.map((band) =>
                  band.map(({ key, label, marked }) => ({
                    key,
                    label,
                    count: marked.length,
                    content: (
                      <FavouriteGrid
                        entries={marked}
                        label={label}
                        arranging={arranging}
                        onReorder={(ordered) => void reorder(key, ordered)}
                      />
                    ),
                  })),
                )}
              />
            )}
          </section>

          {!narrow && <ShelfTable shelves={shelves} average={totals.meanScore} />}

          {narrow && (
            <section className="status-section">
              <h2>
                Your library
                <Link className="section-action" to="/stats">
                  See all stats →
                </Link>
              </h2>

              <table className="stat-table">
                <tbody>
                  {shelves.map(({ key, path, label, summary, time }) => (
                    <tr key={key}>
                      <th scope="row">
                        <Link to={path}>{label}</Link>
                      </th>
                      <td>{`${summary.tracked.toLocaleString()} entries`}</td>
                      <td className="muted">
                        {summary.completed > 0 ? `${summary.completed.toLocaleString()} completed` : ''}
                      </td>
                      <td className="muted">
                        {summary.meanScore === null ? '' : `${summary.meanScore.toFixed(1)} average`}
                      </td>
                      <td className="muted">
                        {time === null ? '' : `${time.amount.toLocaleString()} ${time.unit}`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}
        </>
      )}

    </AppShell>
  )
}
