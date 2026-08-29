import { useLayoutEffect, useRef, useState } from 'react'
import { Tooltip } from './Tooltip'
import { useTooltip } from './useTooltip'

export interface ScatterPoint {
  x: number
  y: number
  /** Dot area follows this; left off everywhere, every dot is the same modest size. */
  weight?: number
  label: string
  /** The numbers the tooltip states under the name. */
  lines?: string[]
}

export interface Quadrants {
  x: number
  y: number
  /** Clockwise from the top left: what sitting in each quarter of the field means. */
  corners: [string, string, string, string]
}

interface Props {
  points: ScatterPoint[]
  xDomain: [number, number]
  yDomain: [number, number]
  xLabel: string
  yLabel: string
  /** Median lines quartering the field, for a chart whose quarters mean something. */
  quadrants?: Quadrants
  /** The y = x diagonal, for a chart whose two axes speak the same unit. */
  agreement?: boolean
  height?: number
}

const MARGIN = { top: 20, right: 16, bottom: 30, left: 36 }

const DOT_LEAST = 4
const DOT_MOST = 13

/** A tick every fifth or so of the span, snapped to the steps an axis is read in. */
const tickStep = (span: number): number => {
  const raw = span / 5
  const power = 10 ** Math.floor(Math.log10(raw))
  const unit = raw / power
  return (unit <= 1 ? 1 : unit <= 2 ? 2 : unit <= 5 ? 5 : 10) * power
}

const ticksFor = ([low, high]: [number, number]): number[] => {
  const step = tickStep(high - low)
  const ticks: number[] = []
  // The epsilon keeps the last tick when float steps land a hair past the top of the domain.
  for (let at = Math.ceil(low / step) * step; at <= high + step / 1e6; at += step) {
    ticks.push(at)
  }
  return ticks
}

const numeral = (value: number): string =>
  Number.isInteger(value) ? value.toLocaleString() : value.toFixed(1)

/**
 * Dots on the page's own ground, sized to the pixel rather than to a viewBox.
 *
 * <p>The SVG is drawn at the width its container measures out, because a viewBox that
 * stretches would stretch the tick labels with it — chart text has to sit at the same size
 * as the text around it or the chart reads as a pasted-in picture.
 *
 * <p>Dots take focus as well as hover, and heavy dots render first so the small ones stay
 * reachable on top of them.
 */
export const Scatter = ({
  points,
  xDomain,
  yDomain,
  xLabel,
  yLabel,
  quadrants,
  agreement = false,
  height = 300,
}: Props) => {
  const ref = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  const tip = useTooltip()

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const watch = new ResizeObserver((observed) =>
      setWidth(Math.floor(observed[0]?.contentRect.width ?? 0)),
    )
    watch.observe(el)
    return () => watch.disconnect()
  }, [])

  const plotWidth = width - MARGIN.left - MARGIN.right
  const plotHeight = height - MARGIN.top - MARGIN.bottom
  const toX = (value: number) =>
    MARGIN.left + ((value - xDomain[0]) / (xDomain[1] - xDomain[0])) * plotWidth
  const toY = (value: number) =>
    MARGIN.top + plotHeight - ((value - yDomain[0]) / (yDomain[1] - yDomain[0])) * plotHeight

  const weighted = points.some((point) => point.weight !== undefined)
  const heaviest = Math.max(...points.map((point) => point.weight ?? 1))
  const radius = (weight = 1) =>
    weighted ? DOT_LEAST + (DOT_MOST - DOT_LEAST) * Math.sqrt(weight / heaviest) : 5

  const drawn = [...points].sort((a, b) => (b.weight ?? 1) - (a.weight ?? 1))

  // Where the y = x line enters and leaves the plot, since the domains need not match.
  const agreeFrom = Math.max(xDomain[0], yDomain[0])
  const agreeTo = Math.min(xDomain[1], yDomain[1])

  return (
    <div ref={ref} className="scatter">
      {/* A group rather than one img: the dots take focus one by one, each with its own name. */}
      {width > 0 && (
        <svg width={width} height={height} role="group" aria-label={`${yLabel} against ${xLabel}`}>
          {ticksFor(yDomain).map((tick) => (
            <line
              key={tick}
              className="scatter-grid"
              x1={MARGIN.left}
              x2={width - MARGIN.right}
              y1={toY(tick)}
              y2={toY(tick)}
            />
          ))}
          <line
            className="scatter-axis"
            x1={MARGIN.left}
            x2={width - MARGIN.right}
            y1={height - MARGIN.bottom}
            y2={height - MARGIN.bottom}
          />

          {ticksFor(yDomain).map((tick) => (
            <text
              key={tick}
              className="scatter-tick"
              x={MARGIN.left - 8}
              y={toY(tick)}
              textAnchor="end"
              dominantBaseline="middle"
            >
              {numeral(tick)}
            </text>
          ))}
          {ticksFor(xDomain).map((tick) => (
            <text
              key={tick}
              className="scatter-tick"
              x={toX(tick)}
              y={height - MARGIN.bottom + 16}
              textAnchor="middle"
            >
              {numeral(tick)}
            </text>
          ))}

          <text className="scatter-name" x={MARGIN.left} y={MARGIN.top - 8}>
            {yLabel}
          </text>
          <text
            className="scatter-name"
            x={width - MARGIN.right}
            y={height - 4}
            textAnchor="end"
          >
            {xLabel}
          </text>

          {agreement && agreeTo > agreeFrom && (
            <line
              className="scatter-median"
              x1={toX(agreeFrom)}
              y1={toY(agreeFrom)}
              x2={toX(agreeTo)}
              y2={toY(agreeTo)}
            />
          )}

          {quadrants && (
            <>
              <line
                className="scatter-median"
                x1={toX(quadrants.x)}
                x2={toX(quadrants.x)}
                y1={MARGIN.top}
                y2={height - MARGIN.bottom}
              />
              <line
                className="scatter-median"
                x1={MARGIN.left}
                x2={width - MARGIN.right}
                y1={toY(quadrants.y)}
                y2={toY(quadrants.y)}
              />
              <text className="scatter-corner" x={MARGIN.left + 8} y={MARGIN.top + 14}>
                {quadrants.corners[0]}
              </text>
              <text
                className="scatter-corner"
                x={width - MARGIN.right - 8}
                y={MARGIN.top + 14}
                textAnchor="end"
              >
                {quadrants.corners[1]}
              </text>
              <text
                className="scatter-corner"
                x={width - MARGIN.right - 8}
                y={height - MARGIN.bottom - 8}
                textAnchor="end"
              >
                {quadrants.corners[2]}
              </text>
              <text className="scatter-corner" x={MARGIN.left + 8} y={height - MARGIN.bottom - 8}>
                {quadrants.corners[3]}
              </text>
            </>
          )}

          {drawn.map((point, index) => (
            <circle
              key={`${point.label}-${index}`}
              className="scatter-dot"
              cx={toX(point.x)}
              cy={toY(point.y)}
              r={radius(point.weight)}
              tabIndex={0}
              role="img"
              aria-label={[point.label, ...(point.lines ?? [])].join(', ')}
              onMouseMove={(event) => tip.show(event, { title: point.label, lines: point.lines })}
              onMouseLeave={tip.hide}
              onFocus={(event) => {
                const box = event.currentTarget.getBoundingClientRect()
                tip.show(
                  { clientX: box.x + box.width / 2, clientY: box.y },
                  { title: point.label, lines: point.lines },
                )
              }}
              onBlur={tip.hide}
            />
          ))}
        </svg>
      )}
      <Tooltip tip={tip.tip} />
    </div>
  )
}
