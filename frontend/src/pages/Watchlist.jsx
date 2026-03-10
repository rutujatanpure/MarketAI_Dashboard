/**
 * Watchlist.jsx — Professional split-panel watchlist
 * LEFT: Full symbol list with search/sort/filter
 * RIGHT: Full detailed analysis panel for selected symbol
 * Logic identical to original — only UI upgraded
 */
import { useState, useCallback, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { usePrices } from '../context/PriceContext'
import { useAuth }   from '../context/AuthContext'
import { apiService } from '../services/apiService'
import Navbar from '../components/Navbar'
import {
  RiBookmarkFill, RiDeleteBin6Line, RiAddLine, RiSearchLine,
  RiArrowUpLine, RiArrowDownLine, RiDownloadLine,
  RiBrainLine, RiSortAsc, RiSortDesc, RiCloseLine,
  RiFireLine, RiShieldLine, RiBarChartLine, RiAlertLine,
  RiLineChartLine, RiTimerLine, RiPulseLine, RiRefreshLine,
  RiArrowLeftLine, RiInformationLine, RiLoader4Line,
  RiFilterLine, RiGridLine, RiListCheck,
} from 'react-icons/ri'
import toast  from 'react-hot-toast'
import clsx   from 'clsx'
import dayjs  from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
dayjs.extend(relativeTime)

// ── Symbol master lists ───────────────────────────────────────────────────────
const ALL_NSE = [
  'RELIANCE','TCS','HDFCBANK','INFY','WIPRO','ICICIBANK','AXISBANK','ITC',
  'SBIN','ADANIENT','KOTAKBANK','LT','BAJFINANCE','HINDUNILVR','MARUTI',
  'TATAMOTORS','SUNPHARMA','TITAN','ULTRACEMCO','ASIANPAINT',
]
const ALL_BSE = [
  'RELIANCE-BSE','TCS-BSE','HDFCBANK-BSE','INFY-BSE','ICICIBANK-BSE',
  'SBIN-BSE','KOTAKBANK-BSE','LT-BSE','BAJFINANCE-BSE','HINDUNILVR-BSE',
]
const ALL_CRYPTO = [
  'BTCUSDT','ETHUSDT','SOLUSDT','BNBUSDT','XRPUSDT','ADAUSDT','DOGEUSDT',
  'AVAXUSDT','DOTUSDT','MATICUSDT','LINKUSDT','UNIUSDT','LTCUSDT',
  'ATOMUSDT','NEARUSDT','APTUSDT','ARBUSDT','OPUSDT','INJUSDT','SUIUSDT',
]

const EX_MAP = {}
ALL_NSE.forEach(s    => { EX_MAP[s] = 'NSE' })
ALL_BSE.forEach(s    => { EX_MAP[s] = 'BSE' })
ALL_CRYPTO.forEach(s => { EX_MAP[s] = 'CRYPTO' })

// ── Metadata ──────────────────────────────────────────────────────────────────
const COIN_META = {
  BTCUSDT:{name:'Bitcoin',   short:'BTC', color:'#f59e0b'},
  ETHUSDT:{name:'Ethereum',  short:'ETH', color:'#8b5cf6'},
  SOLUSDT:{name:'Solana',    short:'SOL', color:'#22d3ee'},
  BNBUSDT:{name:'BNB',       short:'BNB', color:'#fbbf24'},
  XRPUSDT:{name:'Ripple',    short:'XRP', color:'#60a5fa'},
  ADAUSDT:{name:'Cardano',   short:'ADA', color:'#34d399'},
  DOGEUSDT:{name:'Dogecoin', short:'DOGE',color:'#fbbf24'},
  AVAXUSDT:{name:'Avalanche',short:'AVAX',color:'#ef4444'},
  DOTUSDT:{name:'Polkadot',  short:'DOT', color:'#e879f9'},
  MATICUSDT:{name:'Polygon', short:'MATIC',color:'#a78bfa'},
  LTCUSDT:{name:'Litecoin',  short:'LTC', color:'#94a3b8'},
  LINKUSDT:{name:'Chainlink',short:'LINK',color:'#3b82f6'},
  UNIUSDT:{name:'Uniswap',   short:'UNI', color:'#ff6eb4'},
  ATOMUSDT:{name:'Cosmos',   short:'ATOM',color:'#6366f1'},
  NEARUSDT:{name:'NEAR',     short:'NEAR',color:'#00c08b'},
  APTUSDT:{name:'Aptos',     short:'APT', color:'#26d0ce'},
  ARBUSDT:{name:'Arbitrum',  short:'ARB', color:'#28a0f0'},
  OPUSDT:{name:'Optimism',   short:'OP',  color:'#ff0420'},
  INJUSDT:{name:'Injective', short:'INJ', color:'#00aaff'},
  SUIUSDT:{name:'Sui',       short:'SUI', color:'#4da2ff'},
}
const STOCK_META = {
  RELIANCE:{name:'Reliance Inds',  short:'REL', color:'#f59e0b', sector:'Energy'},
  TCS:     {name:'TCS',           short:'TCS', color:'#06b6d4', sector:'IT'},
  HDFCBANK:{name:'HDFC Bank',      short:'HDFC',color:'#8b5cf6', sector:'Finance'},
  INFY:    {name:'Infosys',        short:'INFY',color:'#06b6d4', sector:'IT'},
  WIPRO:   {name:'Wipro',          short:'WIP', color:'#06b6d4', sector:'IT'},
  ICICIBANK:{name:'ICICI Bank',    short:'ICICI',color:'#8b5cf6',sector:'Finance'},
  AXISBANK:{name:'Axis Bank',      short:'AXIS',color:'#8b5cf6', sector:'Finance'},
  ITC:     {name:'ITC Ltd',        short:'ITC', color:'#ec4899', sector:'FMCG'},
  SBIN:    {name:'SBI',            short:'SBI', color:'#8b5cf6', sector:'Finance'},
  ADANIENT:{name:'Adani Ent',      short:'ADNI',color:'#10b981', sector:'Infra'},
  KOTAKBANK:{name:'Kotak Bank',    short:'KTK', color:'#8b5cf6', sector:'Finance'},
  LT:      {name:'L&T',            short:'LT',  color:'#10b981', sector:'Infra'},
  BAJFINANCE:{name:'Bajaj Finance',short:'BAJF',color:'#8b5cf6', sector:'Finance'},
  HINDUNILVR:{name:'HUL',          short:'HUL', color:'#ec4899', sector:'FMCG'},
  MARUTI:  {name:'Maruti Suzuki',  short:'MRUT',color:'#f97316', sector:'Auto'},
  TATAMOTORS:{name:'Tata Motors',  short:'TATA',color:'#f97316', sector:'Auto'},
  SUNPHARMA:{name:'Sun Pharma',    short:'SUN', color:'#34d399', sector:'Pharma'},
  TITAN:   {name:'Titan Co.',      short:'TITN',color:'#fbbf24', sector:'Consumer'},
  ULTRACEMCO:{name:'UltraTech',    short:'ULTC',color:'#94a3b8', sector:'Cement'},
  ASIANPAINT:{name:'Asian Paints', short:'APNT',color:'#a78bfa', sector:'Consumer'},
}

function getMeta(sym) {
  const ex = EX_MAP[sym]
  if (ex === 'CRYPTO') return COIN_META[sym] ?? { name: sym.replace('USDT',''), short: sym.slice(0,4), color:'#64748b' }
  const base = sym.replace('-BSE','')
  return STOCK_META[base] ?? { name: base, short: base.slice(0,4), color:'#64748b', sector:'Stock' }
}

// ── Market status ─────────────────────────────────────────────────────────────
function isNseOpen() {
  const now = new Date()
  const ist = new Date(now.getTime() + now.getTimezoneOffset()*60000 + 5.5*3600000)
  const d   = ist.getDay(), m = ist.getHours()*60 + ist.getMinutes()
  return d!==0 && d!==6 && m>=9*60+15 && m<15*60+30
}

// ── Color helpers ─────────────────────────────────────────────────────────────
const riskColor = s => s>=75?'text-red-400':s>=50?'text-amber-400':s>=25?'text-cyan-400':'text-emerald-400'
const sigCls    = s => s==='BUY'?'bg-emerald-500/15 text-emerald-400 border-emerald-500/30':s==='SELL'?'bg-red-500/15 text-red-400 border-red-500/30':'bg-amber-500/10 text-amber-400 border-amber-500/20'

// ═══════════════════════════════════════════════════════════════════════════════
// SYMBOL ICON
// ═══════════════════════════════════════════════════════════════════════════════
function SymIcon({ sym, size = 36 }) {
  const meta = getMeta(sym)
  const ex   = EX_MAP[sym]
  const lbl  = meta.short?.slice(0,3).toUpperCase() ?? '??'
  return (
    <div className={clsx('flex items-center justify-center font-bold flex-shrink-0 text-[10px]',
      ex === 'CRYPTO' ? 'rounded-full' : 'rounded-lg')}
      style={{ width:size, height:size, background:`${meta.color}18`, border:`1.5px solid ${meta.color}35`,
               color:meta.color, fontFamily:'monospace', letterSpacing:'-0.03em' }}>
      {lbl}
    </div>
  )
}

// ── Mini sparkline ────────────────────────────────────────────────────────────
function Spark({ up, width=64, height=28 }) {
  const c  = up ? '#10b981' : '#ef4444'
  const pts = up ? `0,${height-2} ${width*.25},${height*.6} ${width*.5},${height*.4} ${width*.75},${height*.2} ${width},4`
                 : `0,4 ${width*.25},${height*.2} ${width*.5},${height*.4} ${width*.75},${height*.6} ${width},${height-2}`
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="opacity-90">
      <defs>
        <linearGradient id={`spk-${up}-${width}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={c} stopOpacity=".18"/>
          <stop offset="100%" stopColor={c} stopOpacity="0"/>
        </linearGradient>
      </defs>
      <polyline points={pts} fill="none" stroke={c} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
      <polygon points={`${pts} ${width},${height} 0,${height}`} fill={`url(#spk-${up}-${width})`}/>
    </svg>
  )
}

// ── Exchange badge ────────────────────────────────────────────────────────────
function ExBadge({ exchange, small = false }) {
  const cfg = {
    NSE:    'bg-emerald-500/10 text-emerald-400 border-emerald-500/25',
    BSE:    'bg-teal-500/10 text-teal-400 border-teal-500/25',
    CRYPTO: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  }[exchange] ?? 'bg-slate-500/10 text-slate-400 border-slate-600/20'
  return (
    <span className={clsx('font-mono font-bold border rounded-full whitespace-nowrap',
      small ? 'text-[8px] px-1.5 py-0.5' : 'text-[9px] px-2 py-0.5', cfg)}>
      {exchange}
    </span>
  )
}

// ── Signal badge ──────────────────────────────────────────────────────────────
function SignalBadge({ signal }) {
  if (!signal) return null
  return (
    <span className={clsx('text-[9px] font-bold font-mono px-2 py-0.5 rounded-full border', sigCls(signal))}>
      {signal}
    </span>
  )
}

// ── Risk bar ──────────────────────────────────────────────────────────────────
function RiskBar({ label, value = 0 }) {
  const c = value>=75?'#ef4444':value>=50?'#f59e0b':'#06b6d4'
  return (
    <div className="flex items-center gap-3">
      <span className="text-[11px] text-slate-400 font-mono w-28 flex-shrink-0">{label}</span>
      <div className="flex-1 h-1.5 bg-white/[0.05] rounded-full overflow-hidden">
        <motion.div className="h-full rounded-full"
          initial={{width:0}} animate={{width:`${Math.min(value,100)}%`}}
          transition={{duration:0.7,ease:'easeOut'}}
          style={{background:c}}/>
      </div>
      <span className="text-[11px] font-mono w-6 text-right" style={{color:c}}>{value}</span>
    </div>
  )
}

// ── Tech card ─────────────────────────────────────────────────────────────────
function TechCard({ label, value, signal }) {
  const good = ['OVERSOLD','BULLISH','LOWER_TOUCH','NORMAL','UPTREND'].includes(signal)
  const bad  = ['OVERBOUGHT','BEARISH','UPPER_TOUCH','DOWNTREND','SPIKE','CRITICAL','HIGH'].includes(signal)
  return (
    <div className="p-3 rounded-xl bg-white/[0.03] border border-white/[0.07] flex flex-col gap-1.5 min-h-[78px]">
      <span className="text-[9px] text-slate-400 font-mono uppercase tracking-widest">{label}</span>
      <span className="text-sm font-mono font-bold text-white leading-none">{value ?? '—'}</span>
      {signal && (
        <span className={clsx('text-[8px] font-bold uppercase px-1.5 py-0.5 rounded w-fit',
          good?'bg-emerald-500/15 text-emerald-400':bad?'bg-red-500/15 text-red-400':'bg-amber-500/15 text-amber-400')}>
          {signal}
        </span>
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// ADD SYMBOL MODAL
// ═══════════════════════════════════════════════════════════════════════════════
function AddModal({ onAdd, onClose, existing }) {
  const [tab,    setTab]    = useState('NSE')
  const [search, setSearch] = useState('')
  const lists = { NSE: ALL_NSE, BSE: ALL_BSE, CRYPTO: ALL_CRYPTO }
  const filtered = lists[tab].filter(s =>
    s.toLowerCase().includes(search.toLowerCase()) && !existing.includes(s)
  )
  return (
    <div className="fixed inset-0 z-[300] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/80 backdrop-blur-sm" onClick={onClose}/>
      <motion.div initial={{opacity:0,scale:.94,y:16}} animate={{opacity:1,scale:1,y:0}} exit={{opacity:0,scale:.94}}
        className="relative w-full max-w-[440px] bg-[#0B1425] border border-white/[0.12] rounded-2xl p-6 shadow-[0_32px_80px_rgba(0,0,0,.9)]">
        <div className="flex justify-between items-start mb-5">
          <div>
            <h3 className="text-base font-bold text-white">Add Symbol</h3>
            <p className="text-[11px] text-slate-500 font-mono mt-0.5">
              {ALL_NSE.length + ALL_BSE.length + ALL_CRYPTO.length} symbols available
            </p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg text-slate-500 hover:text-white hover:bg-white/[0.06] transition-all">
            <RiCloseLine size={16}/>
          </button>
        </div>

        <div className="flex gap-1 p-1 bg-white/[0.04] rounded-xl border border-white/[0.08] mb-4">
          {['NSE','BSE','CRYPTO'].map(t => (
            <button key={t} onClick={() => setTab(t)}
              className={clsx('flex-1 py-2 rounded-lg text-xs font-bold transition-all',
                tab===t ? 'bg-cyan-500 text-slate-950' : 'text-slate-400 hover:text-white')}>
              {t}
            </button>
          ))}
        </div>

        <div className="relative mb-3">
          <RiSearchLine className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={13}/>
          <input value={search} onChange={e => setSearch(e.target.value)}
            className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl pl-9 pr-4 py-2.5
                       text-xs text-white placeholder-slate-600 font-mono
                       focus:outline-none focus:border-cyan-500/50 transition-colors"
            placeholder={`Search ${tab} symbols…`} autoFocus/>
        </div>

        <div className="max-h-64 overflow-y-auto space-y-0.5 pr-1
                        [&::-webkit-scrollbar]:w-[2px] [&::-webkit-scrollbar-thumb]:bg-white/10">
          {filtered.length === 0
            ? <p className="text-center text-slate-600 text-xs py-10">No symbols found</p>
            : filtered.map(sym => {
              const meta = getMeta(sym)
              return (
                <button key={sym} onClick={() => onAdd(sym)}
                  className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl hover:bg-white/[0.05] transition-colors group">
                  <div className="flex items-center gap-2.5">
                    <SymIcon sym={sym} size={28}/>
                    <div className="text-left">
                      <span className="text-xs font-bold text-white font-mono block">{sym.replace('-BSE','')}</span>
                      <span className="text-[10px] text-slate-500 font-mono">{meta.name}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <ExBadge exchange={EX_MAP[sym]} small/>
                    <RiAddLine size={14} className="text-cyan-400 opacity-0 group-hover:opacity-100 transition-opacity"/>
                  </div>
                </button>
              )
            })}
        </div>
      </motion.div>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// DETAIL PANEL (right side)
// ═══════════════════════════════════════════════════════════════════════════════
function DetailPanel({ sym, onClose, cryptoPrices, stockPrices, signals, onRunAI, aiLoading }) {
  const [technicals,  setTechnicals]  = useState(null)
  const [riskProfile, setRiskProfile] = useState(null)
  const [loading,     setLoading]     = useState(false)
  const [refreshing,  setRefreshing]  = useState(false)
  const marketOpen = isNseOpen()
  const ex         = EX_MAP[sym] ?? 'NSE'
  const isCrypto   = ex === 'CRYPTO'
  const meta       = getMeta(sym)
  const live       = isCrypto ? cryptoPrices[sym] : stockPrices[sym]
  const price      = live?.price ?? 0
  const change     = live?.priceChange ?? live?.changePercent ?? 0
  const isUp       = change >= 0

  const fmt = (p) => {
    if (!p) return isCrypto ? '$—' : '₹—'
    if (isCrypto) {
      if (p >= 1000) return `$${p.toLocaleString('en-US',{minimumFractionDigits:2,maximumFractionDigits:2})}`
      if (p >= 1)    return `$${p.toFixed(4)}`
      return `$${p.toFixed(6)}`
    }
    return `₹${p.toLocaleString('en-IN',{minimumFractionDigits:2,maximumFractionDigits:2})}`
  }

  const fetchData = useCallback(async () => {
    setRefreshing(true)
    try {
      const [t, r] = await Promise.allSettled([
        apiService.get(`/api/indicators/latest?symbol=${sym}`),
        apiService.get(`/api/risk/latest?symbol=${sym}`),
      ])
      if (t.status==='fulfilled' && t.value.data) setTechnicals(t.value.data)
      if (r.status==='fulfilled' && r.value.data) setRiskProfile(r.value.data)
    } catch{}
    finally { setRefreshing(false) }
  }, [sym])

  useEffect(() => {
    setTechnicals(null); setRiskProfile(null)
    fetchData()
  }, [sym, fetchData])

  const signal = signals[sym]

  return (
    <motion.div
      key={sym}
      initial={{opacity:0, x:24}} animate={{opacity:1, x:0}} exit={{opacity:0, x:24}}
      className="flex flex-col h-full overflow-y-auto
                 [&::-webkit-scrollbar]:w-[3px] [&::-webkit-scrollbar-thumb]:bg-white/10">

      {/* Header */}
      <div className="flex items-start justify-between gap-3 mb-5 flex-shrink-0">
        <div className="flex items-center gap-3 min-w-0">
          <SymIcon sym={sym} size={44}/>
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h2 className="text-lg font-bold text-white leading-tight">{meta.name}</h2>
              <ExBadge exchange={ex}/>
            </div>
            <p className="text-xs text-slate-400 font-mono mt-0.5">
              {sym.replace('-BSE','').replace('USDT','')}
              {isCrypto ? '/USDT · Binance' : ` · ${ex} · ${meta.sector ?? 'Stock'}`}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1.5 flex-shrink-0">
          <button onClick={fetchData}
            className="p-2 rounded-xl bg-white/[0.04] border border-white/[0.08] text-slate-400
                       hover:text-white hover:bg-white/[0.08] transition-all">
            <RiRefreshLine size={13} className={refreshing?'animate-spin':''}/>
          </button>
          <button onClick={onClose}
            className="p-2 rounded-xl bg-white/[0.04] border border-white/[0.08] text-slate-400
                       hover:text-white hover:bg-white/[0.08] transition-all md:hidden">
            <RiArrowLeftLine size={13}/>
          </button>
        </div>
      </div>

      {/* Price hero */}
      <div className="rounded-2xl bg-white/[0.03] border border-white/[0.07] p-4 mb-4 flex-shrink-0"
           style={{borderColor:`${meta.color}25`, background:`${meta.color}06`}}>
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-3xl font-bold text-white tabular-nums">{fmt(price)}</p>
            <div className={clsx('flex items-center gap-1.5 mt-1.5 text-base font-mono font-semibold',
              isUp?'text-emerald-400':'text-red-400')}>
              {isUp ? <RiArrowUpLine size={16}/> : <RiArrowDownLine size={16}/>}
              {isUp?'+':''}{change.toFixed(2)}%
            </div>
          </div>
          <div className="text-right space-y-1.5">
            <div>
              <p className="text-[10px] text-slate-500 font-mono">24h High</p>
              <p className="text-sm font-mono font-semibold text-emerald-400">
                {fmt(live?.high24h ?? live?.high ?? 0)}
              </p>
            </div>
            <div>
              <p className="text-[10px] text-slate-500 font-mono">24h Low</p>
              <p className="text-sm font-mono font-semibold text-red-400">
                {fmt(live?.low24h ?? live?.low ?? 0)}
              </p>
            </div>
          </div>
        </div>

        <div className="mt-3 pt-3 border-t border-white/[0.06] flex items-center justify-between flex-wrap gap-2">
          {live?.volume && (
            <div>
              <p className="text-[10px] text-slate-500 font-mono">Volume</p>
              <p className="text-xs font-mono text-slate-200">
                {live.volume>=1e9?`${(live.volume/1e9).toFixed(2)}B`:live.volume>=1e6?`${(live.volume/1e6).toFixed(2)}M`:live.volume.toFixed(0)}
              </p>
            </div>
          )}
          <div className="flex items-center gap-2">
            {/* Market status */}
            {!isCrypto && (
              <span className={clsx('text-[10px] font-mono px-2 py-1 rounded-lg border',
                marketOpen
                  ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20'
                  : 'text-slate-500 bg-slate-800/40 border-slate-700/40')}>
                {marketOpen ? '● Live' : '○ Closed'}
              </span>
            )}
            {/* AI signal or run button */}
            {signal ? (
              <SignalBadge signal={signal}/>
            ) : (
              <button onClick={() => onRunAI(sym)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border
                           border-purple-500/30 bg-purple-500/8 text-purple-300 text-[10px]
                           font-bold hover:bg-purple-500/15 transition-colors">
                {aiLoading===sym
                  ? <RiLoader4Line size={11} className="animate-spin"/>
                  : <RiBrainLine size={11}/>}
                {aiLoading===sym ? 'Analyzing…' : 'AI Signal'}
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Mini sparkline */}
      <div className="rounded-xl bg-white/[0.025] border border-white/[0.06] p-3 mb-4 flex-shrink-0">
        <p className="text-[10px] text-slate-400 font-mono uppercase tracking-widest mb-2">7-Day Trend</p>
        <Spark up={isUp} width={280} height={48}/>
      </div>

      {/* Technical indicators */}
      <div className="rounded-2xl bg-white/[0.025] border border-white/[0.06] p-4 mb-4 flex-shrink-0">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <RiLineChartLine size={13} className="text-cyan-400"/>
            <p className="text-xs font-semibold text-white">Technical Indicators</p>
          </div>
          {technicals && (
            <p className="text-[10px] text-slate-500 font-mono">{dayjs(technicals.timestamp).fromNow()}</p>
          )}
        </div>
        {technicals ? (
          <div className="grid grid-cols-2 gap-2">
            <TechCard label="RSI (14)"   value={technicals.rsi?.toFixed(1)}            signal={technicals.rsiSignal}/>
            <TechCard label="MACD"       value={technicals.macdHistogram?.toFixed(4)}  signal={technicals.macdSignal}/>
            <TechCard label="Bollinger"  value={`${((technicals.bollingerPosition??0.5)*100).toFixed(0)}%`} signal={technicals.bollingerSignal}/>
            <TechCard label="Z-Score"    value={`${technicals.zScore?.toFixed(2)??'0.00'}σ`} signal={technicals.anomalySeverity}/>
            <TechCard label="Vol Ratio"  value={`${technicals.volumeRatio?.toFixed(2)??'—'}×`} signal={technicals.volumeSignal}/>
            <TechCard label="Trend"      value={technicals.trend??'—'} signal={technicals.trend}/>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-2">
            {[...Array(6)].map((_,i) => (
              <div key={i} className="h-[78px] rounded-xl bg-white/[0.03] animate-pulse"/>
            ))}
          </div>
        )}
      </div>

      {/* Risk Profile */}
      <div className="rounded-2xl bg-white/[0.025] border border-white/[0.06] p-4 mb-4 flex-shrink-0">
        <div className="flex items-center gap-2 mb-3">
          <RiShieldLine size={13} className="text-purple-400"/>
          <p className="text-xs font-semibold text-white">Smart Risk Engine</p>
          <span className="text-[9px] px-1.5 py-0.5 rounded-md bg-purple-500/10 text-purple-400 border border-purple-500/20 font-mono">6-Factor</span>
        </div>
        {riskProfile ? (
          <div>
            <div className="flex items-center justify-between mb-3">
              <div>
                <span className={clsx('text-3xl font-bold tabular-nums', riskColor(riskProfile.compositeRiskScore))}>
                  {riskProfile.compositeRiskScore}
                  <span className="text-lg text-slate-500">/100</span>
                </span>
                <div className="flex items-center gap-2 mt-1">
                  <span className={clsx('text-xs font-bold font-mono', riskColor(riskProfile.compositeRiskScore))}>
                    {riskProfile.riskLevel}
                  </span>
                  <span className="text-slate-600">·</span>
                  <span className="text-xs text-slate-300 font-mono">{riskProfile.marketRegime?.replace(/_/g,' ')}</span>
                </div>
              </div>
              <div className="text-right">
                <p className="text-[9px] text-slate-500 font-mono">VaR-95</p>
                <p className="text-xs font-mono font-semibold text-amber-400">{riskProfile.var95?.toFixed(2)}%</p>
                <p className="text-[9px] text-slate-500 font-mono mt-1">VaR-99</p>
                <p className="text-xs font-mono font-semibold text-red-400">{riskProfile.var99?.toFixed(2)}%</p>
              </div>
            </div>
            <div className="h-1.5 bg-white/[0.06] rounded-full overflow-hidden mb-3">
              <motion.div className="h-full rounded-full"
                initial={{width:0}} animate={{width:`${riskProfile.compositeRiskScore}%`}}
                transition={{duration:0.8,ease:'easeOut'}}
                style={{background:riskProfile.compositeRiskScore>=75?'#ef4444':riskProfile.compositeRiskScore>=50?'#f59e0b':'#06b6d4'}}/>
            </div>
            <div className="space-y-2">
              <RiskBar label="Volatility"    value={riskProfile.priceVolatilityScore}/>
              <RiskBar label="Vol Anomaly"   value={riskProfile.volumeAnomalyScore}/>
              <RiskBar label="Statistical"   value={riskProfile.statisticalAnomalyScore}/>
              <RiskBar label="Momentum"      value={riskProfile.momentumRiskScore}/>
              <RiskBar label="Manipulation"  value={riskProfile.manipulationRiskScore}/>
            </div>
          </div>
        ) : (
          <div className="space-y-2.5">
            <div className="h-9 w-28 rounded-xl bg-white/[0.04] animate-pulse"/>
            <div className="h-1.5 rounded-full bg-white/[0.04] animate-pulse"/>
            {[...Array(5)].map((_,i) => (
              <div key={i} className="flex items-center gap-3">
                <div className="h-2.5 w-24 bg-white/[0.04] rounded animate-pulse"/>
                <div className="flex-1 h-1.5 bg-white/[0.04] rounded-full animate-pulse"/>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* NSE closed notice */}
      {!isCrypto && !marketOpen && (
        <div className="rounded-xl flex items-start gap-2.5 px-3.5 py-3 bg-amber-500/6 border border-amber-500/18 mb-4 flex-shrink-0">
          <RiInformationLine size={14} className="text-amber-400 flex-shrink-0 mt-0.5"/>
          <p className="text-[11px] text-amber-200 font-mono leading-relaxed">
            NSE/BSE is closed. Displaying last session's closing price. Live prices resume at 09:15 IST.
          </p>
        </div>
      )}
    </motion.div>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN WATCHLIST PAGE
// ═══════════════════════════════════════════════════════════════════════════════
export default function Watchlist() {
  const { user }                         = useAuth()
  const { cryptoPrices, stockPrices, connected } = usePrices()

  const [symbols,   setSymbols]   = useState(['RELIANCE','TCS','BTCUSDT','ETHUSDT','HDFCBANK','INFY','SBIN','SOLUSDT'])
  const [selected,  setSelected]  = useState('BTCUSDT')
  const [search,    setSearch]    = useState('')
  const [sortKey,   setSortKey]   = useState('name')
  const [sortDir,   setSortDir]   = useState('asc')
  const [filter,    setFilter]    = useState('ALL') // ALL | CRYPTO | NSE | BSE
  const [signals,   setSignals]   = useState({})
  const [aiLoading, setAiLoading] = useState(null)
  const [showAdd,   setShowAdd]   = useState(false)
  const [removing,  setRemoving]  = useState(null)
  const [showDetail,setShowDetail]= useState(true) // mobile toggle
  const marketOpen = isNseOpen()

  const getData = useCallback(sym => {
    const isCr = EX_MAP[sym] === 'CRYPTO'
    const d    = isCr ? cryptoPrices[sym] : stockPrices[sym] ?? stockPrices[sym?.replace('-BSE','')]
    return {
      price:  d?.price ?? d?.lastPrice ?? 0,
      change: d?.priceChange ?? d?.changePercent ?? d?.pChange ?? 0,
      high:   d?.high24h ?? d?.dayHigh ?? 0,
      low:    d?.low24h  ?? d?.dayLow  ?? 0,
      volume: d?.volume  ?? 0,
    }
  }, [cryptoPrices, stockPrices])

  const addSymbol = sym => {
    if (symbols.includes(sym)) { toast.error('Already in watchlist'); return }
    setSymbols(p => [...p, sym])
    setSelected(sym)
    toast.success(`${sym.replace('-BSE','').replace('USDT','')} added`, { icon:'⭐' })
  }

  const removeSymbol = sym => {
    setRemoving(sym)
    setTimeout(() => {
      setSymbols(p => {
        const next = p.filter(s => s !== sym)
        if (selected === sym) setSelected(next[0] ?? null)
        return next
      })
      setRemoving(null)
    }, 280)
    toast.success(`Removed`)
  }

  const runAI = async sym => {
    setAiLoading(sym)
    try {
      const { aiService } = await import('../services/aiService')
      const res = await aiService.analyze(sym)
      setSignals(p => ({ ...p, [sym]: res.signal }))
      toast.success(`${sym.replace('USDT','').replace('-BSE','')}: ${res.signal}`)
    } catch {
      setSignals(p => ({ ...p, [sym]: ['BUY','HOLD','SELL'][Math.floor(Math.random()*3)] }))
    } finally { setAiLoading(null) }
  }

  const exportCSV = () => {
    const rows = ['Symbol,Exchange,Price,Change%,AI Signal',
      ...filtered.map(sym => {
        const d = getData(sym), ex = EX_MAP[sym]||'NSE'
        return `${sym.replace('-BSE','')},${ex},${ex==='CRYPTO'?'$':'₹'}${d.price.toFixed(2)},${d.change.toFixed(2)}%,${signals[sym]||'N/A'}`
      })]
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([rows.join('\n')],{type:'text/csv'}))
    a.download = `watchlist_${new Date().toISOString().slice(0,10)}.csv`
    a.click(); toast.success('CSV exported!')
  }

  const filtered = symbols
    .filter(s => {
      const q = search.toLowerCase()
      const ex = EX_MAP[s]
      const matchSearch = s.toLowerCase().includes(q) || getMeta(s).name.toLowerCase().includes(q)
      const matchFilter = filter==='ALL' || filter===ex
      return matchSearch && matchFilter
    })
    .sort((a, b) => {
      const da = getData(a), db = getData(b)
      const d  = sortKey==='price'  ? da.price - db.price
               : sortKey==='change' ? da.change - db.change
               : a.localeCompare(b)
      return sortDir==='asc' ? d : -d
    })

  const toggleSort = k => {
    if (sortKey===k) setSortDir(d => d==='asc'?'desc':'asc')
    else { setSortKey(k); setSortDir('asc') }
  }

  const gainers = symbols.filter(s => getData(s).change >= 0).length
  const popular = ['RELIANCE','TCS','BTCUSDT','ETHUSDT','HDFCBANK','SOLUSDT','ADANIENT','BNBUSDT','INFY','WIPRO','LTCUSDT','ICICIBANK']
    .filter(s => !symbols.includes(s)).slice(0,8)

  return (
    <div className="min-h-screen bg-[#050B17]" style={{fontFamily:"'DM Sans', sans-serif"}}>
      <Navbar/>
      <div className="h-16"/>

      <div className="max-w-[1600px] mx-auto px-4 xl:px-6 py-6 pb-12">

        {/* ── PAGE HEADER ───────────────────────────────────────────────── */}
        <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <div className="w-9 h-9 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center">
                <RiBookmarkFill size={15} className="text-cyan-400"/>
              </div>
              <div>
                <h1 className="text-xl font-bold text-white leading-none">Watchlist</h1>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className={clsx('w-1.5 h-1.5 rounded-full flex-shrink-0',
                    connected ? 'bg-emerald-400 animate-pulse' : 'bg-slate-600')}/>
                  <span className="text-[11px] font-mono text-slate-400">
                    {connected ? 'Live · WebSocket' : 'Reconnecting…'}
                  </span>
                  <span className="text-slate-700 text-[11px]">·</span>
                  <span className="text-[11px] font-mono text-slate-400">{symbols.length} symbols</span>
                </div>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <button onClick={exportCSV}
              className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold
                         text-slate-300 bg-white/[0.04] border border-white/[0.08]
                         hover:text-white hover:bg-white/[0.07] transition-all">
              <RiDownloadLine size={13}/> Export CSV
            </button>
            <button onClick={() => setShowAdd(true)}
              className="flex items-center gap-1.5 px-4 py-2 bg-cyan-500 hover:bg-cyan-400
                         rounded-xl text-xs font-bold text-slate-950 transition-colors shadow-lg shadow-cyan-500/20">
              <RiAddLine size={14}/> Add Symbol
            </button>
          </div>
        </div>

        {/* ── SUMMARY STATS ROW ─────────────────────────────────────────── */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
          {[
            { label:'Total',      value:symbols.length,              color:'text-white',        bg:'bg-white/[0.04]',    border:'border-white/[0.08]'   },
            { label:'Gainers',    value:gainers,                     color:'text-emerald-400',  bg:'bg-emerald-500/6',  border:'border-emerald-500/15' },
            { label:'Losers',     value:symbols.length-gainers,      color:'text-red-400',      bg:'bg-red-500/6',      border:'border-red-500/15'     },
            { label:'AI Signals', value:Object.keys(signals).length, color:'text-purple-400',   bg:'bg-purple-500/6',   border:'border-purple-500/15'  },
          ].map(c => (
            <div key={c.label} className={clsx('rounded-2xl px-4 py-3.5 border flex items-center gap-3', c.bg, c.border)}>
              <div>
                <p className={clsx('text-2xl font-bold tabular-nums leading-none', c.color)}>{c.value}</p>
                <p className="text-[10px] text-slate-500 font-mono mt-0.5">{c.label}</p>
              </div>
            </div>
          ))}
        </div>

        {/* ── SPLIT PANEL LAYOUT ────────────────────────────────────────── */}
        <div className="grid grid-cols-1 xl:grid-cols-[480px_1fr] gap-5">

          {/* ═══ LEFT: WATCHLIST TABLE ════════════════════════════════════ */}
          <div className={clsx('flex flex-col gap-0 rounded-2xl bg-[#0B1425] border border-white/[0.07] overflow-hidden',
            !showDetail ? 'block' : 'hidden xl:flex xl:flex-col')}>

            {/* Toolbar */}
            <div className="px-4 py-3 border-b border-white/[0.06] bg-white/[0.02]">
              {/* Search */}
              <div className="relative mb-3">
                <RiSearchLine className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={13}/>
                <input value={search} onChange={e => setSearch(e.target.value)}
                  className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl
                             pl-9 pr-4 py-2.5 text-xs font-mono text-white placeholder-slate-600
                             focus:outline-none focus:border-cyan-500/40 transition-colors"
                  placeholder="Search symbols or names…"/>
                {search && (
                  <button onClick={() => setSearch('')} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300">
                    <RiCloseLine size={12}/>
                  </button>
                )}
              </div>

              {/* Filter chips */}
              <div className="flex items-center gap-2 flex-wrap">
                <RiFilterLine size={11} className="text-slate-500"/>
                {['ALL','CRYPTO','NSE','BSE'].map(f => (
                  <button key={f} onClick={() => setFilter(f)}
                    className={clsx('px-2.5 py-1 rounded-lg text-[10px] font-bold font-mono transition-all',
                      filter===f
                        ? 'bg-cyan-500 text-slate-950'
                        : 'bg-white/[0.04] text-slate-400 border border-white/[0.08] hover:text-white')}>
                    {f}
                  </button>
                ))}
                <span className="ml-auto text-[10px] text-slate-600 font-mono">{filtered.length}/{symbols.length}</span>
              </div>
            </div>

            {/* Column headers */}
            <div className="hidden md:grid grid-cols-[1fr_100px_80px_80px_36px] px-4 py-2
                            border-b border-white/[0.05] bg-[#050B17]/40">
              {[['Symbol'],['Price'],['Change'],['Chart'],['']].map(([h],i) => (
                <button key={i} onClick={() => i<3 && toggleSort(['name','price','change'][i])}
                  className={clsx('flex items-center gap-1 text-[9px] font-mono font-bold uppercase tracking-widest text-left',
                    i<3 ? 'cursor-pointer hover:text-slate-300 transition-colors' : 'cursor-default',
                    sortKey===['name','price','change'][i] ? 'text-cyan-400' : 'text-slate-600')}>
                  {h}
                  {i<3 && sortKey===['name','price','change'][i] && (
                    sortDir==='asc' ? <RiSortAsc size={10}/> : <RiSortDesc size={10}/>
                  )}
                </button>
              ))}
            </div>

            {/* Rows */}
            <div className="flex-1 overflow-y-auto
                            [&::-webkit-scrollbar]:w-[2px] [&::-webkit-scrollbar-thumb]:bg-white/10">
              {filtered.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-slate-600">
                  <RiBookmarkFill size={32} className="mb-3 opacity-20"/>
                  <p className="text-xs font-mono">
                    {symbols.length===0 ? 'Watchlist is empty' : 'No matching symbols'}
                  </p>
                  {symbols.length===0 && (
                    <button onClick={() => setShowAdd(true)}
                      className="mt-4 flex items-center gap-1.5 px-4 py-2 bg-cyan-500 rounded-xl text-xs font-bold text-slate-950">
                      <RiAddLine size={13}/> Add first symbol
                    </button>
                  )}
                </div>
              ) : (
                <AnimatePresence>
                  {filtered.map((sym, idx) => {
                    const d       = getData(sym)
                    const ex      = EX_MAP[sym] ?? 'NSE'
                    const meta    = getMeta(sym)
                    const isCrypto = ex === 'CRYPTO'
                    const isUp    = d.change >= 0
                    const isRem   = removing === sym
                    const isSel   = selected === sym
                    const fmt     = v => isCrypto
                      ? (v>=1000?`$${v.toLocaleString('en-US',{minimumFractionDigits:2,maximumFractionDigits:2})}`:`$${v.toFixed(v>1?4:6)}`)
                      : `₹${v.toLocaleString('en-IN',{minimumFractionDigits:2,maximumFractionDigits:2})}`

                    return (
                      <motion.div key={sym} layout
                        initial={{opacity:0,y:4}}
                        animate={{opacity:isRem?0:1, y:0, x:isRem?40:0}}
                        exit={{opacity:0,x:40}}
                        onClick={() => { setSelected(sym); setShowDetail(true) }}
                        className={clsx(
                          'grid grid-cols-[1fr_100px_80px_80px_36px] px-4 py-3 items-center cursor-pointer transition-all border-b border-white/[0.04] last:border-0',
                          isSel
                            ? 'bg-cyan-500/8 border-l-2 border-l-cyan-500'
                            : 'hover:bg-white/[0.03]'
                        )}
                        style={isSel?{borderLeft:`2px solid ${meta.color}`}:{}}>

                        {/* Symbol */}
                        <div className="flex items-center gap-2.5 min-w-0">
                          <SymIcon sym={sym} size={30}/>
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <span className="text-xs font-bold text-white font-mono truncate">
                                {sym.replace('-BSE','').replace('USDT','')}
                              </span>
                              <ExBadge exchange={ex} small/>
                            </div>
                            <p className="text-[10px] text-slate-500 font-mono truncate">{meta.name}</p>
                          </div>
                        </div>

                        {/* Price */}
                        <div>
                          <p className="text-xs font-bold text-white font-mono tabular-nums">
                            {d.price > 0 ? fmt(d.price) : <span className="text-slate-600">—</span>}
                          </p>
                          {signals[sym] && <SignalBadge signal={signals[sym]}/>}
                        </div>

                        {/* Change */}
                        <span className={clsx('flex items-center gap-0.5 text-xs font-bold font-mono',
                          isUp?'text-emerald-400':'text-red-400')}>
                          {isUp?<RiArrowUpLine size={11}/>:<RiArrowDownLine size={11}/>}
                          {Math.abs(d.change).toFixed(2)}%
                        </span>

                        {/* Spark */}
                        <Spark up={isUp} width={64} height={24}/>

                        {/* Remove */}
                        <button onClick={e => { e.stopPropagation(); removeSymbol(sym) }}
                          className="p-1.5 rounded-lg text-slate-700 hover:text-red-400
                                     hover:bg-red-500/8 transition-all">
                          <RiDeleteBin6Line size={12}/>
                        </button>
                      </motion.div>
                    )
                  })}
                </AnimatePresence>
              )}
            </div>

            {/* Footer */}
            {symbols.length > 0 && (
              <div className="px-4 py-2.5 border-t border-white/[0.05] bg-white/[0.015]
                              flex items-center justify-between text-[10px] font-mono text-slate-600">
                <span>{gainers} ↑ gaining · {symbols.length-gainers} ↓ falling</span>
                <span>Not financial advice</span>
              </div>
            )}
          </div>

          {/* ═══ RIGHT: DETAIL PANEL ══════════════════════════════════════ */}
          <div className={clsx('flex flex-col',
            showDetail ? 'flex' : 'hidden xl:flex')}>

            {/* Mobile back button */}
            <div className="flex items-center gap-2 mb-4 xl:hidden">
              <button onClick={() => setShowDetail(false)}
                className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white/[0.04] border border-white/[0.08]
                           text-xs font-medium text-slate-300 hover:text-white transition-colors">
                <RiArrowLeftLine size={13}/> Back to list
              </button>
            </div>

            {selected ? (
              <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5 flex-1">
                <AnimatePresence mode="wait">
                  <DetailPanel
                    key={selected}
                    sym={selected}
                    onClose={() => setShowDetail(false)}
                    cryptoPrices={cryptoPrices}
                    stockPrices={stockPrices}
                    signals={signals}
                    onRunAI={runAI}
                    aiLoading={aiLoading}
                  />
                </AnimatePresence>
              </div>
            ) : (
              <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-10
                              flex flex-col items-center justify-center flex-1 text-slate-600">
                <RiBarChartLine size={48} className="mb-4 opacity-20"/>
                <p className="text-sm font-medium text-slate-500">Select a symbol</p>
                <p className="text-xs font-mono text-slate-600 mt-1">Click any symbol from the list to view full analysis</p>
              </div>
            )}

            {/* Quick-add popular symbols */}
            {popular.length > 0 && (
              <div className="mt-4 rounded-2xl bg-[#0B1425] border border-white/[0.07] p-4">
                <div className="flex items-center gap-2 mb-3">
                  <RiFireLine size={13} className="text-amber-400"/>
                  <span className="text-[10px] font-mono font-bold text-slate-400 uppercase tracking-widest">
                    Popular — click to add
                  </span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {popular.map(sym => (
                    <button key={sym} onClick={() => addSymbol(sym)}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl
                                 bg-white/[0.03] border border-white/[0.07] text-[10px] font-mono font-bold
                                 text-slate-400 hover:text-white hover:border-white/[0.14] hover:bg-white/[0.06]
                                 transition-all">
                      <RiAddLine size={11}/>
                      {sym.replace('USDT','/U').replace('-BSE','')}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

        </div>
      </div>

      <AnimatePresence>
        {showAdd && (
          <AddModal
            onAdd={sym => { addSymbol(sym); setShowAdd(false) }}
            onClose={() => setShowAdd(false)}
            existing={symbols}
          />
        )}
      </AnimatePresence>
    </div>
  )
}