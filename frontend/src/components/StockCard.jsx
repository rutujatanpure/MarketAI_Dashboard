import { motion } from 'framer-motion'
import clsx from 'clsx'

// ── Stock metadata ─────────────────────────────────────────────────────────────
const STOCK_META = {
  // NSE
  RELIANCE:   { name: 'Reliance',   ticker: 'RELIANCE', exchange: 'NSE', sector: 'Energy',  color: '#f59e0b' },
  INFY:       { name: 'Infosys',    ticker: 'INFY',     exchange: 'NSE', sector: 'IT',      color: '#06b6d4' },
  TCS:        { name: 'TCS',        ticker: 'TCS',      exchange: 'NSE', sector: 'IT',      color: '#06b6d4' },
  HDFCBANK:   { name: 'HDFC Bank',  ticker: 'HDFC',     exchange: 'NSE', sector: 'Finance', color: '#8b5cf6' },
  ICICIBANK:  { name: 'ICICI Bank', ticker: 'ICICI',    exchange: 'NSE', sector: 'Finance', color: '#8b5cf6' },
  WIPRO:      { name: 'Wipro',      ticker: 'WIPRO',    exchange: 'NSE', sector: 'IT',      color: '#06b6d4' },
  SBIN:       { name: 'SBI',        ticker: 'SBIN',     exchange: 'NSE', sector: 'Finance', color: '#8b5cf6' },
  ADANIENT:   { name: 'Adani Ent',  ticker: 'ADANI',    exchange: 'NSE', sector: 'Infra',   color: '#10b981' },
  KOTAKBANK:  { name: 'Kotak Bank', ticker: 'KOTAK',    exchange: 'NSE', sector: 'Finance', color: '#8b5cf6' },
  LT:         { name: 'L&T',        ticker: 'LT',       exchange: 'NSE', sector: 'Infra',   color: '#10b981' },
  ITC:        { name: 'ITC',        ticker: 'ITC',      exchange: 'NSE', sector: 'FMCG',    color: '#ec4899' },
  BAJFINANCE: { name: 'Bajaj Fin',  ticker: 'BAJFIN',   exchange: 'NSE', sector: 'Finance', color: '#8b5cf6' },
  MARUTI:     { name: 'Maruti',     ticker: 'MARUTI',   exchange: 'NSE', sector: 'Auto',    color: '#f97316' },
  SUNPHARMA:  { name: 'Sun Pharma', ticker: 'SUNPH',    exchange: 'NSE', sector: 'Pharma',  color: '#34d399' },
  HINDUNILVR: { name: 'HUL',        ticker: 'HUL',      exchange: 'NSE', sector: 'FMCG',    color: '#ec4899' },
  // BSE
  'RELIANCE-BSE': { name: 'Reliance',   ticker: 'RELIANCE', exchange: 'BSE', sector: 'Energy',  color: '#f59e0b' },
  'INFY-BSE':     { name: 'Infosys',    ticker: 'INFY',     exchange: 'BSE', sector: 'IT',      color: '#06b6d4' },
  'TCS-BSE':      { name: 'TCS',        ticker: 'TCS',      exchange: 'BSE', sector: 'IT',      color: '#06b6d4' },
  'WIPRO-BSE':    { name: 'Wipro',      ticker: 'WIPRO',    exchange: 'BSE', sector: 'IT',      color: '#06b6d4' },
  'SBIN-BSE':     { name: 'SBI',        ticker: 'SBIN',     exchange: 'BSE', sector: 'Finance', color: '#8b5cf6' },
}

const SECTOR_BG = {
  IT:      'bg-cyan-500/10 text-cyan-400',
  Finance: 'bg-purple-500/10 text-purple-400',
  Energy:  'bg-amber-500/10 text-amber-400',
  Infra:   'bg-emerald-500/10 text-emerald-400',
  FMCG:    'bg-pink-500/10 text-pink-400',
  Auto:    'bg-orange-500/10 text-orange-400',
  Pharma:  'bg-teal-500/10 text-teal-400',
}

// ── Stock icon: colored box with 2–3 letter ticker abbreviation ───────────────
function StockIcon({ ticker, color, size = 36 }) {
  const letter = (ticker || '??').slice(0, 2).toUpperCase()
  return (
    <div
      className="rounded-lg flex items-center justify-center font-bold text-xs flex-shrink-0"
      style={{
        width: size, height: size,
        background: `${color}20`,
        border: `1px solid ${color}35`,
        color,
        fontFamily: 'monospace',
        letterSpacing: '-0.03em',
      }}>
      {letter}
    </div>
  )
}

// ── Mini sparkline ────────────────────────────────────────────────────────────
function Spark({ up }) {
  const c  = up ? '#34d399' : '#fb7185'
  const pts = up
    ? '0,28 16,24 32,18 48,14 64,9 80,5'
    : '0,5 16,9 32,14 48,18 64,23 80,28'
  return (
    <svg width="80" height="32" viewBox="0 0 80 32" preserveAspectRatio="none"
         className="opacity-80">
      <defs>
        <linearGradient id={`sg-${up}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={c} stopOpacity=".18" />
          <stop offset="100%" stopColor={c} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polyline points={pts} fill="none" stroke={c} strokeWidth="1.8"
        strokeLinecap="round" strokeLinejoin="round" />
      <polygon points={`${pts} 80,32 0,32`} fill={`url(#sg-${up})`} />
    </svg>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
export default function StockCard({ data, onClick, selected, marketOpen = false }) {
  const sym    = data?.symbol ?? ''
  const meta   = STOCK_META[sym] ?? {
    name:     sym.replace('-BSE', '') || 'Stock',
    ticker:   sym.replace('-BSE', '').slice(0, 5),
    exchange: sym.includes('-BSE') ? 'BSE' : 'NSE',
    sector:   'Stock',
    color:    '#64748b',
  }

  const price  = data?.price ?? 0
  const change = data?.changePercent ?? data?.priceChange ?? data?.pChange ?? 0
  const isUp   = change >= 0
  const hasPrice = price > 0

  // Format Indian rupee price
  const fmtPrice = (p) =>
    p > 0
      ? `₹${p.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
      : '₹—'

  return (
    <motion.div
      whileHover={{ y: -3, scale: 1.01 }}
      whileTap={{ scale: 0.97 }}
      onClick={() => onClick?.(sym)}
      className={clsx(
        'relative rounded-2xl p-4 cursor-pointer transition-all duration-200 overflow-hidden',
        'bg-[#0d1a2d] border',
        selected
          ? 'border-cyan-500/50 shadow-[0_0_24px_#06b6d425]'
          : 'border-white/[0.07] hover:border-white/[0.14]'
      )}>

      {/* Top color accent */}
      <div className="absolute top-0 left-0 right-0 h-[2px] rounded-t-2xl"
           style={{ background: `linear-gradient(90deg, ${meta.color}80, ${meta.color}20)` }} />

      {/* Header row: icon + name + change badge */}
      <div className="flex items-start justify-between gap-2 mb-3">
        <div className="flex items-center gap-2 min-w-0">
          <StockIcon ticker={meta.ticker} color={meta.color} size={34} />
          <div className="min-w-0">
            <p className="text-sm font-bold text-white leading-tight truncate">{meta.name}</p>
            <div className="flex items-center gap-1.5 mt-0.5">
              <span className="text-[9px] font-mono text-slate-400">{meta.ticker}</span>
              <span className="text-slate-600 text-[9px]">·</span>
              <span className={clsx(
                'text-[9px] font-bold font-mono px-1.5 py-0.5 rounded-md border',
                meta.exchange === 'NSE'
                  ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20'
                  : 'text-teal-400 bg-teal-500/10 border-teal-500/20'
              )}>{meta.exchange}</span>
            </div>
          </div>
        </div>

        {/* Change badge */}
        <span className={clsx(
          'flex-shrink-0 text-[11px] font-mono font-bold px-2 py-1 rounded-lg',
          isUp ? 'bg-emerald-500/15 text-emerald-400' : 'bg-red-500/15 text-red-400'
        )}>
          {isUp ? '▲' : '▼'} {Math.abs(change).toFixed(2)}%
        </span>
      </div>

      {/* Price section */}
      {hasPrice ? (
        <div className="mb-3">
          <p className={clsx(
            'text-xl font-bold tabular-nums leading-none',
            marketOpen ? 'text-white' : 'text-slate-400'
          )}>
            {fmtPrice(price)}
          </p>
          <div className="flex items-center justify-between mt-1.5">
            <span className={clsx('text-xs font-mono', isUp ? 'text-emerald-400' : 'text-red-400')}>
              {isUp ? '+' : ''}₹{(data?.change ?? data?.priceChange ?? 0).toFixed(2)}
            </span>
            <span className="text-[10px] font-mono text-slate-500">
              H:{(data?.high24h ?? data?.high ?? 0).toFixed(0)} · L:{(data?.low24h ?? data?.low ?? 0).toFixed(0)}
            </span>
          </div>
        </div>
      ) : (
        <div className="mb-3">
          <p className="text-xl font-bold text-slate-500 tabular-nums">₹—</p>
          <p className="text-[10px] font-mono text-slate-600 mt-1">
            {marketOpen ? 'Awaiting price…' : 'Market closed'}
          </p>
        </div>
      )}

      {/* Sparkline + market status */}
      <div className="flex items-end justify-between">
        <div className="flex flex-col gap-1">
          <span className={clsx(
            'text-[9px] font-bold px-1.5 py-0.5 rounded-md w-fit',
            SECTOR_BG[meta.sector] ?? 'bg-slate-500/10 text-slate-400'
          )}>
            {meta.sector}
          </span>
          <span className={clsx(
            'text-[9px] font-mono px-1.5 py-0.5 rounded-md border w-fit',
            marketOpen
              ? 'text-emerald-400 bg-emerald-500/8 border-emerald-500/20'
              : 'text-slate-500 bg-slate-800/40 border-slate-700/40'
          )}>
            {marketOpen ? '● Live' : '○ Closed'}
          </span>
        </div>
        <Spark up={isUp} />
      </div>
    </motion.div>
  )
}