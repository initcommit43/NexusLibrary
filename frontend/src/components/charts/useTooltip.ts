import { useState } from 'react'

/** What a hover names: the thing itself, then the numbers behind it. */
export interface TipContent {
  title: string
  lines?: string[]
}

export interface TipState extends TipContent {
  x: number
  y: number
}

/**
 * One tooltip per panel, fed by whatever is under the pointer.
 *
 * <p>The panel owns the state rather than each mark owning a tip of its own, so a chart of
 * many bars or dots can only ever show one — two tips at once is a chart arguing with
 * itself.
 */
export const useTooltip = () => {
  const [tip, setTip] = useState<TipState | null>(null)

  return {
    tip,
    show: (at: { clientX: number; clientY: number }, content: TipContent) =>
      setTip({ x: at.clientX, y: at.clientY, ...content }),
    hide: () => setTip(null),
  }
}

export type TooltipApi = ReturnType<typeof useTooltip>
