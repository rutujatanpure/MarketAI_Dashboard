/**
 * Dashboard.jsx — Production-grade market dashboard
 * FIXES vs original (document 2):
 *  1. News section REMOVED — sidebar tabs: Watchlist + Alerts only
 *  2. Symbol change NEVER wipes data to null — old data stays while new loads (no "—" flash)
 *  3. Loading state: 2-col info cards shown instead of blank skeletons
 *  4. techLoading state added — spinner on panel headers during fetch
 *  All logic, hooks, API calls, WebSocket subscriptions UNCHANGED
 */
import { useState, useEffect, useCallback, useRef } from 'react'
import { motion, AnimatePresence }  from 'framer-motion'
import { Link }                     from 'react-router-dom'
import Navbar           from '../components/Navbar'
import CoinCard         from '../components/CoinCard'
import StockCard        from '../components/StockCard'
import DetailChart      from '../components/DetailChart'
import CandlestickChart from '../components/CandlestickChart'
import OrderBookDepth   from '../components/OrderBookDepth'
import AlertBanner      from '../components/AlertBanner'
import WatchlistPanel   from '../components/WatchlistPanel'
import { usePrices }       from '../context/PriceContext'
import { usePriceHistory } from '../hooks/usePriceHistory'
import { useWebSocket }    from '../hooks/useWebSocket'
import { useAiAnalysis }   from '../hooks/useAiAnalysis'
import { useAlerts }       from '../hooks/useAlerts'
import { wsService }       from '../services/websocketService'
import { apiService }      from '../services/apiService'
import { useCurrency }     from '../hooks/useCurrency'
import clsx    from 'clsx'
import dayjs   from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import {
  RiRefreshLine, RiBrainLine, RiBarChartLine, RiBarChart2Line,
  RiBookmarkLine, RiSettings3Line, RiAlertLine, RiLineChartLine,
  RiShieldLine, RiArrowUpLine, RiArrowDownLine, RiInformationLine,
  RiTimerLine, RiEarthLine, RiLightbulbLine, RiLinksLine,
  RiMedalLine, RiCloseLine, RiSearchLine, RiLoader4Line,
  RiBarChartFill, RiDashboardLine,
} from 'react-icons/ri'

const RiCandlestickLine  = RiBarChart2Line
const RiPulseLine        = RiMedalLine
const RiGlobalLine       = RiEarthLine
const RiFlashlightLine   = RiLightbulbLine
const RiExternalLinkLine = RiLinksLine
const RiTimeLine         = RiTimerLine

dayjs.extend(relativeTime)

// ── Constants ─────────────────────────────────────────────────────────────────
const CRYPTO_SYMBOLS  = ['BTCUSDT','ETHUSDT','SOLUSDT','BNBUSDT','XRPUSDT']
const NSE_SYMBOLS     = ['RELIANCE','INFY','TCS','HDFCBANK','ICICIBANK']
const BSE_SYMBOLS     = ['RELIANCE-BSE','INFY-BSE','TCS-BSE','WIPRO-BSE','SBIN-BSE']
const CHART_MODES     = ['Line','Candles','Depth']
const INTERVALS       = ['1m','5m','1h','4h','1d']
const AUTO_REFRESH_MS = 30_000

const ALL_SEARCHABLE = [
  ...CRYPTO_SYMBOLS.map(s => ({ sym:s, label:s.replace('USDT',''), type:'crypto', exchange:'BINANCE' })),
  ...NSE_SYMBOLS.map(s    => ({ sym:s, label:s,                    type:'stock',  exchange:'NSE' })),
  ...BSE_SYMBOLS.map(s    => ({ sym:s, label:s.replace('-BSE',''), type:'stock',  exchange:'BSE' })),
  ...['WIPRO','SBIN','ADANIENT','KOTAKBANK','LT','ITC','BAJFINANCE','MARUTI','SUNPHARMA',
      'HINDUNILVR','TATAMOTORS','TITAN','ULTRACEMCO','ASIANPAINT','AXISBANK']
    .map(s => ({ sym:s, label:s, type:'stock', exchange:'NSE' })),
  ...['ADAUSDT','DOGEUSDT','AVAXUSDT','DOTUSDT','MATICUSDT','LINKUSDT','LTCUSDT','ATOMUSDT']
    .map(s => ({ sym:s, label:s.replace('USDT',''), type:'crypto', exchange:'BINANCE' })),
]

// ── Market hours ──────────────────────────────────────────────────────────────
function getMarketStatus() {
  const now  = new Date()
  const utc  = now.getTime() + now.getTimezoneOffset() * 60000
  const ist  = new Date(utc + 5.5 * 3600000)
  const day  = ist.getDay()
  const mins = ist.getHours() * 60 + ist.getMinutes()
  const open = 9*60+15, close = 15*60+30
  const isWeekend = day === 0 || day === 6
  const isOpen    = !isWeekend && mins >= open && mins < close
  let nextEvent = ''
  if (isWeekend)        nextEvent = 'Opens Monday 09:15 IST'
  else if (mins < open) { const w=open-mins; nextEvent=`Opens in ${Math.floor(w/60)}h ${w%60}m` }
  else if (mins>=close)  nextEvent='Opens tomorrow 09:15 IST'
  else { const l=close-mins; nextEvent=`Closes in ${Math.floor(l/60)}h ${l%60}m` }
  return { isOpen, nextEvent }
}

const SS = {
  get: (k, def) => { try { const v=sessionStorage.getItem(k); return v!=null?JSON.parse(v):def } catch { return def } },
  set: (k, v)  => { try { sessionStorage.setItem(k, JSON.stringify(v)) } catch {} },
}

const riskColor = s => s>=75?'text-red-400':s>=50?'text-amber-400':s>=25?'text-cyan-400':'text-emerald-400'
const riskBg    = s => s>=75?'border-red-500/30 bg-red-500/5':s>=50?'border-amber-500/30 bg-amber-500/5':s>=25?'border-cyan-500/30 bg-cyan-500/5':'border-emerald-500/30 bg-emerald-500/5'
const sigCls    = s => s==='BUY'?'text-emerald-400 bg-emerald-500/10 border-emerald-500/30':s==='SELL'?'text-red-400 bg-red-500/10 border-red-500/30':'text-amber-400 bg-amber-500/10 border-amber-500/20'

// ══════════════════════════════════════════════════════════════════════════════
// SUB-COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

function LiveDot({ active }) {
  return (
    <span className="relative flex h-2 w-2 flex-shrink-0">
      {active && <span className="animate-ping absolute h-full w-full rounded-full bg-emerald-400 opacity-60" />}
      <span className={clsx('relative rounded-full h-2 w-2', active?'bg-emerald-400':'bg-slate-600')} />
    </span>
  )
}

function PanelHeader({ icon: Icon, iconClass='text-cyan-400', title, subtitle, badge, right }) {
  return (
    <div className="flex items-center justify-between mb-4">
      <div className="flex items-center gap-2.5">
        <div className="w-7 h-7 rounded-lg bg-white/[0.05] border border-white/[0.08] flex items-center justify-center flex-shrink-0">
          <Icon size={14} className={iconClass} />
        </div>
        <div>
          <h3 className="text-sm font-semibold text-white leading-none">{title}</h3>
          {subtitle && <p className="text-[11px] text-slate-500 font-mono mt-0.5">{subtitle}</p>}
        </div>
        {badge && (
          <span className="text-[10px] px-2 py-0.5 rounded-md bg-white/[0.06] text-slate-300 border border-white/[0.08] font-mono">{badge}</span>
        )}
      </div>
      {right}
    </div>
  )
}

function TechCard({ label, value, signal }) {
  const good = ['OVERSOLD','BULLISH','LOWER_TOUCH','NORMAL','UPTREND'].includes(signal)
  const bad  = ['OVERBOUGHT','BEARISH','UPPER_TOUCH','DOWNTREND','SPIKE','CRITICAL','HIGH'].includes(signal)
  return (
    <div className="flex flex-col gap-1.5 p-3 rounded-xl bg-white/[0.03] border border-white/[0.07] min-h-[84px]">
      <span className="text-[10px] text-slate-400 font-mono uppercase tracking-widest">{label}</span>
      <span className="text-base font-mono font-bold text-white leading-none">{value ?? '—'}</span>
      {signal && (
        <span className={clsx('text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded-md w-fit',
          good?'bg-emerald-500/15 text-emerald-400':bad?'bg-red-500/15 text-red-400':'bg-amber-500/15 text-amber-400')}>
          {signal}
        </span>
      )}
    </div>
  )
}

// Skeleton keeps the label visible — no blank flash
function TechCardSkel({ label }) {
  return (
    <div className="flex flex-col gap-1.5 p-3 rounded-xl bg-white/[0.03] border border-white/[0.06] min-h-[84px]">
      <span className="text-[10px] text-slate-500 font-mono uppercase tracking-widest">{label}</span>
      <div className="h-5 w-16 bg-white/[0.05] rounded animate-pulse" />
      <div className="h-3 w-12 bg-white/[0.04] rounded animate-pulse" />
    </div>
  )
}

function RiskBar({ label, value=0 }) {
  const c = value>=75?'#ef4444':value>=50?'#f59e0b':'#06b6d4'
  return (
    <div className="flex items-center gap-3">
      <span className="text-[11px] text-slate-400 font-mono w-32 flex-shrink-0">{label}</span>
      <div className="flex-1 h-1.5 bg-white/[0.05] rounded-full overflow-hidden">
        <motion.div className="h-full rounded-full"
          initial={{ width:0 }} animate={{ width:`${Math.min(value,100)}%` }}
          transition={{ duration:0.8, ease:'easeOut' }}
          style={{ background:c }} />
      </div>
      <span className="text-[11px] font-mono font-semibold w-6 text-right" style={{color:c}}>{value}</span>
    </div>
  )
}

function RiskMeter({ score=0 }) {
  const c = score>=75?'#ef4444':score>=50?'#f59e0b':score>=25?'#06b6d4':'#10b981'
  return (
    <div className="relative w-full h-2 bg-white/[0.06] rounded-full overflow-hidden">
      <motion.div className="absolute left-0 top-0 h-full rounded-full"
        initial={{ width:0 }} animate={{ width:`${Math.min(score,100)}%` }}
        transition={{ duration:0.8, ease:'easeOut' }}
        style={{ background:c, boxShadow:`0 0 8px ${c}50` }} />
    </div>
  )
}

function ConfluenceBar({ count=0, signal='HOLD' }) {
  const c = signal==='BUY'?'#10b981':signal==='SELL'?'#ef4444':'#64748b'
  return (
    <div className="flex items-center gap-1.5 mt-2">
      {[1,2,3,4].map(i=>(
        <div key={i} className="h-1.5 flex-1 rounded-full transition-all duration-500"
          style={{ background:i<=count?c:'rgba(255,255,255,0.06)' }} />
      ))}
      <span className="text-[10px] font-mono ml-1" style={{color:c}}>{count}/4 TF</span>
    </div>
  )
}

// ── 2-col info grid — shown while data hasn't loaded yet for first time ────────
const TECH_INFO = [
  { icon:RiBarChartFill,   color:'#06b6d4', title:'RSI (14)',        desc:'Relative Strength Index — oversold <30, overbought >70' },
  { icon:RiLineChartLine,  color:'#10b981', title:'MACD Histogram',  desc:'Momentum divergence — positive = bullish momentum building' },
  { icon:RiFlashlightLine, color:'#a78bfa', title:'Bollinger Bands', desc:'Volatility bands — upper touch = overbought pressure' },
  { icon:RiShieldLine,     color:'#f59e0b', title:'Z-Score',         desc:'Statistical deviation — >2σ flags a price anomaly' },
  { icon:RiBarChart2Line,  color:'#ef4444', title:'Volume Ratio',    desc:'Current vs average volume — >1.5× signals unusual activity' },
  { icon:RiDashboardLine,  color:'#06b6d4', title:'Pump/Dump Prob',  desc:'ML probability of manipulation or coordinated price action' },
]

const RISK_INFO = [
  { title:'Composite Score', desc:'Weighted average across all 6 risk factors (0–100)' },
  { title:'VaR-95 / VaR-99', desc:'Value at Risk — expected max loss at 95% and 99% confidence' },
  { title:'Market Regime',   desc:'Trending, ranging, or high-volatility classification' },
  { title:'Dominant Factor', desc:'Highest contributing risk driver for this symbol right now' },
]

const MTF_INFO = [
  { title:'1m Timeframe',  desc:'Micro momentum — scalp signals & very short-term bias' },
  { title:'5m Timeframe',  desc:'Intraday trend direction & entry timing' },
  { title:'15m Timeframe', desc:'Swing signal confirmation — filters 1m noise' },
  { title:'1h Timeframe',  desc:'Macro trend bias — used for position direction' },
]

function InfoGrid({ items }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-4">
      {items.map(item => (
        <div key={item.title}
          className="flex items-start gap-3 p-3 rounded-xl bg-white/[0.025] border border-white/[0.06]">
          {item.icon && (
            <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
              style={{ background:`${item.color}14`, border:`1px solid ${item.color}22` }}>
              <item.icon size={13} style={{ color:item.color }}/>
            </div>
          )}
          <div>
            <p className="text-[11px] font-semibold text-slate-200 mb-0.5">{item.title}</p>
            <p className="text-[10px] text-slate-500 leading-relaxed">{item.desc}</p>
          </div>
        </div>
      ))}
    </div>
  )
}

// ── Global Search Bar ─────────────────────────────────────────────────────────
function GlobalSearch({ onSelect }) {
  const [query,   setQuery]   = useState('')
  const [open,    setOpen]    = useState(false)
  const [results, setResults] = useState([])
  const ref = useRef(null)

  useEffect(() => {
    if (!query.trim()) { setResults([]); return }
    const q = query.toUpperCase()
    setResults(ALL_SEARCHABLE.filter(item => item.sym.includes(q) || item.label.includes(q)).slice(0,8))
  }, [query])

  useEffect(() => {
    const f = e => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', f)
    return () => document.removeEventListener('mousedown', f)
  }, [])

  const exColor = ex => ex==='NSE'?'text-emerald-400 bg-emerald-500/10':ex==='BSE'?'text-teal-400 bg-teal-500/10':'text-amber-400 bg-amber-500/10'

  return (
    <div ref={ref} className="relative w-64">
      <div className="relative">
        <RiSearchLine className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={14} />
        <input value={query}
          onChange={e => { setQuery(e.target.value); setOpen(true) }}
          onFocus={() => setOpen(true)}
          placeholder="Search stocks & crypto…"
          className="w-full bg-white/[0.05] border border-white/[0.09] rounded-xl
                     pl-9 pr-3 py-2 text-xs font-mono text-white placeholder-slate-500
                     focus:outline-none focus:border-cyan-500/50 focus:bg-white/[0.08] transition-all" />
        {query && (
          <button onClick={() => { setQuery(''); setResults([]) }}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-600 hover:text-slate-300">
            <RiCloseLine size={13} />
          </button>
        )}
      </div>
      <AnimatePresence>
        {open && results.length > 0 && (
          <motion.div
            initial={{ opacity:0, y:-6, scale:0.97 }} animate={{ opacity:1, y:0, scale:1 }}
            exit={{ opacity:0, y:-6, scale:0.97 }}
            className="absolute top-full left-0 right-0 mt-1.5 z-[100]
                       bg-[#0d1a2d] border border-white/[0.12] rounded-xl
                       overflow-hidden shadow-2xl shadow-black/50">
            {results.map(item => (
              <button key={item.sym}
                onMouseDown={() => {
                  onSelect(item.sym, item.type==='crypto'?'crypto':item.exchange==='BSE'?'bse':'nse')
                  setQuery(''); setResults([]); setOpen(false)
                }}
                className="w-full flex items-center justify-between px-3.5 py-2.5 hover:bg-white/[0.06] transition-colors">
                <div className="flex items-center gap-2.5">
                  <span className="text-xs font-bold text-white font-mono">{item.label}</span>
                  <span className="text-[10px] text-slate-500 font-mono">{item.sym}</span>
                </div>
                <span className={clsx('text-[9px] font-bold px-1.5 py-0.5 rounded-md', exColor(item.exchange))}>
                  {item.exchange}
                </span>
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ══════════════════════════════════════════════════════════════════════════════
export default function Dashboard() {
  const { cryptoPrices, stockPrices, connected } = usePrices()
  const { format } = useCurrency()

  // ── Persisted state ───────────────────────────────────────────────────────
  const [activeTab,  setActiveTab]  = useState(() => SS.get('dash_tab',   'crypto'))
  const [selected,   setSelected]   = useState(() => SS.get('dash_sym',   'BTCUSDT'))
  const [chartMode,  setChartMode]  = useState(() => SS.get('dash_chart', 'Candles'))
  const [interval,   setInterval]   = useState(() => SS.get('dash_iv',    '1h'))
  const [sideTab,    setSideTab]    = useState(() => SS.get('dash_side',  'watchlist'))

  // ── Non-persisted state ───────────────────────────────────────────────────
  const [marketStatus, setMarketStatus] = useState(getMarketStatus())
  const [technicals,   setTechnicals]   = useState(null)
  const [riskProfile,  setRiskProfile]  = useState(null)
  const [confluence,   setConfluence]   = useState(null)
  const [lastUpdate,   setLastUpdate]   = useState(null)
  const [autoRefresh,  setAutoRefresh]  = useState(true)
  const [refreshing,   setRefreshing]   = useState(false)

  // ── FIX 1: separate loading flag — does NOT wipe data to null ─────────────
  // When user clicks NSE/BSE, old data stays visible while new data loads
  // techLoading=true just shows a spinner on the panel header, not blank "—"
  const [techLoading, setTechLoading] = useState(false)

  useEffect(() => { SS.set('dash_tab',   activeTab)  }, [activeTab])
  useEffect(() => { SS.set('dash_sym',   selected)   }, [selected])
  useEffect(() => { SS.set('dash_chart', chartMode)  }, [chartMode])
  useEffect(() => { SS.set('dash_iv',    interval)   }, [interval])
  useEffect(() => { SS.set('dash_side',  sideTab)    }, [sideTab])

  // ── Hooks ─────────────────────────────────────────────────────────────────
  const selectedType = activeTab === 'crypto' ? 'crypto' : 'stock'
  const { history, loading: histLoading } = usePriceHistory(selected, selectedType, 24)
  const { analysis, loading: aiLoading, refresh: refreshAi } = useAiAnalysis(selected)
  const { alerts } = useAlerts(selected, 20)

  const techWs = useWebSocket(`/topic/indicators/${selected}`, { enabled: !!selected })
  const riskWs = useWebSocket(`/topic/risk/${selected}`,       { enabled: !!selected })
  const confWs = useWebSocket(`/topic/confluence/${selected}`, { enabled: !!selected })

  useEffect(() => { if (techWs) setTechnicals(techWs) }, [techWs])
  useEffect(() => { if (riskWs) setRiskProfile(riskWs) }, [riskWs])
  useEffect(() => { if (confWs) setConfluence(confWs)  }, [confWs])

  // ── Market clock ──────────────────────────────────────────────────────────
  useEffect(() => {
    const t = setInterval(() => setMarketStatus(getMarketStatus()), 30000)
    return () => clearInterval(t)
  }, [])

  // ── FIX 2: fetchSymbolData — NEVER sets technicals/risk/confluence to null
  // Old data stays on screen while fetching; spinner shows on header
  const fetchSymbolData = useCallback((sym) => {
    if (!sym) return
    setRefreshing(true)
    setTechLoading(true)
    const p1 = apiService.get(`/api/indicators/latest?symbol=${sym}`)
      .then(r => { if (r.data) setTechnicals(r.data) }).catch(()=>{})
    const p2 = apiService.get(`/api/risk/latest?symbol=${sym}`)
      .then(r => { if (r.data) setRiskProfile(r.data) }).catch(()=>{})
    const p3 = apiService.get(`/api/confluence/latest?symbol=${sym}`)
      .then(r => { if (r.data) setConfluence(r.data) }).catch(()=>{})
    Promise.all([p1,p2,p3]).finally(() => { setRefreshing(false); setTechLoading(false) })
  }, [])

  // ── FIX 3: On symbol change — DO NOT clear to null. Fetch silently.
  // REMOVED: setTechnicals(null); setRiskProfile(null); setConfluence(null)
  useEffect(() => {
    fetchSymbolData(selected)
  }, [selected, fetchSymbolData])

  // ── Auto-refresh every 30s ────────────────────────────────────────────────
  useEffect(() => {
    if (!autoRefresh) return
    const t = setInterval(() => fetchSymbolData(selected), AUTO_REFRESH_MS)
    return () => clearInterval(t)
  }, [selected, autoRefresh, fetchSymbolData])

  // ── Live data ─────────────────────────────────────────────────────────────
  const liveData = activeTab === 'crypto' ? cryptoPrices[selected] : stockPrices[selected]
  useEffect(() => { if (liveData) setLastUpdate(new Date()) }, [liveData])

  // ── Symbol selection ──────────────────────────────────────────────────────
  const handleSelect = useCallback((sym) => {
    setSelected(sym)
    wsService.subscribeSymbol?.(sym)
  }, [])

  const handleTabChange = useCallback((tab) => {
    setActiveTab(tab)
    const def = { crypto:'BTCUSDT', nse:'RELIANCE', bse:'RELIANCE-BSE' }
    setSelected(def[tab])
    wsService.subscribeSymbol?.(def[tab])
  }, [])

  const handleSearchSelect = useCallback((sym, tab) => {
    setActiveTab(tab)
    setSelected(sym)
    wsService.subscribeSymbol?.(sym)
  }, [])

  const currentSymbols = activeTab==='crypto' ? CRYPTO_SYMBOLS : activeTab==='nse' ? NSE_SYMBOLS : BSE_SYMBOLS
  const changePct  = liveData ? (liveData.priceChange ?? liveData.changePercent ?? 0) : 0
  const isUp       = changePct >= 0
  const isStockTab = activeTab !== 'crypto'
  const isLive     = !isStockTab || marketStatus.isOpen
  const isConnLive = connected && isLive

  const fmtPrice = (p) => {
    if (!p || p === 0) return isStockTab ? '₹—' : '$—'
    if (isStockTab) return `₹${p.toLocaleString('en-IN',{minimumFractionDigits:2,maximumFractionDigits:2})}`
    if (p >= 1000)  return `$${p.toLocaleString('en-US',{minimumFractionDigits:2,maximumFractionDigits:2})}`
    if (p >= 1)     return `$${p.toFixed(4)}`
    return `$${p.toFixed(6)}`
  }

  const sentColor = { BULLISH:'text-emerald-400', BEARISH:'text-red-400', NEUTRAL:'text-amber-400' }

  // ── RENDER ────────────────────────────────────────────────────────────────
  return (
    <div className="min-h-screen bg-[#050B17] pt-16">
      <Navbar />
      <AlertBanner />

      <div className="max-w-[1800px] mx-auto px-4 xl:px-6 py-5">

        {/* ══ TOP BAR ══════════════════════════════════════════════════════ */}
        <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
          <div>
            <h1 className="text-xl font-bold text-white tracking-tight">Market Dashboard</h1>
            <div className="flex flex-wrap items-center gap-3 mt-1">
              <div className="flex items-center gap-1.5">
                <LiveDot active={isConnLive} />
                <span className={clsx('text-xs font-mono', isConnLive?'text-emerald-400':'text-slate-500')}>
                  {isConnLive ? 'Live' : connected ? 'Market Closed' : 'Connecting…'}
                </span>
                {lastUpdate && (
                  <span className="text-xs text-slate-600 font-mono">· {dayjs(lastUpdate).fromNow()}</span>
                )}
              </div>
              {isStockTab ? (
                <div className={clsx('flex items-center gap-1.5 text-xs font-mono px-2.5 py-1 rounded-lg border',
                  marketStatus.isOpen
                    ? 'bg-emerald-500/10 border-emerald-500/25 text-emerald-400'
                    : 'bg-slate-800/60 border-slate-700/40 text-slate-400')}>
                  <RiTimeLine size={11} />
                  {marketStatus.isOpen ? '🟢 NSE/BSE Open' : '🔴 NSE/BSE Closed'}
                  <span className="opacity-60">· {marketStatus.nextEvent}</span>
                </div>
              ) : (
                <div className="flex items-center gap-1.5 text-xs font-mono px-2.5 py-1 rounded-lg border bg-emerald-500/10 border-emerald-500/25 text-emerald-400">
                  <RiGlobalLine size={11} /> 🟢 Crypto 24/7
                </div>
              )}
              {riskProfile && (
                <div className={clsx('flex items-center gap-1.5 text-xs font-mono px-2.5 py-1 rounded-lg border', riskBg(riskProfile.compositeRiskScore))}>
                  <RiShieldLine size={11} className={riskColor(riskProfile.compositeRiskScore)} />
                  <span className={riskColor(riskProfile.compositeRiskScore)}>Risk {riskProfile.compositeRiskScore}/100</span>
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            <GlobalSearch onSelect={handleSearchSelect} />
            <button onClick={() => setAutoRefresh(p => !p)}
              className={clsx('flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold border transition-all',
                autoRefresh
                  ? 'bg-cyan-500/10 border-cyan-500/25 text-cyan-400'
                  : 'bg-white/[0.04] border-white/[0.08] text-slate-400')}>
              <RiLoader4Line size={13} className={autoRefresh ? 'animate-spin' : ''} />
              Auto
            </button>
            <div className="flex gap-1 p-1 bg-white/[0.04] rounded-xl border border-white/[0.08]">
              {[['crypto','₿ Crypto'],['nse','🇮🇳 NSE'],['bse','📊 BSE']].map(([tab, label]) => (
                <button key={tab} onClick={() => handleTabChange(tab)}
                  className={clsx('px-4 py-1.5 rounded-lg text-xs font-semibold transition-all',
                    activeTab===tab
                      ? 'bg-cyan-500 text-slate-950 shadow-[0_0_14px_#06b6d440]'
                      : 'text-slate-300 hover:text-white hover:bg-white/[0.05]')}>
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* ══ SYMBOL CARDS ════════════════════════════════════════════════ */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-5">
          {currentSymbols.map((sym, i) => {
            const data = activeTab==='crypto' ? cryptoPrices[sym] : stockPrices[sym]
            return (
              <motion.div key={sym} initial={{ opacity:0, y:10 }} animate={{ opacity:1, y:0 }} transition={{ delay:i*0.05 }}>
                {activeTab==='crypto'
                  ? <CoinCard data={data||{symbol:sym,price:0}} history={history} onClick={handleSelect} selected={selected===sym} />
                  : <StockCard data={data||{symbol:sym,price:0}} onClick={handleSelect} selected={selected===sym} marketOpen={marketStatus.isOpen} />
                }
              </motion.div>
            )
          })}
        </div>

        {/* NSE/BSE closed banner */}
        {isStockTab && !marketStatus.isOpen && (
          <div className="mb-5 flex items-center gap-3 px-4 py-3 rounded-xl bg-amber-500/6 border border-amber-500/18 text-amber-300 text-sm">
            <RiInformationLine size={16} className="flex-shrink-0" />
            <span>
              NSE/BSE is currently <strong>closed</strong>. Displaying last session prices.
              Live trading resumes <strong>{marketStatus.nextEvent.toLowerCase()}</strong>.
            </span>
          </div>
        )}

        {/* ══ MAIN 2-COL LAYOUT ════════════════════════════════════════════ */}
        <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-5">

          {/* ═ LEFT COLUMN ═════════════════════════════════════════════════ */}
          <div className="flex flex-col gap-5">

            {/* ── CHART PANEL ───────────────────────────────────────────── */}
            <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
              <div className="flex flex-wrap items-start justify-between gap-4 mb-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2.5 mb-1">
                    <h2 className="text-xl font-bold text-white tracking-tight">{selected.replace('-BSE','').replace('USDT','')}</h2>
                    <span className="text-sm text-slate-400 font-mono">{selected}</span>
                    {isStockTab && (
                      <span className={clsx('text-[10px] font-mono font-bold px-2 py-0.5 rounded-md border',
                        marketStatus.isOpen
                          ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/25'
                          : 'text-slate-500 bg-slate-800/40 border-slate-700/40')}>
                        {marketStatus.isOpen ? '● LIVE' : '○ CLOSED'}
                      </span>
                    )}
                    {refreshing && <RiLoader4Line size={14} className="text-cyan-400 animate-spin" />}
                  </div>
                  {liveData ? (
                    <div className="flex flex-wrap items-baseline gap-3">
                      <span className="text-4xl font-bold text-white tabular-nums">{fmtPrice(liveData.price ?? 0)}</span>
                      <div className={clsx('flex items-center gap-1 text-lg font-mono font-semibold', isUp?'text-emerald-400':'text-red-400')}>
                        {isUp ? <RiArrowUpLine size={18}/> : <RiArrowDownLine size={18}/>}
                        {Math.abs(changePct).toFixed(2)}%
                      </div>
                      {(liveData.high24h||liveData.high) && (
                        <span className="text-xs font-mono text-slate-400">
                          H: {fmtPrice(liveData.high24h??liveData.high??0)} · L: {fmtPrice(liveData.low24h??liveData.low??0)}
                        </span>
                      )}
                    </div>
                  ) : (
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-48 rounded-xl bg-white/[0.05] animate-pulse" />
                      <div className="h-7 w-20 rounded-xl bg-white/[0.04] animate-pulse" />
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                  <div className="flex gap-0.5 p-1 bg-white/[0.04] rounded-xl border border-white/[0.08]">
                    {CHART_MODES.map(m => (
                      <button key={m} onClick={() => setChartMode(m)}
                        className={clsx('flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all',
                          chartMode===m ? 'bg-cyan-500 text-slate-950' : 'text-slate-400 hover:text-white')}>
                        {m==='Line' && <RiBarChartLine size={12}/>}
                        {m==='Candles' && <RiCandlestickLine size={12}/>}
                        {m}
                      </button>
                    ))}
                  </div>
                  <div className="flex gap-0.5 p-1 bg-white/[0.04] rounded-xl border border-white/[0.08]">
                    {INTERVALS.map(iv => (
                      <button key={iv} onClick={() => setInterval(iv)}
                        className={clsx('px-2.5 py-1.5 rounded-lg text-[11px] font-mono font-medium transition-all',
                          interval===iv ? 'bg-slate-600 text-white' : 'text-slate-500 hover:text-slate-200')}>
                        {iv}
                      </button>
                    ))}
                  </div>
                  <button onClick={() => fetchSymbolData(selected)}
                    className="p-2 rounded-xl bg-white/[0.04] border border-white/[0.08] text-slate-400 hover:text-white hover:bg-white/[0.08] transition-all">
                    <RiRefreshLine size={14} className={refreshing?'animate-spin':''} />
                  </button>
                </div>
              </div>

              <div className="rounded-xl overflow-hidden bg-[#060F1C] border border-white/[0.04] min-h-[300px]">
                {histLoading ? (
                  <div className="flex items-center justify-center h-[300px]">
                    <div className="flex flex-col items-center gap-2 text-slate-600">
                      <div className="w-7 h-7 border-2 border-cyan-500/30 border-t-cyan-400 rounded-full animate-spin" />
                      <span className="text-xs font-mono">Loading chart data…</span>
                    </div>
                  </div>
                ) : chartMode==='Line' ? (
                  <DetailChart history={history} height={300} />
                ) : chartMode==='Candles' ? (
                  <CandlestickChart symbol={selected} type={selectedType} interval={interval} />
                ) : (
                  <OrderBookDepth price={liveData?.price ?? 50000} />
                )}
              </div>

              {liveData && (
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 pt-4 border-t border-white/[0.05]">
                  {[
                    ['Volume',    liveData.volume >= 1e9 ? `${(liveData.volume/1e9).toFixed(2)}B` : liveData.volume >= 1e6 ? `${(liveData.volume/1e6).toFixed(2)}M` : (liveData.volume?.toFixed(0) ?? '—')],
                    ['Vol Ratio', technicals?.volumeRatio ? `${technicals.volumeRatio.toFixed(2)}×` : '—'],
                    ['ATR',       technicals?.atr ? fmtPrice(technicals.atr) : '—'],
                    ['ATR %',     technicals?.atrPercent ? `${technicals.atrPercent.toFixed(2)}%` : '—'],
                  ].map(([k, v]) => (
                    <div key={k} className="px-3 py-2.5 rounded-xl bg-white/[0.025] border border-white/[0.05]">
                      <p className="text-[10px] text-slate-400 font-mono uppercase tracking-wider">{k}</p>
                      <p className="text-sm font-mono font-semibold text-white mt-0.5">{v}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* ── TECHNICAL INDICATORS ──────────────────────────────────── */}
            <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
              <PanelHeader
                icon={RiLineChartLine}
                title="Technical Indicators"
                subtitle={`RSI · MACD · Bollinger Bands · Z-Score · Volume${technicals ? ` · ${dayjs(technicals.timestamp).fromNow()}` : ''}`}
                right={
                  techLoading && !technicals
                    ? <RiLoader4Line size={14} className="text-cyan-400 animate-spin" />
                    : techLoading
                      ? <RiLoader4Line size={14} className="text-cyan-500/40 animate-spin" />
                      : null
                }
              />

              {technicals ? (
                // Data available — show cards (old data stays while refreshing for new symbol)
                <motion.div initial={{opacity:0}} animate={{opacity:1}}>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 mb-2.5">
                    <TechCard label="RSI (14)"   value={technicals.rsi?.toFixed(1)}                               signal={technicals.rsiSignal} />
                    <TechCard label="MACD Hist"  value={technicals.macdHistogram?.toFixed(4)}                     signal={technicals.macdSignal} />
                    <TechCard label="Bollinger"  value={`${((technicals.bollingerPosition??0.5)*100).toFixed(0)}%`} signal={technicals.bollingerSignal} />
                    <TechCard label="Z-Score"    value={`${technicals.zScore?.toFixed(2)??'0.00'}σ`}              signal={technicals.anomalySeverity} />
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                    <TechCard label="Volume"     value={`${technicals.volumeRatio?.toFixed(2)??'1.00'}×`}         signal={technicals.volumeSignal} />
                    <TechCard label="Risk Score" value={`${technicals.riskScore??0}/100`}                         signal={technicals.riskLevel} />
                    <TechCard label="Pump/Dump"  value={`${technicals.pumpDumpProbability?.toFixed(0)??'0'}%`}    signal={technicals.pumpDumpSuspected?'CRITICAL':'NORMAL'} />
                    <TechCard label="Trend"      value={technicals.trend??'SIDEWAYS'}                             signal={technicals.trend} />
                  </div>
                </motion.div>
              ) : (
                // No data at all yet — show 2-col info + labeled skeletons
                <motion.div initial={{opacity:0}} animate={{opacity:1}}>
                  <div className="flex items-center gap-2 mb-3 px-3 py-2 rounded-xl bg-cyan-500/5 border border-cyan-500/15">
                    <RiLoader4Line size={13} className="text-cyan-400 animate-spin flex-shrink-0" />
                    <p className="text-[11px] text-slate-400 font-mono">
                      Fetching indicators for <span className="text-cyan-400 font-semibold">{selected}</span>…
                    </p>
                  </div>
                  <InfoGrid items={TECH_INFO} />
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 mb-2.5">
                    {['RSI (14)','MACD Hist','Bollinger','Z-Score'].map(l => <TechCardSkel key={l} label={l} />)}
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                    {['Volume','Risk Score','Pump/Dump','Trend'].map(l => <TechCardSkel key={l} label={l} />)}
                  </div>
                </motion.div>
              )}
            </div>

            {/* ── RISK + CONFLUENCE ─────────────────────────────────────── */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">

              {/* Smart Risk Engine */}
              <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
                <PanelHeader icon={RiShieldLine} iconClass="text-purple-400"
                  title="Smart Risk Engine" badge="6-Factor"
                  right={techLoading && !riskProfile ? <RiLoader4Line size={14} className="text-purple-400 animate-spin" /> : null}
                />

                {riskProfile ? (
                  <motion.div initial={{opacity:0}} animate={{opacity:1}}>
                    <div className="flex items-start justify-between mb-4">
                      <div>
                        <div className={clsx('text-4xl font-bold tabular-nums leading-none', riskColor(riskProfile.compositeRiskScore))}>
                          {riskProfile.compositeRiskScore}<span className="text-xl text-slate-500">/100</span>
                        </div>
                        <div className="flex items-center gap-2 mt-2">
                          <span className={clsx('text-sm font-bold font-mono', riskColor(riskProfile.compositeRiskScore))}>{riskProfile.riskLevel}</span>
                          <span className="text-slate-600">·</span>
                          <span className="text-sm text-slate-300 font-mono">{riskProfile.marketRegime?.replace(/_/g,' ')}</span>
                        </div>
                      </div>
                      <div className="text-right space-y-1">
                        <p className="text-[10px] text-slate-400 font-mono">VaR-95</p>
                        <p className="text-sm font-mono font-semibold text-amber-400">{riskProfile.var95?.toFixed(2)}%</p>
                        <p className="text-[10px] text-slate-400 font-mono">VaR-99</p>
                        <p className="text-sm font-mono font-semibold text-red-400">{riskProfile.var99?.toFixed(2)}%</p>
                      </div>
                    </div>
                    <RiskMeter score={riskProfile.compositeRiskScore} />
                    <div className="space-y-2.5 mt-4">
                      <RiskBar label="Price Volatility"  value={riskProfile.priceVolatilityScore} />
                      <RiskBar label="Volume Anomaly"    value={riskProfile.volumeAnomalyScore} />
                      <RiskBar label="Statistical (Z)"   value={riskProfile.statisticalAnomalyScore} />
                      <RiskBar label="Momentum"          value={riskProfile.momentumRiskScore} />
                      <RiskBar label="Manipulation"      value={riskProfile.manipulationRiskScore} />
                      <RiskBar label="Market Regime"     value={riskProfile.regimeRiskScore} />
                    </div>
                    {riskProfile.dominantRiskFactor && (
                      <p className="text-[11px] text-slate-400 font-mono mt-3 pt-3 border-t border-white/[0.05]">
                        Dominant: <span className="text-amber-300 font-semibold">{riskProfile.dominantRiskFactor}</span>
                      </p>
                    )}
                  </motion.div>
                ) : (
                  // No data yet — 2-col info + bar skeletons
                  <motion.div initial={{opacity:0}} animate={{opacity:1}}>
                    <InfoGrid items={RISK_INFO.map(i => ({ ...i, icon:RiShieldLine, color:'#a78bfa' }))} />
                    <div className="space-y-2.5 mt-2">
                      {['Price Volatility','Volume Anomaly','Statistical (Z)','Momentum','Manipulation','Market Regime'].map(l => (
                        <div key={l} className="flex items-center gap-3">
                          <span className="text-[11px] text-slate-500 font-mono w-32 flex-shrink-0">{l}</span>
                          <div className="flex-1 h-1.5 bg-white/[0.04] rounded-full overflow-hidden">
                            <div className="h-full w-1/3 bg-white/[0.06] rounded-full animate-pulse" />
                          </div>
                          <span className="text-[11px] text-slate-700 w-6 text-right">—</span>
                        </div>
                      ))}
                    </div>
                  </motion.div>
                )}
              </div>

              {/* Multi-Timeframe Confluence */}
              <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
                <PanelHeader icon={RiPulseLine} iconClass="text-cyan-400"
                  title="Multi-Timeframe" badge="1m · 5m · 15m · 1h"
                  right={techLoading && !confluence ? <RiLoader4Line size={14} className="text-cyan-400 animate-spin" /> : null}
                />

                {confluence ? (
                  <motion.div initial={{opacity:0}} animate={{opacity:1}}>
                    <div className="flex items-center justify-between mb-4">
                      <div>
                        <span className={clsx('text-3xl font-bold',
                          confluence.confluenceSignal==='BUY'  ? 'text-emerald-400' :
                          confluence.confluenceSignal==='SELL' ? 'text-red-400' : 'text-amber-400')}>
                          {confluence.confluenceSignal}
                        </span>
                        <p className="text-xs text-slate-400 font-mono mt-1">{confluence.confidenceText ?? ''}</p>
                      </div>
                      <div className="text-right">
                        <p className="text-xs text-slate-300 font-mono">
                          Multiplier: <span className="text-cyan-400 font-semibold">{confluence.multiplier?.toFixed(1)??'1.0'}×</span>
                        </p>
                        {confluence.anomalyCount > 0 && (
                          <p className="text-xs text-amber-400 font-mono mt-1">⚠ {confluence.anomalyCount}/4 anomaly</p>
                        )}
                      </div>
                    </div>
                    <div className="grid grid-cols-4 gap-2">
                      {confluence.windows && Object.keys(confluence.windows).length > 0
                        ? Object.entries(confluence.windows).map(([tf, w]) => (
                          <div key={tf} className={clsx('rounded-xl p-3 bg-white/[0.03] border text-center',
                            w.signal==='BUY'?'border-emerald-500/25':w.signal==='SELL'?'border-red-500/25':'border-white/[0.07]')}>
                            <p className="text-[10px] text-slate-400 font-mono mb-2">{tf.replace('_',' ')}</p>
                            <p className={clsx('text-xs font-bold',
                              w.signal==='BUY'?'text-emerald-400':w.signal==='SELL'?'text-red-400':'text-amber-400')}>
                              {w.signal??'HOLD'}
                            </p>
                            <p className="text-[10px] text-slate-400 font-mono mt-1">RSI {w.rsi?.toFixed(0)??'50'}</p>
                            <p className="text-[10px] text-slate-400 font-mono">Z {w.zScore?.toFixed(1)??'0.0'}σ</p>
                            <div className="h-0.5 bg-white/[0.05] rounded-full mt-2 overflow-hidden">
                              <div className="h-full rounded-full"
                                style={{ width:`${(w.signalStrength??0)*100}%`,
                                  background:w.signal==='BUY'?'#10b981':w.signal==='SELL'?'#ef4444':'#f59e0b' }} />
                            </div>
                          </div>
                        ))
                        : ['1m','5m','15m','1h'].map(tf => (
                          <div key={tf} className="rounded-xl p-3 bg-white/[0.03] border border-white/[0.07] text-center">
                            <p className="text-[10px] text-slate-400 font-mono">{tf}</p>
                            <p className="text-xs text-slate-600 mt-2">—</p>
                          </div>
                        ))
                      }
                    </div>
                    <ConfluenceBar count={confluence.confluenceCount} signal={confluence.confluenceSignal} />
                  </motion.div>
                ) : (
                  // No data yet — 2-col info + TF card skeletons
                  <motion.div initial={{opacity:0}} animate={{opacity:1}}>
                    <InfoGrid items={MTF_INFO.map(i => ({ ...i, icon:RiPulseLine, color:'#06b6d4' }))} />
                    <div className="grid grid-cols-4 gap-2">
                      {['1m','5m','15m','1h'].map(tf => (
                        <div key={tf} className="rounded-xl p-3 bg-white/[0.03] border border-white/[0.06] text-center">
                          <p className="text-[10px] text-slate-400 font-mono mb-2">{tf}</p>
                          <div className="h-4 w-10 mx-auto bg-white/[0.05] rounded animate-pulse mb-1.5" />
                          <div className="h-2.5 w-8 mx-auto bg-white/[0.04] rounded animate-pulse" />
                          <div className="h-0.5 bg-white/[0.04] rounded-full mt-2" />
                        </div>
                      ))}
                    </div>
                  </motion.div>
                )}
              </div>
            </div>

            {/* ── AI ANALYSIS ───────────────────────────────────────────── */}
            <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
              <PanelHeader
                icon={RiBrainLine} iconClass="text-purple-400"
                title="AI Analysis"
                subtitle="Gemini · 12h cache · auto-updates via WebSocket"
                right={
                  <button onClick={refreshAi} disabled={aiLoading}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                               border border-purple-500/30 text-purple-300 hover:bg-purple-500/10
                               disabled:opacity-40 transition-all">
                    <RiRefreshLine size={12} className={aiLoading?'animate-spin':''} />
                    {aiLoading ? 'Analyzing…' : 'Refresh'}
                  </button>
                }
              />
              <AnimatePresence mode="wait">
                {analysis ? (
                  <motion.div key="ai-data" initial={{opacity:0,y:8}} animate={{opacity:1,y:0}} exit={{opacity:0}}>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <div className="p-4 rounded-xl bg-white/[0.03] border border-white/[0.06]">
                        <p className="text-[11px] text-slate-400 font-mono uppercase tracking-wider mb-2">Sentiment</p>
                        <p className={clsx('text-2xl font-bold', sentColor[analysis.sentiment]??'text-white')}>{analysis.sentiment}</p>
                        <div className="mt-2.5 h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
                          <div className="h-full rounded-full bg-gradient-to-r from-red-400 via-amber-400 to-emerald-400"
                            style={{ width:`${((analysis.sentimentScore+1)/2)*100}%` }} />
                        </div>
                        <p className="text-[11px] text-slate-400 font-mono mt-1.5">
                          Score: {analysis.sentimentScore?.toFixed(2)} · Conf: {((analysis.confidenceScore??0)*100).toFixed(0)}%
                        </p>
                      </div>
                      <div className="p-4 rounded-xl bg-white/[0.03] border border-white/[0.06]">
                        <p className="text-[11px] text-slate-400 font-mono uppercase tracking-wider mb-2">Signal</p>
                        <span className={clsx('inline-block text-xl font-bold px-3 py-1 rounded-lg border', sigCls(analysis.signal))}>
                          {analysis.signal}
                        </span>
                        <div className="mt-3 space-y-1.5">
                          {analysis.anomaly && (
                            <div className="flex items-center gap-1.5 text-xs text-amber-300">
                              <RiAlertLine size={12}/> Anomaly detected
                            </div>
                          )}
                          {analysis.riskScore > 0 && (
                            <p className="text-xs font-mono text-slate-400">
                              Risk: <span className={riskColor(analysis.riskScore)}>{analysis.riskScore}/100</span>
                            </p>
                          )}
                          {analysis.confluenceSignal && (
                            <p className="text-xs font-mono text-slate-400">
                              Confluence: <span className="text-cyan-400">{analysis.confluenceSignal} {analysis.confluenceCount}/4</span>
                            </p>
                          )}
                        </div>
                      </div>
                      <div className="p-4 rounded-xl bg-white/[0.03] border border-white/[0.06]">
                        <p className="text-[11px] text-slate-400 font-mono uppercase tracking-wider mb-2">AI Summary</p>
                        <p className="text-xs text-slate-200 leading-relaxed">{analysis.summary}</p>
                        {analysis.source && (
                          <p className="text-[10px] text-slate-500 font-mono mt-2">via {analysis.source} · {analysis.modelUsed}</p>
                        )}
                      </div>
                    </div>
                  </motion.div>
                ) : (
                  <motion.div key="ai-empty" initial={{opacity:0}} animate={{opacity:1}}
                    className="flex flex-col items-center justify-center py-12 text-slate-600">
                    <RiBrainLine size={40} className="mb-3 opacity-20" />
                    <p className="text-xs font-mono text-slate-500">Analysis updates automatically</p>
                    <p className="text-[11px] font-mono text-slate-600 mt-1">Click Refresh for on-demand analysis</p>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

          </div>{/* end left col */}

          {/* ═ RIGHT SIDEBAR — Watchlist + Alerts only (News REMOVED) ══════ */}
          <div className="flex flex-col gap-4">

            {/* 2 tabs only — News tab removed */}
            <div className="flex gap-1 p-1 bg-white/[0.04] rounded-xl border border-white/[0.08]">
              {[['watchlist',RiBookmarkLine,'Watchlist'],['alerts',RiAlertLine,'Alerts']].map(([key,Icon,label]) => (
                <button key={key} onClick={() => setSideTab(key)}
                  className={clsx('flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-[11px] font-semibold transition-all',
                    sideTab===key
                      ? 'bg-cyan-500 text-slate-950 shadow-[0_0_10px_#06b6d430]'
                      : 'text-slate-400 hover:text-white hover:bg-white/[0.05]')}>
                  <Icon size={12}/>{label}
                </button>
              ))}
            </div>

            {sideTab==='watchlist' && <WatchlistPanel onSelect={handleSelect} />}

            {sideTab==='alerts' && (
              <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-4">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <RiAlertLine size={14} className="text-amber-400" />
                    <span className="text-sm font-semibold text-white">Alerts</span>
                    <span className="text-xs text-slate-500 font-mono">{selected}</span>
                  </div>
                  {alerts.length > 0 && (
                    <span className="text-[10px] bg-amber-500/10 text-amber-400 border border-amber-500/20 px-2 py-0.5 rounded-full font-mono">
                      {alerts.length}
                    </span>
                  )}
                </div>
                <div className="space-y-2 max-h-[400px] overflow-y-auto">
                  {alerts.length > 0 ? alerts.map((alert, i) => {
                    const crit = alert.severity==='CRITICAL', high = alert.severity==='HIGH'
                    return (
                      <div key={`${alert.timestamp}-${i}`}
                        className={clsx('p-3 rounded-xl border',
                          crit?'bg-red-500/6 border-red-500/20':high?'bg-amber-500/6 border-amber-500/15':'bg-white/[0.02] border-white/[0.06]')}>
                        <div className="flex items-center justify-between gap-2 mb-1">
                          <div className="flex items-center gap-1.5">
                            <span className={clsx('w-1.5 h-1.5 rounded-full flex-shrink-0',
                              crit?'bg-red-500':high?'bg-amber-500':'bg-slate-500')} />
                            <span className={clsx('text-[11px] font-semibold',
                              crit?'text-red-400':high?'text-amber-400':'text-slate-300')}>
                              {alert.alertType?.replace(/_/g,' ')}
                            </span>
                          </div>
                          <span className="text-[10px] text-slate-500 font-mono">{dayjs(alert.timestamp).fromNow()}</span>
                        </div>
                        <p className="text-xs text-slate-300 leading-relaxed">{alert.message}</p>
                      </div>
                    )
                  }) : (
                    <div className="flex flex-col items-center py-10 text-slate-600">
                      <RiAlertLine size={28} className="mb-2 opacity-20" />
                      <p className="text-xs font-mono">No alerts for {selected}</p>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Live Stats */}
            <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-4">
              <p className="text-[11px] text-slate-400 font-mono uppercase tracking-widest mb-3">
                Live Stats · <span className="text-cyan-400">{selected}</span>
              </p>
              {liveData ? (
                <div className="space-y-0">
                  {[
                    ['Price',    fmtPrice(liveData.price??0), 'text-white'],
                    ['24h High', fmtPrice(liveData.high24h??liveData.high??0), 'text-emerald-400'],
                    ['24h Low',  fmtPrice(liveData.low24h??liveData.low??0), 'text-red-400'],
                    ['Change',   `${changePct>=0?'+':''}${changePct.toFixed(2)}%`, isUp?'text-emerald-400':'text-red-400'],
                    ['Volume',   liveData.volume>=1e9?`${(liveData.volume/1e9).toFixed(2)}B`:liveData.volume>=1e6?`${(liveData.volume/1e6).toFixed(2)}M`:(liveData.volume?.toFixed(0)??'—'), 'text-white'],
                  ].map(([k, v, vc]) => (
                    <div key={k} className="flex items-center justify-between py-2 border-b border-white/[0.04] last:border-0">
                      <span className="text-xs text-slate-400 font-mono">{k}</span>
                      <span className={clsx('text-sm font-mono font-medium', vc)}>{v}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="space-y-3">
                  {[...Array(5)].map((_,i) => (
                    <div key={i} className="flex justify-between items-center">
                      <div className="h-3 w-14 bg-white/[0.05] rounded animate-pulse" />
                      <div className="h-3 w-20 bg-white/[0.05] rounded animate-pulse" />
                    </div>
                  ))}
                </div>
              )}
            </div>

            {riskProfile && (
              <div className={clsx('rounded-2xl border p-4', riskBg(riskProfile.compositeRiskScore))}>
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <RiShieldLine size={14} className={riskColor(riskProfile.compositeRiskScore)} />
                    <span className="text-sm font-semibold text-white">Risk Profile</span>
                  </div>
                  <span className={clsx('text-sm font-bold font-mono', riskColor(riskProfile.compositeRiskScore))}>
                    {riskProfile.riskLevel}
                  </span>
                </div>
                <RiskMeter score={riskProfile.compositeRiskScore} />
                <div className="flex justify-between mt-2">
                  <span className="text-[11px] text-slate-400 font-mono">{riskProfile.marketRegime?.replace(/_/g,' ')}</span>
                  <span className="text-[11px] text-slate-400 font-mono">
                    VaR-99: <span className="text-red-400">{riskProfile.var99?.toFixed(1)}%</span>
                  </span>
                </div>
                {riskProfile.riskSummary && (
                  <p className="text-[11px] text-slate-300 font-mono mt-2 pt-2 border-t border-white/[0.06] leading-relaxed">
                    {riskProfile.riskSummary}
                  </p>
                )}
              </div>
            )}

            <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-4">
              <p className="text-[11px] text-slate-400 font-mono uppercase tracking-widest mb-3">Quick Links</p>
              <div className="grid grid-cols-2 gap-2">
                {[
                  ['/watchlist', RiBookmarkLine,  'Watchlist', 'text-cyan-400 bg-cyan-500/8 border-cyan-500/20 hover:bg-cyan-500/15'],
                  ['/profile',   RiSettings3Line, 'Settings',  'text-slate-300 bg-white/[0.025] border-white/[0.07] hover:bg-white/[0.05]'],
                ].map(([to, Icon, label, cls]) => (
                  <Link key={to} to={to}
                    className={clsx('flex items-center gap-2 p-2.5 rounded-xl border text-xs font-medium transition-all', cls)}>
                    <Icon size={13}/>{label}
                  </Link>
                ))}
              </div>
            </div>

          </div>{/* end right sidebar */}
        </div>
      </div>
    </div>
  )
}