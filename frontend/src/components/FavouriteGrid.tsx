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
  rectSortingStrategy,
  sortableKeyboardCoordinates,
  useSortable,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { TrackedItem } from '../api/client'
import { EntryCard } from './EntryCard'

/**
 * How far the pointer travels before a press becomes a drag.
 *
 * <p>The card is a link before it is a handle, so a click that never moves has to reach the
 * cover underneath it.
 */
const DRAG_THRESHOLD_PX = 6

const SortableCard = ({ entry }: { entry: TrackedItem }) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: entry.id,
  })

  return (
    <div
      ref={setNodeRef}
      className={isDragging ? 'sortable-card is-dragging' : 'sortable-card'}
      // dnd-kit measures the grid and hands back where this card should sit while another is
      // being carried; the transition is what makes it slide there rather than jump.
      style={{ transform: CSS.Transform.toString(transform), transition }}
      {...attributes}
      {...listeners}
    >
      <EntryCard entry={entry} />
    </div>
  )
}

/**
 * Favourites in the order their owner put them in, rearranged by dragging one somewhere else.
 *
 * <p>The card being carried is drawn above the grid rather than moved inside it, so the gap
 * it came from stays open and the cards it displaces settle into their new places underneath.
 */
export const FavouriteGrid = ({
  entries,
  onReorder,
}: {
  entries: TrackedItem[]
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

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={start}
      onDragEnd={end}
      onDragCancel={() => setCarried(null)}
    >
      <SortableContext items={entries.map((entry) => entry.id)} strategy={rectSortingStrategy}>
        <div className="cover-grid">
          {entries.map((entry) => (
            <SortableCard key={entry.id} entry={entry} />
          ))}
        </div>
      </SortableContext>

      <DragOverlay dropAnimation={{ duration: 220, easing: 'cubic-bezier(0.2, 0, 0, 1)' }}>
        {carried && (
          <div className="sortable-card is-carried">
            <EntryCard entry={carried} />
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}
