import { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { apiService } from '../services/apiService'
import { usePrices } from '../context/PriceContext'
import { useCurrency } from '../hooks/useCurrency'
import { wsService } from '../services/websocketService'
import {
  RiBookmarkLine, RiAddLine, RiDeleteBin6Line,
  RiArrowUpLine, RiArrowDownLine, RiSearchLine,
  RiRefreshLine, RiShieldLine
} from 'react-icons/ri'
import toast from 'react-hot-toast'
import clsx from 'clsx'
import dayjs from 'dayjs'

// ── Market hours (IST) ────────────────────────────────────────────────────────
function isNseOpen() {
  const now  = new Date()
  const utc  = now.getTime() + now.getTimezoneOffset() * 60000
  const ist  = new Date(utc + 5.5 * 3600000)
  const day  = ist.getDay()
  const mins = ist.getHours() * 60 + ist.getMinutes()
  if (day === 0 || day === 6) return false
  return mins >= 9 * 60 + 15 && mins < 15 * 60 + 30
}

function isCrypto(sym) {
  return sym?.endsWith('USDT') || sym?.endsWith('BTC') || sym?.endsWith('ETH')
}

const riskColor = (score) => {
  if (!score) return 'text-slate-600'
  if (score >= 75) return 'text-red-400'
  if (score >= 50) return 'text-amber-400'
  if (score >= 25) return 'text-cyan-400'
  return 'text-emerald-400'
}

// ── Suggestions for quick add ─────────────────────────────────────────────────
const CRYPTO_SUGGESTIONS = ['BTCUSDT','ETHUSDT','SOLUSDT','BNBUSDT','XRPUSDT','ADAUSDT','DOGEUSDT','AVAXUSDT']
const NSE_SUGGESTIONS    = ['RELIANCE','INFY','TCS','HDFCBANK','ICICIBANK','WIPRO','SBIN','ADANIENT','ITC','LT']

export default function WatchlistPanel({ onSelect }) {
  const { cryptoPrices, stockPrices } = usePrices()
  const { format } = useCurrency()

  const [watchlist, setWatchlist] = useState(null)
  const [input,     setInput]     = useState('')
  const [tab,       setTab]       = useState('crypto')        // crypto | stock
  const [loading,   setLoading]   = useState(true)
  const [adding,    setAdding]    = useState(false)
  const [showSugg,  setShowSugg]  = useState(false)
  const [riskData,  setRiskData]  = useState({})             // symbol → riskScore
  const marketOpen = isNseOpen()

  const load = useCallback(() => {
    setLoading(true)
    apiService.get('/api/watchlist')
      .then(r => setWatchlist(r.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  // Subscribe to WebSocket for all watchlist symbols
  useEffect(() => {
    const symbols = [
      ...(watchlist?.cryptoSymbols || []).map(e => e.symbol ?? e),
      ...(watchlist?.stockSymbols  || []).map(e => e.symbol ?? e),
    ]
    symbols.forEach(sym => wsService.subscribeSymbol(sym))
  }, [watchlist])

  // Fetch risk scores for watchlist symbols
  useEffect(() => {
    const symbols = tab === 'crypto'
      ? (watchlist?.cryptoSymbols || []).map(e => e.symbol ?? e)
      : (watchlist?.stockSymbols  || []).map(e => e.symbol ?? e)

    if (symbols.length === 0) return
    const sym = symbols.join(',')
    apiService.get(`/api/risk/portfolio?symbols=${sym}`)
      .then(r => {
        const map = {}
        ;(r.data?.symbolRisks || []).forEach(s => { map[s.symbol] = s.riskScore })
        setRiskData(map)
      })
      .catch(() => {})
  }, [watchlist, tab])

  const addSymbol = async () => {
    const sym = input.toUpperCase().trim()
    if (!sym) { setShowSugg(true); return }
    setAdding(true)
    try {
      await apiService.post(`/api/watchlist/${tab}?symbol=${sym}&name=${sym}`)
      toast.success(`${sym} added to watchlist`)
      setInput(''); setShowSugg(false); load()
    } catch (e) {
      toast.error(e.response?.data?.message || `Failed to add ${sym}`)
    } finally { setAdding(false) }
  }

  const remove = async (sym, type) => {
    try {
      await apiService.delete(`/api/watchlist/${type}?symbol=${sym}`)
      toast.success(`${sym} removed`)
      load()
    } catch { toast.error('Failed to remove') }
  }

  const getLive = (sym) => tab === 'crypto' ? cryptoPrices[sym] : stockPrices[sym]

  const symbols = (tab === 'crypto'
    ? (watchlist?.cryptoSymbols || [])
    : (watchlist?.stockSymbols  || [])
  ).map(e => e.symbol ?? e)

  const suggestions = (tab === 'crypto' ? CRYPTO_SUGGESTIONS : NSE_SUGGESTIONS)
    .filter(s => !symbols.includes(s) && s.includes(input.toUpperCase()))
    .slice(0, 6)

  return (
    <div className="glass-card p-4">
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-1.5">
          <RiBookmarkLine className="text-cyan-400" size={14} />
          <h3 className="font-display font-semibold text-slate-100 text-sm">Watchlist</h3>
        </div>
        <button onClick={load} className="text-slate-600 hover:text-slate-400 transition-colors p-1 rounded-lg hover:bg-white/5">
          <RiRefreshLine size={13} />
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-3 p-1 bg-white/[0.03] rounded-xl">
        {['crypto','stock'].map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={clsx('flex-1 py-1.5 rounded-lg text-[11px] font-semibold transition-all',
              tab === t ? 'bg-cyan-500 text-slate-950 shadow-[0_0_8px_#06b6d430]'
                       : 'text-slate-500 hover:text-slate-300')}>
            {t === 'crypto' ? '₿ Crypto' : '📊 Stocks'}
          </button>
        ))}
      </div>

      {/* Stock market status */}
      {tab === 'stock' && (
        <div className={clsx('flex items-center gap-1.5 text-[10px] font-mono px-2.5 py-1.5 rounded-lg mb-3 border',
          marketOpen
            ? 'bg-emerald-500/8 border-emerald-500/20 text-emerald-400'
            : 'bg-white/[0.03] border-white/[0.06] text-slate-600')}>
          <span className={clsx('w-1.5 h-1.5 rounded-full', marketOpen ? 'bg-emerald-400' : 'bg-slate-600')} />
          NSE {marketOpen ? 'OPEN — prices live' : 'CLOSED — prices delayed'}
        </div>
      )}

      {/* Add input */}
      <div className="relative mb-3">
        <div className="flex gap-1.5">
          <div className="relative flex-1">
            <RiSearchLine className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-600" size={13} />
            <input
              value={input}
              onChange={e => { setInput(e.target.value); setShowSugg(true) }}
              onFocus={() => setShowSugg(true)}
              onBlur={() => setTimeout(() => setShowSugg(false), 200)}
              onKeyDown={e => e.key === 'Enter' && addSymbol()}
              placeholder={tab === 'crypto' ? 'BTCUSDT…' : 'RELIANCE…'}
              className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl pl-8 pr-3 py-2
                         text-xs font-mono text-slate-200 placeholder-slate-600
                         focus:outline-none focus:border-cyan-500/40 focus:bg-white/[0.06] transition-all" />
          </div>
          <button onClick={addSymbol} disabled={adding}
            className="bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-slate-950
                       px-3 rounded-xl flex items-center gap-1 text-xs font-semibold transition-colors">
            <RiAddLine size={14} />
          </button>
        </div>

        {/* Suggestions dropdown */}
        <AnimatePresence>
          {showSugg && suggestions.length > 0 && (
            <motion.div
              initial={{ opacity:0, y:-4 }} animate={{ opacity:1, y:0 }} exit={{ opacity:0, y:-4 }}
              className="absolute top-full left-0 right-0 mt-1 z-50 bg-[#0E1628] border border-white/[0.1]
                         rounded-xl overflow-hidden shadow-xl">
              {suggestions.map(sym => (
                <button key={sym}
                  onMouseDown={() => { setInput(sym); setShowSugg(false) }}
                  className="w-full flex items-center justify-between px-3 py-2 text-xs font-mono
                             text-slate-400 hover:bg-white/[0.06] hover:text-slate-200 transition-colors">
                  <span>{sym}</span>
                  <span className="text-[10px] text-slate-600">+ Add</span>
                </button>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Symbol list */}
      <div className="space-y-1.5 max-h-[360px] overflow-y-auto pr-1
                      scrollbar-thin scrollbar-thumb-white/10 scrollbar-track-transparent">
        {loading ? (
          [...Array(4)].map((_,i) => (
            <div key={i} className="h-14 rounded-xl bg-white/[0.03] animate-pulse" />
          ))
        ) : symbols.length === 0 ? (
          <div className="text-center py-8">
            <RiBookmarkLine size={24} className="mx-auto text-slate-700 mb-2" />
            <p className="text-[11px] text-slate-600 font-mono">No {tab} symbols yet</p>
            <p className="text-[10px] text-slate-700 font-mono mt-1">Search above to add</p>
          </div>
        ) : (
          <AnimatePresence>
            {symbols.map((sym) => {
              const live  = getLive(sym)
              const chg   = live?.priceChange ?? live?.changePercent ?? 0
              const isUp  = chg >= 0
              const risk  = riskData[sym]
              // For stocks: show live price only if market is open
              const showLive = tab === 'crypto' || marketOpen
              const isSelected = false

              return (
                <motion.div key={sym}
                  initial={{ opacity:0, x:-8 }} animate={{ opacity:1, x:0 }}
                  exit={{ opacity:0, x:8 }}
                  className="flex items-center justify-between px-3 py-2.5 rounded-xl
                             bg-white/[0.025] border border-white/[0.05]
                             hover:border-white/[0.1] hover:bg-white/[0.04]
                             transition-all cursor-pointer group"
                  onClick={() => onSelect?.(sym)}>

                  {/* Left: symbol + price */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <p className="text-xs font-mono font-semibold text-slate-200 truncate">
                        {sym.replace('-BSE','').replace('USDT','')}
                      </p>
                      <span className="text-[9px] text-slate-700 font-mono">
                        {isCrypto(sym) ? 'USDT' : sym.includes('-BSE') ? 'BSE' : 'NSE'}
                      </span>
                    </div>
                    {live && showLive ? (
                      <div className="flex items-center gap-1.5 mt-0.5">
                        <span className="text-[11px] font-mono text-slate-300">
                          {format(live.price ?? 0, live.price > 100 ? 2 : 4)}
                        </span>
                        <span className={clsx('text-[10px] font-mono flex items-center gap-0.5',
                          isUp ? 'text-emerald-400' : 'text-red-400')}>
                          {isUp ? <RiArrowUpLine size={9} /> : <RiArrowDownLine size={9} />}
                          {Math.abs(chg).toFixed(2)}%
                        </span>
                      </div>
                    ) : !showLive ? (
                      <p className="text-[10px] text-slate-700 font-mono mt-0.5">Market closed</p>
                    ) : (
                      <div className="h-3 w-20 bg-white/[0.04] rounded animate-pulse mt-1" />
                    )}
                  </div>

                  {/* Right: risk + delete */}
                  <div className="flex items-center gap-2 ml-2">
                    {risk !== undefined && (
                      <div className="flex items-center gap-0.5">
                        <RiShieldLine size={9} className={riskColor(risk)} />
                        <span className={clsx('text-[10px] font-mono', riskColor(risk))}>{risk}</span>
                      </div>
                    )}
                    <button
                      onClick={e => { e.stopPropagation(); remove(sym, tab) }}
                      className="opacity-0 group-hover:opacity-100 text-slate-700
                                 hover:text-red-400 transition-all p-1 rounded-lg">
                      <RiDeleteBin6Line size={12} />
                    </button>
                  </div>
                </motion.div>
              )
            })}
          </AnimatePresence>
        )}
      </div>

      {symbols.length > 0 && (
        <p className="text-[9px] text-slate-700 font-mono text-center mt-2">
          {symbols.length} symbol{symbols.length !== 1 ? 's' : ''} · click to view
        </p>
      )}
    </div>
  )
}