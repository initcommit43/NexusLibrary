import { type ReactNode, useRef, useState } from 'react'
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MeasuringStrategy,
  PointerSensor,
  closestCorners,
  pointerWithin,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type CollisionDetection,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { Grip } from './Grip'

/** One row: a heading, how much it holds, and whatever the caller draws under it. */
export type FavouriteRow = {
  key: string
  label: string
  count: number
  content: ReactNode
}

/** A band holds one row across the page, or two sharing it half and half. */
export type FavouriteBand = FavouriteRow[]

/** A place in the grid: which band, and which half of it. */
type Cell = { band: number; side: 0 | 1; row: FavouriteRow | null }

/**
 * The cell under the pointer, not the one whose middle is nearest it.
 *
 * <p>Corners are the fallback for the gutters between cells, where the pointer is inside
 * nothing at all.
 */
const cellUnderPointer: CollisionDetection = (args) => {
  const inside = pointerWithin(args)
  return inside.length > 0 ? inside : closestCorners(args)
}

/** The handle, and the only part of a row that carries its drag. */
const RowHandle = ({ row }: { row: FavouriteRow }) => {
  const { attributes, listeners, setNodeRef } = useDraggable({ id: row.key })

  return (
    <button
      ref={setNodeRef}
      type="button"
      className="row-handle"
      aria-label={`Move the ${row.label} row`}
      {...attributes}
      {...listeners}
    >
      <Grip />
    </button>
  )
}

/** One cell of the grid: a half of a band, taken or free, and always the same size. */
const PlanCell = ({ cell }: { cell: Cell }) => {
  const { isOver, setNodeRef } = useDroppable({ id: `cell:${cell.band}:${cell.side}` })

  const classes = ['plan-cell']
  if (cell.row) classes.push('is-taken')
  if (isOver) classes.push('is-over')

  return (
    <div ref={setNodeRef} className={classes.join(' ')}>
      {cell.row && (
        <>
          {cell.row.label}
          <span className="muted">
            {cell.row.count === 1 ? '1 title' : `${cell.row.count} titles`}
          </span>
        </>
      )}
    </div>
  )
}

/**
 * A reader's favourite rows, one or two to a band, in the order and pairing they arranged.
 *
 * <p>Picking a row up puts the page aside and draws the grid of places it can go: two halves
 * to a band, one band under the last for a row that wants the page to itself, and every cell
 * the same size. A free cell takes the row; a taken one trades places with it. Nothing in
 * the grid moves while a row is being carried over it, which is the whole of what made the
 * earlier arranging impossible to land.
 */
export const FavouriteBands = ({
  bands,
  arranging,
  onArrange,
}: {
  bands: FavouriteBand[]
  arranging: boolean
  /** The arrangement a drop produced: the rows of each band, in order. */
  onArrange: (bands: string[][]) => void
}) => {
  const [carried, setCarried] = useState<FavouriteRow | null>(null)
  const live = useRef<HTMLDivElement>(null)
  // The height the rows had, kept under the grid so the page does not collapse and take the
  // pointer somewhere else the moment a row is lifted.
  const [held, setHeld] = useState<number | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor),
  )

  const start = ({ active }: DragStartEvent) => {
    setHeld(live.current?.offsetHeight ?? null)
    setCarried(bands.flat().find((row) => row.key === active.id) ?? null)
  }

  const done = () => {
    setCarried(null)
    setHeld(null)
  }

  /** Every band as its two halves, and one empty band under them all. */
  const grid: Cell[] = [
    ...bands.flatMap((band, at): Cell[] => [
      { band: at, side: 0, row: band[0] ?? null },
      { band: at, side: 1, row: band[1] ?? null },
    ]),
    { band: bands.length, side: 0, row: null },
    { band: bands.length, side: 1, row: null },
  ]

  /**
   * Where a drop leaves the arrangement.
   *
   * <p>A free cell takes the carried row. A taken one trades: the row already there goes to
   * the half the carried row came from, which is the only reading that leaves every other
   * row where the reader left it.
   */
  const end = ({ active, over }: DragEndEvent) => {
    const key = String(active.id)
    done()
    if (!over) return

    const [, band, side] = String(over.id).split(':')
    const target = { band: Number(band), side: Number(side) }

    const halves: (string | null)[][] = bands.map((held) => [
      held[0]?.key ?? null,
      held[1]?.key ?? null,
    ])
    halves.push([null, null])

    const from = halves.findIndex((pair) => pair.includes(key))
    const mine = halves[from].indexOf(key)
    if (from === target.band && mine === target.side) return

    halves[target.band][target.side] = key
    halves[from][mine] = null
    const displaced = bands[target.band]?.[target.side]?.key ?? null
    if (displaced && displaced !== key) halves[from][mine] = displaced

    // A band emptied by the move closes up, and a half left open closes over rather than
    // leaving a row stranded in the second half of its own band.
    const arranged = halves
      .map((pair) => pair.filter((row): row is string => row !== null))
      .filter((pair) => pair.length > 0)

    const before = bands.map((held) => held.map((row) => row.key))
    if (JSON.stringify(arranged) !== JSON.stringify(before)) onArrange(arranged)
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={cellUnderPointer}
      // The grid is drawn as the drag begins, so its cells are not there to be measured the
      // once: measuring throughout is what makes them the right shape from the first move.
      measuring={{ droppable: { strategy: MeasuringStrategy.Always } }}
      onDragStart={start}
      onDragEnd={end}
      onDragCancel={done}
    >
      <div className="favourite-bands" style={held === null ? undefined : { minHeight: held }}>
        {/* Kept mounted under the grid rather than swapped out: the handle being dragged is
            in here, and a handle that unmounts mid-drag takes the drag with it. */}
        <div
          className="band-rows"
          hidden={carried !== null}
          // Said inline as well as in the stylesheet: a class that sets display beats the
          // hidden attribute, and the rows staying up is the grid never appearing.
          style={carried === null ? undefined : { display: 'none' }}
          ref={live}
        >
          {bands.map((band) => (
            <div
              className="favourite-band"
              data-rows={band.length}
              key={band.map((row) => row.key).join('+')}
            >
              {band.map((row) => (
                <section key={row.key} className="profile-favourites">
                  <h3>
                    {arranging && <RowHandle row={row} />}
                    {row.label}
                  </h3>
                  {row.content}
                </section>
              ))}
            </div>
          ))}
        </div>

        {carried && (
          <div className="band-plan">
            {grid.map((cell) => (
              <PlanCell
                key={`${cell.band}:${cell.side}`}
                cell={cell.row?.key === carried.key ? { ...cell, row: null } : cell}
              />
            ))}
          </div>
        )}
      </div>

      {/* A whole row of covers is a lot to carry: the overlay shows its name, which is
          enough to say what is in hand. */}
      <DragOverlay dropAnimation={{ duration: 220, easing: 'cubic-bezier(0.2, 0, 0, 1)' }}>
        {carried && (
          <div className="row-carried">
            <Grip />
            {carried.label}
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}
