/**
 * CandlestickChart.jsx — Pure SVG candlestick chart, zero external charting lib
 * Features:
 *  - Real OHLC candles from backend API (/api/crypto/candles or /api/stocks/candles)
 *  - Fallback: generates realistic synthetic candles when backend is offline
 *  - Hover tooltip: exact Date, Time, Open, High, Low, Close, Volume, Change%
 *  - Crosshair lines (vertical + horizontal) following mouse
 *  - Y-axis price labels (right side)
 *  - X-axis time labels (bottom)
 *  - Volume bars at bottom (proportional)
 *  - Fully responsive via ResizeObserver
 *  - Bull candles: emerald, Bear candles: rose
 *  - interval selector: 1m 5m 1h 4h 1d passed as prop, chart re-fetches
 */
import { useEffect, useRef, useState, useCallback } from 'react'
import { apiService } from '../services/apiService'
import dayjs from 'dayjs'

// ── Synthetic candle generator (offline fallback) ─────────────────────────────
function generateCandles(symbol, interval, count = 120) {
  const isCrypto = !symbol.includes('-BSE') && symbol.endsWith('USDT')
  const seeds = {
    BTCUSDT:60000, ETHUSDT:3200, SOLUSDT:180, BNBUSDT:580, XRPUSDT:0.62,
    RELIANCE:2800, TCS:3900, HDFCBANK:1650, INFY:1780, ICICIBANK:1100,
    default: isCrypto ? 100 : 1000,
  }
  const basePrice = seeds[symbol] ?? seeds.default
  const intervalMs = { '1m':60, '5m':300, '1h':3600, '4h':14400, '1d':86400 }[interval] ?? 3600
  const now = Math.floor(Date.now() / 1000)
  const startTs = now - count * intervalMs
  const candles = []
  let price = basePrice
  for (let i = 0; i < count; i++) {
    const time   = startTs + i * intervalMs
    const drift  = (Math.random() - 0.499) * basePrice * 0.008
    const range  = basePrice * (0.003 + Math.random() * 0.012)
    const open   = price
    const close  = price + drift
    const high   = Math.max(open, close) + Math.random() * range * 0.6
    const low    = Math.min(open, close) - Math.random() * range * 0.6
    const volume = Math.floor(basePrice * (800 + Math.random() * 2400))
    candles.push({ time, open:+open.toFixed(4), high:+high.toFixed(4), low:+low.toFixed(4), close:+close.toFixed(4), volume })
    price = close
  }
  return candles
}

// ── Price formatter ────────────────────────────────────────────────────────────
function fmtPrice(v, symbol) {
  if (!v && v !== 0) return '—'
  const isCrypto = !symbol.includes('-BSE') && symbol.endsWith('USDT')
  if (!isCrypto) return `₹${v.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  if (v >= 1000)  return `$${v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  if (v >= 1)     return `$${v.toFixed(4)}`
  return `$${v.toFixed(6)}`
}

function fmtTime(ts, interval) {
  const d = dayjs.unix(ts)
  if (interval === '1d') return d.format('MMM D')
  if (interval === '4h' || interval === '1h') return d.format('MMM D HH:mm')
  return d.format('HH:mm')
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════
export default function CandlestickChart({ symbol, type = 'crypto', interval = '1h', height = 340 }) {
  const wrapRef      = useRef(null)
  const [candles,    setCandles]    = useState([])
  const [loading,    setLoading]    = useState(true)
  const [error,      setError]      = useState(false)
  const [width,      setWidth]      = useState(800)
  const [hover,      setHover]      = useState(null)   // { x, y, candle }
  const [crossX,     setCrossX]     = useState(null)
  const [crossY,     setCrossY]     = useState(null)
  const [usingFallback, setUsingFallback] = useState(false)

  // ── Responsive width ───────────────────────────────────────────────────────
  useEffect(() => {
    if (!wrapRef.current) return
    const ro = new ResizeObserver(entries => {
      const w = entries[0]?.contentRect.width
      if (w && w > 0) setWidth(Math.floor(w))
    })
    ro.observe(wrapRef.current)
    setWidth(Math.floor(wrapRef.current.clientWidth || 800))
    return () => ro.disconnect()
  }, [])

  // ── Fetch candles ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (!symbol) return
    setLoading(true); setError(false); setHover(null)
    const endpoint = type === 'crypto' ? '/api/crypto/candles' : '/api/stocks/candles'
    apiService.get(`${endpoint}?symbol=${symbol}&interval=${interval}&limit=150`)
      .then(r => {
        const raw = r.data || []
        if (!raw.length) throw new Error('empty')
        const parsed = raw.map(c => ({
          time:   typeof c.timestamp === 'number'
                    ? Math.floor(c.timestamp > 1e12 ? c.timestamp/1000 : c.timestamp)
                    : Math.floor(new Date(c.timestamp).getTime() / 1000),
          open:   +c.open, high: +c.high, low: +c.low, close: +c.close,
          volume: +(c.volume ?? 0),
        })).filter(c => c.time && c.high && c.low).sort((a, b) => a.time - b.time)
        if (!parsed.length) throw new Error('empty after parse')
        setCandles(parsed)
        setUsingFallback(false)
      })
      .catch(() => {
        // Realistic synthetic fallback
        setCandles(generateCandles(symbol, interval, 130))
        setUsingFallback(true)
      })
      .finally(() => setLoading(false))
  }, [symbol, type, interval])

  // ── Layout constants ────────────────────────────────────────────────────────
  const PAD_LEFT   = 10
  const PAD_RIGHT  = 72   // Y-axis labels
  const PAD_TOP    = 16
  const PAD_BOTTOM = 36   // X-axis labels
  const VOL_HEIGHT = 44   // volume pane height
  const CHART_H    = height - PAD_TOP - PAD_BOTTOM - VOL_HEIGHT - 8
  const chartW     = width - PAD_LEFT - PAD_RIGHT

  // ── Derived geometry ────────────────────────────────────────────────────────
  const { yMin, yMax, xScale, yScale, candleW, volScale, yTicks, xTicks } = (() => {
    if (!candles.length) return { yMin:0, yMax:1, xScale:()=>0, yScale:()=>0, candleW:6, volScale:()=>0, yTicks:[], xTicks:[] }

    const visibleCount = Math.min(candles.length, Math.floor(chartW / 7))
    const visible      = candles.slice(-visibleCount)

    const allHigh = visible.map(c => c.high)
    const allLow  = visible.map(c => c.low)
    const rawMin  = Math.min(...allLow)
    const rawMax  = Math.max(...allHigh)
    const pad     = (rawMax - rawMin) * 0.06
    const yMin    = rawMin - pad
    const yMax    = rawMax + pad

    const candleW = Math.max(3, Math.min(18, Math.floor(chartW / visibleCount) - 1))
    const gap     = Math.floor((chartW - visibleCount * candleW) / Math.max(visibleCount - 1, 1))

    const xScale = (i) => PAD_LEFT + i * (candleW + gap) + candleW / 2
    const yScale = (p) => PAD_TOP + CHART_H - ((p - yMin) / (yMax - yMin)) * CHART_H

    const maxVol = Math.max(...visible.map(c => c.volume), 1)
    const volScale = (v) => VOL_HEIGHT * (v / maxVol)

    // Y axis ticks: 6 levels
    const yStep  = (yMax - yMin) / 5
    const yTicks = [0,1,2,3,4,5].map(i => yMin + yStep * i)

    // X axis ticks: up to 8 labels
    const step   = Math.max(1, Math.floor(visibleCount / 8))
    const xTicks = visible
      .map((c, i) => ({ i, time: c.time }))
      .filter((_, i) => i % step === 0 || i === visible.length - 1)

    return { yMin, yMax, xScale, yScale, candleW, volScale, yTicks, xTicks, visible }
  })()

  const visibleCount = Math.min(candles.length, Math.floor(chartW / 7))
  const visible      = candles.slice(-visibleCount)

  // ── Mouse interaction ──────────────────────────────────────────────────────
  const handleMouseMove = useCallback((e) => {
    if (!visible.length || !wrapRef.current) return
    const rect = wrapRef.current.getBoundingClientRect()
    const mx   = e.clientX - rect.left
    const my   = e.clientY - rect.top
    setCrossX(mx); setCrossY(my)

    // Find nearest candle
    let nearest = null, minDist = Infinity
    visible.forEach((c, i) => {
      const cx = xScale(i)
      const d  = Math.abs(mx - cx)
      if (d < minDist) { minDist = d; nearest = { i, c } }
    })
    if (nearest && minDist < candleW * 2.5) {
      setHover({ x: xScale(nearest.i), y: my, candle: nearest.c })
    } else {
      setHover(null)
    }
  }, [visible, xScale, candleW])

  const handleMouseLeave = () => { setHover(null); setCrossX(null); setCrossY(null) }

  // ── Tooltip position ────────────────────────────────────────────────────────
  const tooltipX = hover ? (hover.x > width * 0.6 ? hover.x - 218 : hover.x + 16) : 0
  const tooltipY = hover ? Math.max(PAD_TOP, Math.min(hover.y - 60, height - 180)) : 0

  if (loading) {
    return (
      <div className="flex items-center justify-center" style={{ height }}>
        <div className="flex flex-col items-center gap-2 text-slate-600">
          <div className="w-7 h-7 border-2 border-cyan-500/30 border-t-cyan-400 rounded-full animate-spin"/>
          <span className="text-xs font-mono">Loading chart…</span>
        </div>
      </div>
    )
  }

  const volTop = PAD_TOP + CHART_H + 12

  return (
    <div ref={wrapRef} className="relative w-full select-none" style={{ height }}>
      {/* Fallback badge */}
      {usingFallback && (
        <div className="absolute top-2 left-3 z-20 flex items-center gap-1.5 px-2.5 py-1
                        rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-400 text-[10px] font-mono">
          ⚡ Demo data — connect backend for live candles
        </div>
      )}

      <svg
        width={width}
        height={height}
        className="absolute inset-0 cursor-crosshair"
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
        style={{ fontFamily: 'JetBrains Mono, monospace' }}
      >
        {/* ── Background ─────────────────────────────── */}
        <rect width={width} height={height} fill="transparent"/>

        {/* ── Horizontal grid lines ──────────────────── */}
        {yTicks.map((p, i) => {
          const y = yScale(p)
          if (y < PAD_TOP || y > PAD_TOP + CHART_H) return null
          return (
            <g key={i}>
              <line x1={PAD_LEFT} y1={y} x2={width - PAD_RIGHT} y2={y}
                    stroke="#ffffff05" strokeWidth="1"/>
            </g>
          )
        })}

        {/* ── Volume pane ────────────────────────────── */}
        {visible.map((c, i) => {
          const x   = xScale(i)
          const vh  = volScale(c.volume)
          const isB = c.close >= c.open
          return (
            <rect key={`vol-${i}`}
              x={x - candleW / 2} y={volTop + VOL_HEIGHT - vh}
              width={candleW} height={Math.max(1, vh)}
              fill={isB ? '#10b98128' : '#fb718528'}
              rx="1"
            />
          )
        })}

        {/* ── Volume pane divider ─────────────────────── */}
        <line x1={PAD_LEFT} y1={volTop} x2={width - PAD_RIGHT} y2={volTop}
              stroke="#ffffff08" strokeWidth="1" strokeDasharray="3 4"/>

        {/* ── Candles ─────────────────────────────────── */}
        {visible.map((c, i) => {
          const x    = xScale(i)
          const isB  = c.close >= c.open
          const col  = isB ? '#34d399' : '#fb7185'
          const colD = isB ? '#34d39955' : '#fb718555'
          const yO   = yScale(c.open)
          const yC   = yScale(c.close)
          const yH   = yScale(c.high)
          const yL   = yScale(c.low)
          const bodyTop = Math.min(yO, yC)
          const bodyH   = Math.max(1.5, Math.abs(yO - yC))
          const isHov   = hover?.candle.time === c.time

          return (
            <g key={`c-${i}`} opacity={isHov ? 1 : 0.92}>
              {/* Wick */}
              <line x1={x} y1={yH} x2={x} y2={yL}
                    stroke={isHov ? col : colD} strokeWidth={isHov ? 1.5 : 1}/>
              {/* Body */}
              <rect
                x={x - candleW / 2 + 0.5}
                y={bodyTop}
                width={Math.max(1, candleW - 1)}
                height={bodyH}
                fill={isB ? col : col}
                fillOpacity={isHov ? 1 : 0.85}
                stroke={col}
                strokeWidth={0.5}
                rx="0.5"
              />
            </g>
          )
        })}

        {/* ── Y-axis labels ───────────────────────────── */}
        {yTicks.map((p, i) => {
          const y = yScale(p)
          if (y < PAD_TOP || y > PAD_TOP + CHART_H) return null
          return (
            <text key={`yt-${i}`}
              x={width - PAD_RIGHT + 6} y={y + 4}
              fontSize="9" fill="#475569" textAnchor="start">
              {fmtPrice(p, symbol)}
            </text>
          )
        })}

        {/* ── X-axis labels ───────────────────────────── */}
        {xTicks.map(({ i, time }) => {
          const x = xScale(i)
          return (
            <text key={`xt-${i}`}
              x={x} y={height - PAD_BOTTOM + 16}
              fontSize="9" fill="#475569" textAnchor="middle">
              {fmtTime(time, interval)}
            </text>
          )
        })}

        {/* ── Crosshair ───────────────────────────────── */}
        {crossX !== null && crossY !== null && (
          <g>
            {/* Vertical line */}
            <line x1={crossX} y1={PAD_TOP} x2={crossX} y2={height - PAD_BOTTOM}
                  stroke="#ffffff18" strokeWidth="1" strokeDasharray="3 3"/>
            {/* Horizontal line */}
            <line x1={PAD_LEFT} y1={crossY} x2={width - PAD_RIGHT} y2={crossY}
                  stroke="#ffffff18" strokeWidth="1" strokeDasharray="3 3"/>
            {/* Price label on right axis */}
            {crossY > PAD_TOP && crossY < PAD_TOP + CHART_H && (() => {
              const p = yMin + ((PAD_TOP + CHART_H - crossY) / CHART_H) * (yMax - yMin)
              return (
                <g>
                  <rect x={width - PAD_RIGHT} y={crossY - 9} width={PAD_RIGHT - 2} height={17}
                        fill="#1e293b" rx="3"/>
                  <text x={width - PAD_RIGHT + 4} y={crossY + 4}
                        fontSize="9" fill="#e2e8f0" fontWeight="600">
                    {fmtPrice(p, symbol)}
                  </text>
                </g>
              )
            })()}
          </g>
        )}
      </svg>

      {/* ── OHLC Tooltip ──────────────────────────────────────────────────── */}
      {hover?.candle && (
        <div
          className="absolute z-30 pointer-events-none"
          style={{ left: tooltipX, top: tooltipY }}
        >
          <div className="rounded-xl border border-white/[0.14] bg-[#0d1e35]/95 backdrop-blur-sm
                          shadow-2xl shadow-black/60 p-3.5 w-[210px]">
            {/* Header: date + time */}
            <div className="flex items-center justify-between mb-2.5 pb-2 border-b border-white/[0.08]">
              <span className="text-[11px] font-mono font-bold text-slate-200">
                {dayjs.unix(hover.candle.time).format('DD MMM YYYY')}
              </span>
              <span className="text-[10px] font-mono text-slate-400">
                {dayjs.unix(hover.candle.time).format('HH:mm')}
              </span>
            </div>

            {/* OHLC rows */}
            <div className="space-y-1.5">
              {[
                ['Open',  hover.candle.open,  'text-slate-200'],
                ['High',  hover.candle.high,  'text-emerald-400'],
                ['Low',   hover.candle.low,   'text-red-400'],
                ['Close', hover.candle.close, hover.candle.close >= hover.candle.open ? 'text-emerald-400' : 'text-red-400'],
              ].map(([label, val, cls]) => (
                <div key={label} className="flex items-center justify-between">
                  <span className="text-[10px] text-slate-500 font-mono w-10">{label}</span>
                  <span className={`text-[11px] font-mono font-bold tabular-nums ${cls}`}>
                    {fmtPrice(val, symbol)}
                  </span>
                </div>
              ))}
            </div>

            {/* Change % */}
            <div className="mt-2.5 pt-2 border-t border-white/[0.07] flex items-center justify-between">
              <span className="text-[10px] text-slate-500 font-mono">Change</span>
              {(() => {
                const chg = ((hover.candle.close - hover.candle.open) / hover.candle.open) * 100
                const up  = chg >= 0
                return (
                  <span className={`text-[11px] font-mono font-bold ${up ? 'text-emerald-400' : 'text-red-400'}`}>
                    {up ? '▲' : '▼'} {Math.abs(chg).toFixed(2)}%
                  </span>
                )
              })()}
            </div>

            {/* Volume */}
            {hover.candle.volume > 0 && (
              <div className="flex items-center justify-between mt-1">
                <span className="text-[10px] text-slate-500 font-mono">Volume</span>
                <span className="text-[11px] font-mono text-slate-300">
                  {hover.candle.volume >= 1e9
                    ? `${(hover.candle.volume/1e9).toFixed(2)}B`
                    : hover.candle.volume >= 1e6
                    ? `${(hover.candle.volume/1e6).toFixed(2)}M`
                    : hover.candle.volume >= 1e3
                    ? `${(hover.candle.volume/1e3).toFixed(1)}K`
                    : hover.candle.volume.toFixed(0)}
                </span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}