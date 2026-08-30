import { useState } from 'react'
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  horizontalListSortingStrategy,
  sortableKeyboardCoordinates,
  useSortable,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { TrackedItem } from '../api/client'
import { Carousel } from './Carousel'
import { EntryCard } from './EntryCard'
import { Grip } from './Grip'

/**
 * How far the pointer travels before a press becomes a drag.
 *
 * <p>Small, because in arrange mode the cover is not a link and there is nothing underneath
 * for a short press to reach — but not nothing, so a hand that shakes on the way down does
 * not shuffle the row.
 */
const DRAG_THRESHOLD_PX = 6

/** Signage, not a control: the whole card is the handle, this only says so. */
const CardGrip = () => (
  <span className="card-grip" aria-hidden="true">
    <Grip />
  </span>
)

const SortableCard = ({ entry, arranging }: { entry: TrackedItem; arranging: boolean }) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: entry.id,
    disabled: !arranging,
  })

  // Outside the mode a card is a cover and a link and nothing else: none of a handle's
  // listeners, and none of its roles either, which would otherwise put a button around every
  // cover and a stop on the way through the page.
  const handle = arranging ? { ...attributes, ...listeners } : {}

  return (
    <div
      ref={setNodeRef}
      className={isDragging ? 'sortable-card is-dragging' : 'sortable-card'}
      // dnd-kit measures the row and hands back where this card should sit while another is
      // being carried; the transition is what makes it slide there rather than jump.
      style={{ transform: CSS.Transform.toString(transform), transition }}
      {...handle}
    >
      {arranging && <CardGrip />}
      <EntryCard entry={entry} artOnly />
    </div>
  )
}

/**
 * Favourites in the order their owner put them in, rearranged by dragging one somewhere else.
 *
 * <p>A row is one row, arranging or not. However many covers it holds it scrolls rather than
 * wraps — eight across on its own, four when it shares its band — because a second line of
 * covers is a second shelf, and a row that grows a line when it is picked at moves everything
 * under it. Carrying a card to the edge scrolls the row along under it.
 *
 * <p>The card being carried is drawn above the row rather than moved inside it, so the gap it
 * came from stays open and the cards it displaces settle into their new places underneath.
 *
 * <p>Cards move only while the profile is being arranged. A cover that can be dragged at any
 * time is a cover that cannot quite be clicked: the drag threshold decides which of the two a
 * press was, and outside the mode a cover should simply be a link to the title.
 */
export const FavouriteGrid = ({
  entries,
  label,
  arranging,
  onReorder,
}: {
  entries: TrackedItem[]
  /** What the row is called, for the arrows a screen reader would otherwise meet unlabelled. */
  label: string
  arranging: boolean
  onReorder: (ordered: TrackedItem[]) => void
}) => {
  const [carried, setCarried] = useState<TrackedItem | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: DRAG_THRESHOLD_PX } }),
    // Space lifts a card, the arrows move it, space puts it down.
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const start = ({ active }: DragStartEvent) =>
    setCarried(entries.find((entry) => entry.id === active.id) ?? null)

  const end = ({ active, over }: DragEndEvent) => {
    setCarried(null)
    if (!over || active.id === over.id) return

    const from = entries.findIndex((entry) => entry.id === active.id)
    const to = entries.findIndex((entry) => entry.id === over.id)
    if (from === -1 || to === -1) return

    onReorder(arrayMove(entries, from, to))
  }

  if (!arranging) {
    return (
      <div className="favourite-carousel">
        <Carousel label={label}>
          {entries.map((entry) => (
            <EntryCard key={entry.id} entry={entry} artOnly />
          ))}
        </Carousel>
      </div>
    )
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={start}
      onDragEnd={end}
      onDragCancel={() => setCarried(null)}
    >
      <SortableContext
        items={entries.map((entry) => entry.id)}
        strategy={horizontalListSortingStrategy}
      >
        <div className="favourite-carousel is-arranging">
          <Carousel label={label}>
            {entries.map((entry) => (
              <SortableCard key={entry.id} entry={entry} arranging={arranging} />
            ))}
          </Carousel>
        </div>
      </SortableContext>

      <DragOverlay dropAnimation={{ duration: 220, easing: 'cubic-bezier(0.2, 0, 0, 1)' }}>
        {carried && (
          <div className="sortable-card is-carried">
            <EntryCard entry={carried} artOnly />
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}
