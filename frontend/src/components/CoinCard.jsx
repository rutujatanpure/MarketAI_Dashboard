import { motion } from 'framer-motion'
import clsx from 'clsx'

// ── Coin metadata ─────────────────────────────────────────────────────────────
const COIN_META = {
  BTCUSDT:   { name: 'Bitcoin',    symbol: 'BTC',  color: '#f59e0b' },
  ETHUSDT:   { name: 'Ethereum',   symbol: 'ETH',  color: '#8b5cf6' },
  SOLUSDT:   { name: 'Solana',     symbol: 'SOL',  color: '#22d3ee' },
  BNBUSDT:   { name: 'BNB',        symbol: 'BNB',  color: '#fbbf24' },
  XRPUSDT:   { name: 'Ripple',     symbol: 'XRP',  color: '#60a5fa' },
  ADAUSDT:   { name: 'Cardano',    symbol: 'ADA',  color: '#34d399' },
  DOGEUSDT:  { name: 'Dogecoin',   symbol: 'DOGE', color: '#fbbf24' },
  AVAXUSDT:  { name: 'Avalanche',  symbol: 'AVAX', color: '#ef4444' },
  DOTUSDT:   { name: 'Polkadot',   symbol: 'DOT',  color: '#e879f9' },
  MATICUSDT: { name: 'Polygon',    symbol: 'MATIC',color: '#a78bfa' },
  LINKUSDT:  { name: 'Chainlink',  symbol: 'LINK', color: '#2563eb' },
  UNIUSDT:   { name: 'Uniswap',    symbol: 'UNI',  color: '#ff6eb4' },
  LTCUSDT:   { name: 'Litecoin',   symbol: 'LTC',  color: '#94a3b8' },
  ATOMUSDT:  { name: 'Cosmos',     symbol: 'ATOM', color: '#6366f1' },
  NEARUSDT:  { name: 'NEAR',       symbol: 'NEAR', color: '#00c08b' },
  APTUSDT:   { name: 'Aptos',      symbol: 'APT',  color: '#26d0ce' },
  ARBUSDT:   { name: 'Arbitrum',   symbol: 'ARB',  color: '#28a0f0' },
  OPUSDT:    { name: 'Optimism',   symbol: 'OP',   color: '#ff0420' },
  INJUSDT:   { name: 'Injective',  symbol: 'INJ',  color: '#00aaff' },
  SUIUSDT:   { name: 'Sui',        symbol: 'SUI',  color: '#4da2ff' },
}

// ── Coin icon: colored circle with symbol letters ─────────────────────────────
function CoinIcon({ symbol, color, size = 34 }) {
  const label = (symbol || '??').slice(0, 3).toUpperCase()
  return (
    <div
      className="rounded-full flex items-center justify-center font-bold flex-shrink-0"
      style={{
        width: size, height: size,
        background: `${color}20`,
        border: `1.5px solid ${color}40`,
        color,
        fontSize: label.length > 2 ? 9 : 11,
        fontFamily: 'monospace',
        letterSpacing: '-0.04em',
      }}>
      {label}
    </div>
  )
}

// ── Mini sparkline using real price history ────────────────────────────────────
function Sparkline({ history, isUp }) {
  const prices = (history ?? [])
    .slice(-20)
    .map(h => h?.price ?? h?.close ?? 0)
    .filter(p => p > 0)

  if (prices.length < 3) {
    // Fallback static sparkline
    const c  = isUp ? '#34d399' : '#fb7185'
    const pts = isUp ? '0,28 16,22 32,16 48,11 64,7 80,4' : '0,4 16,8 32,14 48,19 64,24 80,28'
    return (
      <svg width="80" height="32" viewBox="0 0 80 32" preserveAspectRatio="none">
        <polyline points={pts} fill="none" stroke={c} strokeWidth="1.8"
          strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    )
  }

  const min = Math.min(...prices)
  const max = Math.max(...prices)
  const range = max - min || 1
  const W = 80, H = 30
  const pts = prices.map((p, i) => {
    const x = (i / (prices.length - 1)) * W
    const y = H - ((p - min) / range) * (H - 4) - 2
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')

  const color = isUp ? '#34d399' : '#fb7185'
  return (
    <svg width="80" height="32" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none">
      <defs>
        <linearGradient id={`cg-${isUp}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity=".2" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.8"
        strokeLinecap="round" strokeLinejoin="round" />
      <polygon points={`${pts} ${W},${H} 0,${H}`} fill={`url(#cg-${isUp})`} />
    </svg>
  )
}

// ── Loading skeleton ──────────────────────────────────────────────────────────
function CoinCardSkeleton() {
  return (
    <div className="rounded-2xl bg-[#0d1a2d] border border-white/[0.06] p-4">
      <div className="flex items-center gap-2 mb-3">
        <div className="w-9 h-9 rounded-full bg-white/[0.05] animate-pulse" />
        <div>
          <div className="h-3 w-16 bg-white/[0.05] rounded animate-pulse mb-1.5" />
          <div className="h-2 w-10 bg-white/[0.04] rounded animate-pulse" />
        </div>
      </div>
      <div className="h-6 w-28 bg-white/[0.05] rounded animate-pulse mb-1.5" />
      <div className="h-2 w-20 bg-white/[0.04] rounded animate-pulse" />
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
export default function CoinCard({ data, history, onClick, selected }) {
  if (!data || !data.symbol) return <CoinCardSkeleton />

  const rawSym = data.symbol
  const meta   = COIN_META[rawSym] ?? {
    name:   rawSym.replace('USDT', ''),
    symbol: rawSym.replace('USDT', '').slice(0, 4),
    color:  '#64748b',
  }

  const price  = data.price ?? 0
  const change = data.priceChange ?? data.changePercent ?? 0
  const isUp   = change >= 0

  // Format price: crypto needs more decimals for small coins
  const fmtPrice = (p) => {
    if (!p || p === 0) return '$—'
    if (p >= 1000)  return `$${p.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    if (p >= 1)     return `$${p.toFixed(4)}`
    return `$${p.toFixed(6)}`
  }

  return (
    <motion.div
      whileHover={{ y: -3, scale: 1.01 }}
      whileTap={{ scale: 0.97 }}
      onClick={() => onClick?.(rawSym)}
      className={clsx(
        'relative rounded-2xl p-4 cursor-pointer transition-all duration-200 overflow-hidden',
        'bg-[#0d1a2d] border',
        selected
          ? 'border-cyan-500/50 shadow-[0_0_24px_#06b6d425]'
          : 'border-white/[0.07] hover:border-white/[0.14]'
      )}>

      {/* Top color accent bar */}
      <div className="absolute top-0 left-0 right-0 h-[2px] rounded-t-2xl"
           style={{ background: `linear-gradient(90deg, ${meta.color}90, ${meta.color}20)` }} />

      {/* Header: icon + name + change */}
      <div className="flex items-start justify-between gap-2 mb-3">
        <div className="flex items-center gap-2 min-w-0">
          <CoinIcon symbol={meta.symbol} color={meta.color} size={34} />
          <div className="min-w-0">
            <p className="text-sm font-bold text-white leading-tight truncate">{meta.name}</p>
            <p className="text-[10px] font-mono text-slate-400 mt-0.5">
              {meta.symbol}<span className="text-slate-600">/USDT</span>
            </p>
          </div>
        </div>
        <span className={clsx(
          'flex-shrink-0 text-[11px] font-mono font-bold px-2 py-1 rounded-lg',
          isUp ? 'bg-emerald-500/15 text-emerald-400' : 'bg-red-500/15 text-red-400'
        )}>
          {isUp ? '▲' : '▼'} {Math.abs(change).toFixed(2)}%
        </span>
      </div>

      {/* Price */}
      <div className="mb-3">
        <p className="text-xl font-bold text-white tabular-nums leading-none">
          {fmtPrice(price)}
        </p>
        <div className="flex items-center justify-between mt-1.5">
          <span className={clsx('text-xs font-mono', isUp ? 'text-emerald-400' : 'text-red-400')}>
            {isUp ? '+' : ''}{change.toFixed(2)}%
          </span>
          {data.volume != null && (
            <span className="text-[10px] font-mono text-slate-500">
              Vol: {data.volume >= 1e9
                ? `${(data.volume/1e9).toFixed(1)}B`
                : data.volume >= 1e6
                ? `${(data.volume/1e6).toFixed(1)}M`
                : data.volume.toFixed(0)}
            </span>
          )}
        </div>
      </div>

      {/* Sparkline + 24h range */}
      <div className="flex items-end justify-between">
        <div>
          {(data.high24h || data.high) && (
            <p className="text-[10px] font-mono text-slate-500">
              H:{fmtPrice(data.high24h ?? data.high ?? 0)} · L:{fmtPrice(data.low24h ?? data.low ?? 0)}
            </p>
          )}
          <div className="flex items-center gap-1 mt-1">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span className="text-[9px] font-mono text-emerald-400">24/7</span>
          </div>
        </div>
        <Sparkline history={history} isUp={isUp} />
      </div>
    </motion.div>
  )
}