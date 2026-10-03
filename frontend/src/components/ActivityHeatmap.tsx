import { type ReactNode, useMemo } from 'react'
import type { ActivityDay } from '../api/client'
import { Tooltip } from './charts/Tooltip'
import { useTooltip } from './charts/useTooltip'

const DAYS_PER_WEEK = 7

/*
 * Four steps and a blank. Fewer and a busy day looks like a quiet one; more and the eye
 * stops reading them as an order and starts reading them as different colours.
 */
const STEPS = 4

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

/** The day as the tooltip names it: "Wed 27 May 2026". */
const spellOut = (day: Date) =>
  `${WEEKDAYS[day.getDay()]} ${day.getDate()} ${MONTHS[day.getMonth()]} ${day.getFullYear()}`

/** Local, not ISO: a square is the day the reader had, not the day UTC was having. */
const key = (day: Date) =>
  `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`

const startOfWeek = (day: Date) => {
  const start = new Date(day)
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - start.getDay())
  return start
}

/**
 * A reader's months as squares, a week to a column.
 *
 * <p>What it counts is what was started and finished, which is the only history that goes
 * back further than this app does: an imported library brings years of dates with it, and a
 * map drawn from what happened here would show a first square on the day of the import.
 *
 * <p>The scale is relative to the reader's own busiest day rather than to a fixed number.
 * Someone finishing two things a week and someone finishing twenty both get a map that uses
 * its whole range, which is what makes the shape of a year readable at all.
 *
 * <p>Always the same table: as many weeks across as it is asked for, seven days down. The
 * week today falls in is the last column, and the days it has not reached yet are left
 * undrawn while keeping their places — the map is the same size on the day a week opens as
 * on the day it closes, and when the week is out the whole table moves along by a column.
 */
export const ActivityHeatmap = ({
  days,
  weeks,
  aside,
  labelled = false,
}: {
  days: ActivityDay[]
  weeks: number
  /** Drawn on the scale's line at the map's left edge, opposite the scale. */
  aside?: ReactNode
  /**
   * Months over the columns and weekdays down the side, in place of the scale. For a map
   * read on its own terms, where a square has to be placeable in the year without a hover.
   */
  labelled?: boolean
}) => {
  const { tip, show, hide } = useTooltip()

  const { columns, busiest } = useMemo(() => {
    const amounts = new Map(days.map((day) => [day.date, day.amount]))

    const today = new Date()
    today.setHours(0, 0, 0, 0)

    // Whole weeks back from the week today sits in, so every column is a Sunday to a Saturday
    // and the months above them line up with where they actually start.
    const first = startOfWeek(today)
    first.setDate(first.getDate() - (weeks - 1) * DAYS_PER_WEEK)

    const drawn: { date: Date; amount: number; ahead: boolean }[][] = []
    for (let week = 0; week < weeks; week += 1) {
      const column = []
      for (let weekday = 0; weekday < DAYS_PER_WEEK; weekday += 1) {
        const date = new Date(first)
        date.setDate(first.getDate() + week * DAYS_PER_WEEK + weekday)
        column.push({ date, amount: amounts.get(key(date)) ?? 0, ahead: date > today })
      }
      drawn.push(column)
    }

    return {
      columns: drawn,
      busiest: days.reduce((most, day) => Math.max(most, day.amount), 0),
    }
  }, [days, weeks])

  /** Which of the four steps a day sits on, by how it compares to the busiest one. */
  const step = (amount: number) =>
    amount === 0 ? 0 : Math.max(1, Math.ceil((amount / busiest) * STEPS))

  // Under the map and against its right edge, where the last column ends.
  const scale = (
    <p className="heatmap-key muted" aria-hidden>
      Less
      {[0, 1, 2, 3, 4].map((at) => (
        <span key={at} className="heatmap-day" data-step={at} />
      ))}
      More
    </p>
  )

  // A column is named by the month that begins in it, and the first by whatever it opens on.
  const monthLabels = columns.map((column, at) => {
    const opening = at === 0 ? column[0] : column.find(({ date }) => date.getDate() === 1)
    return opening ? MONTHS[opening.date.getMonth()] : ''
  })

  const grid = (
    <div className="heatmap-grid" onPointerLeave={hide}>
      {columns.map((column) => (
        <div key={key(column[0].date)} className="heatmap-week">
          {column.map(({ date, amount, ahead }) => (
            <span
              key={key(date)}
              className={ahead ? 'heatmap-day is-ahead' : 'heatmap-day'}
              data-step={step(amount)}
              // Hover and touch both open it; the map is a graphic and its numbers are
              // read out below it, so nothing here is only reachable by pointer.
              onPointerEnter={(event) =>
                !ahead &&
                show(event, {
                  title: spellOut(date),
                  lines: [amount === 1 ? '1 title' : `${amount} titles`],
                })
              }
            />
          ))}
        </div>
      ))}
    </div>
  )

  if (labelled) {
    return (
      <div className="heatmap is-labelled">
        <div className="heatmap-months" aria-hidden>
          {monthLabels.map((label, at) => (
            <span key={key(columns[at][0].date)}>{label}</span>
          ))}
        </div>
        <div className="heatmap-body">
          {/* Every other day named, as a calendar's margin does: all seven crowd the squares. */}
          <div className="heatmap-weekdays" aria-hidden>
            {WEEKDAYS.map((day, at) => (
              <span key={day}>{at % 2 === 1 ? day : ''}</span>
            ))}
          </div>
          {grid}
        </div>
        <Tooltip tip={tip} />
      </div>
    )
  }

  return (
    <div className="heatmap">
      {grid}

      {aside ? (
        <div className="heatmap-foot">
          {aside}
          {scale}
        </div>
      ) : (
        scale
      )}

      <Tooltip tip={tip} />
    </div>
  )
}
