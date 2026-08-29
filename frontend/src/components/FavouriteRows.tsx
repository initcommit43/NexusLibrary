import { type ReactNode, useState } from 'react'
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
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { Grip } from './Grip'

/** One row: a heading, and whatever the caller draws under it. */
export type FavouriteRow = {
  key: string
  label: string
  content: ReactNode
}

const SortableRow = ({ row, arranging }: { row: FavouriteRow; arranging: boolean }) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: row.key,
    disabled: !arranging,
  })

  return (
    <section
      ref={setNodeRef}
      className={isDragging ? 'profile-favourites is-dragging' : 'profile-favourites'}
      style={{ transform: CSS.Transform.toString(transform), transition }}
    >
      <h3>
        {/*
         * The handle carries the drag, never the section: a row holds a grid of cards that
         * drag on their own, and listeners on the section would swallow every one of them.
         */}
        {arranging && (
          <button
            type="button"
            className="row-handle"
            aria-label={`Move the ${row.label} row`}
            {...attributes}
            {...listeners}
          >
            <Grip />
          </button>
        )}
        {row.label}
      </h3>
      {row.content}
    </section>
  )
}

/**
 * The favourite rows in the order their owner put them in.
 *
 * <p>Rows move only while the profile is being arranged. Outside that a row is a heading over
 * a grid, and the cards inside it keep the dragging they have always had — a handle that
 * appears only when asked for is what keeps the two gestures from competing for the pointer.
 */
export const FavouriteRows = ({
  rows,
  arranging,
  onReorder,
}: {
  rows: FavouriteRow[]
  arranging: boolean
  onReorder: (ordered: FavouriteRow[]) => void
}) => {
  const [carried, setCarried] = useState<FavouriteRow | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const start = ({ active }: DragStartEvent) =>
    setCarried(rows.find((row) => row.key === active.id) ?? null)

  const end = ({ active, over }: DragEndEvent) => {
    setCarried(null)
    if (!over || active.id === over.id) return

    const from = rows.findIndex((row) => row.key === active.id)
    const to = rows.findIndex((row) => row.key === over.id)
    if (from === -1 || to === -1) return

    onReorder(arrayMove(rows, from, to))
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={start}
      onDragEnd={end}
      onDragCancel={() => setCarried(null)}
    >
      <SortableContext items={rows.map((row) => row.key)} strategy={verticalListSortingStrategy}>
        {rows.map((row) => (
          <SortableRow key={row.key} row={row} arranging={arranging} />
        ))}
      </SortableContext>

      {/* A whole row of covers under the pointer is a lot to carry: the overlay shows its
          name alone, which is enough to say where it will land. */}
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
