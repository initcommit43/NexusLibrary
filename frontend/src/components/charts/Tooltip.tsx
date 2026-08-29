import { useLayoutEffect, useRef, useState } from 'react'
import type { TipState } from './useTooltip'

/**
 * Fixed-position, so it tracks the pointer regardless of what the chart sits inside, and
 * measured after render so it can pull back from the viewport's edges — the rightmost dot
 * is exactly the one whose numbers would otherwise land off screen.
 *
 * <p>aria-hidden, because the marks themselves carry labels and announcing every mousemove
 * would read the whole chart aloud twice.
 */
export const Tooltip = ({ tip }: { tip: TipState | null }) => {
  const ref = useRef<HTMLDivElement>(null)
  const [at, setAt] = useState({ left: 0, top: 0 })

  useLayoutEffect(() => {
    const box = ref.current?.getBoundingClientRect()
    if (!tip || !box) return

    const margin = 8
    const left = Math.min(
      Math.max(tip.x - box.width / 2, margin),
      window.innerWidth - box.width - margin,
    )
    const above = tip.y - box.height - 12
    setAt({ left, top: above < margin ? tip.y + 16 : above })
  }, [tip])

  if (!tip) return null

  return (
    <div ref={ref} className="chart-tip" style={{ left: at.left, top: at.top }} aria-hidden>
      <strong>{tip.title}</strong>
      {tip.lines?.map((line) => <span key={line}>{line}</span>)}
    </div>
  )
}
