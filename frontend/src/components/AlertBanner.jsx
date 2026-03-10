/**
 * AlertBanner.jsx
 * - Fixed bell icon (bottom-right corner)
 * - Badge shows unread count
 * - Click bell → notification panel slides open
 * - Click any alert → expands to show full detail
 * - Dismiss individual alerts or clear all
 *
 * Place <AlertBanner /> ONCE in App.jsx
 */
import { useState, useRef, useEffect } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { usePrices } from '../context/PriceContext'
import {
  RiBellLine, RiBellFill, RiCloseLine,
  RiArrowUpLine, RiArrowDownLine,
  RiAlertFill, RiCheckDoubleLine,
  RiErrorWarningLine, RiPulseLine,
} from 'react-icons/ri'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
dayjs.extend(relativeTime)

/* ── Demo alerts (used when PriceContext has no alerts) ── */
const DEMO_ALERTS = [
  { id: 'd1', symbol: 'RELIANCE', priceChange: 3.24,  message: 'Unusual volume spike detected. Price broke above 20-day resistance at ₹2,940. Strong momentum signal.', timestamp: Date.now() - 120000,  type: 'breakout' },
  { id: 'd2', symbol: 'BTCUSDT',  priceChange: -2.18, message: 'Flash crash alert. Dropped 2.18% in under 5 minutes. Increased sell pressure on Binance.',             timestamp: Date.now() - 480000,  type: 'crash'    },
  { id: 'd3', symbol: 'TCS',      priceChange: 1.87,  message: 'AI signal changed to BUY. Momentum indicators turning positive after 3-day consolidation.',              timestamp: Date.now() - 900000,  type: 'signal'   },
  { id: 'd4', symbol: 'HDFCBANK', priceChange: -0.94, message: 'Below 50-day moving average. Bearish crossover on MACD. Watch support at ₹1,620.',                       timestamp: Date.now() - 1800000, type: 'warning'  },
  { id: 'd5', symbol: 'ETHUSDT',  priceChange: 4.12,  message: 'Major breakout. ETH crossed $3,500 with 3x average volume. Next resistance at $3,750.',                  timestamp: Date.now() - 3600000, type: 'breakout' },
]

const TYPE_CONFIG = {
  breakout: { color: 'text-emerald-400', bg: 'bg-emerald-500/10', border: 'border-emerald-500/25', dot: 'bg-emerald-400' },
  crash:    { color: 'text-rose-400',    bg: 'bg-rose-500/10',    border: 'border-rose-500/25',    dot: 'bg-rose-400'    },
  signal:   { color: 'text-amber-400',   bg: 'bg-amber-500/10',   border: 'border-amber-500/25',   dot: 'bg-amber-400'   },
  warning:  { color: 'text-orange-400',  bg: 'bg-orange-500/10',  border: 'border-orange-500/25',  dot: 'bg-orange-400'  },
  default:  { color: 'text-slate-400',   bg: 'bg-slate-500/10',   border: 'border-slate-500/20',   dot: 'bg-slate-400'   },
}

function AlertItem({ alert, onDismiss }) {
  const [expanded, setExpanded] = useState(false)
  const isUp = (alert.priceChange ?? 0) > 0
  const cfg  = TYPE_CONFIG[alert.type] || (isUp ? TYPE_CONFIG.breakout : TYPE_CONFIG.crash)

  return (
    <motion.div layout
      initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, x: 60 }}
      className={`rounded-xl border ${cfg.border} ${cfg.bg} overflow-hidden cursor-pointer transition-all duration-200 hover:brightness-110`}
      onClick={() => setExpanded(v => !v)}>
      <div className="px-3.5 py-3 flex items-start gap-3">
        {/* Left dot + icon */}
        <div className="flex flex-col items-center gap-1 pt-0.5 shrink-0">
          <span className={`w-2 h-2 rounded-full ${cfg.dot} shrink-0`} />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between mb-1">
            <div className="flex items-center gap-2">
              <span className="text-[13px] font-black text-white font-mono tracking-wide">
                {alert.symbol.replace('USDT', '')}
              </span>
              <span className={`text-[10px] font-bold font-mono ${isUp ? 'text-emerald-400' : 'text-rose-400'} flex items-center gap-0.5`}>
                {isUp ? <RiArrowUpLine /> : <RiArrowDownLine />}
                {isUp ? '+' : ''}{(alert.priceChange ?? 0).toFixed(2)}%
              </span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-[9px] text-slate-600 font-mono">{dayjs(alert.timestamp).fromNow()}</span>
              <button
                onClick={e => { e.stopPropagation(); onDismiss(alert.id) }}
                className="text-slate-700 hover:text-slate-400 transition-colors bg-transparent border-none cursor-pointer p-0 flex items-center">
                <RiCloseLine className="text-xs" />
              </button>
            </div>
          </div>

          <p className={`text-[11px] text-slate-400 leading-relaxed ${expanded ? '' : 'truncate'}`}>
            {alert.message}
          </p>

          <AnimatePresence>
            {expanded && (
              <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}
                className="mt-2 pt-2 border-t border-white/5">
                <div className="flex items-center gap-3 flex-wrap">
                  <span className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded-full border uppercase ${cfg.color} ${cfg.border} ${cfg.bg}`}>
                    {alert.type || 'alert'}
                  </span>
                  <span className="text-[9px] text-slate-600 font-mono">
                    {dayjs(alert.timestamp).format('HH:mm:ss · DD MMM')}
                  </span>
                  <span className="text-[9px] text-slate-600">Tap to collapse</span>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </motion.div>
  )
}

export default function AlertBanner() {
  const { alerts: ctxAlerts } = usePrices?.() || {}
  const [open,      setOpen]      = useState(false)
  const [dismissed, setDismissed] = useState(new Set())
  const [pulse,     setPulse]     = useState(false)
  const panelRef = useRef(null)
  const prevCount = useRef(0)

  // Use context alerts if available, else demo
  const rawAlerts = (ctxAlerts?.length ? ctxAlerts : DEMO_ALERTS)
    .map(a => ({ ...a, id: a.id || a.timestamp, type: a.type || ((a.priceChange ?? 0) > 0 ? 'breakout' : 'crash') }))

  const visible    = rawAlerts.filter(a => !dismissed.has(a.id))
  const unreadCnt  = visible.length

  // Pulse bell when new alerts arrive
  useEffect(() => {
    if (unreadCnt > prevCount.current) { setPulse(true); setTimeout(() => setPulse(false), 1500) }
    prevCount.current = unreadCnt
  }, [unreadCnt])

  // Close panel on outside click
  useEffect(() => {
    const fn = e => { if (panelRef.current && !panelRef.current.contains(e.target)) setOpen(false) }
    if (open) document.addEventListener('mousedown', fn)
    return () => document.removeEventListener('mousedown', fn)
  }, [open])

  const dismiss    = id  => setDismissed(s => new Set([...s, id]))
  const dismissAll = () => setDismissed(new Set(rawAlerts.map(a => a.id)))

  return (
    <div className="fixed bottom-6 right-6 z-[999]" ref={panelRef}>

      {/* ── Notification Panel ── */}
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 16, scale: 0.95 }}
            animate={{ opacity: 1, y: 0,  scale: 1    }}
            exit={{ opacity: 0, y: 16, scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 340, damping: 28 }}
            className="absolute bottom-16 right-0 w-[340px] bg-[#0c1a0c] border border-emerald-900/40
                       rounded-2xl shadow-[0_24px_60px_rgba(0,0,0,.75)] overflow-hidden">

            {/* Header */}
            <div className="px-4 py-3.5 border-b border-emerald-900/25 flex items-center justify-between
                            bg-gradient-to-r from-[#0f2010] to-[#0c1a0c]">
              <div className="flex items-center gap-2.5">
                <RiPulseLine className="text-emerald-400 text-base" />
                <span className="font-bold text-sm text-white">Market Alerts</span>
                {unreadCnt > 0 && (
                  <span className="text-[10px] bg-emerald-500 text-white font-black px-1.5 py-0.5 rounded-full min-w-[18px] text-center">
                    {unreadCnt}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                {unreadCnt > 0 && (
                  <button onClick={dismissAll}
                    className="flex items-center gap-1 text-[10px] font-semibold text-slate-500
                               hover:text-emerald-400 transition-colors bg-transparent border-none cursor-pointer">
                    <RiCheckDoubleLine className="text-[11px]" /> Clear all
                  </button>
                )}
                <button onClick={() => setOpen(false)}
                  className="text-slate-500 hover:text-white transition-colors bg-transparent border-none cursor-pointer flex items-center">
                  <RiCloseLine className="text-base" />
                </button>
              </div>
            </div>

            {/* Alert list */}
            <div className="max-h-[400px] overflow-y-auto p-3 space-y-2">
              <AnimatePresence mode="popLayout">
                {visible.length === 0 ? (
                  <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                    className="flex flex-col items-center justify-center py-12 text-slate-700">
                    <RiBellLine className="text-4xl mb-3 opacity-30" />
                    <p className="text-sm font-semibold">No active alerts</p>
                    <p className="text-xs mt-1 text-slate-800">Price anomalies will appear here</p>
                  </motion.div>
                ) : visible.map(alert => (
                  <AlertItem key={alert.id} alert={alert} onDismiss={dismiss} />
                ))}
              </AnimatePresence>
            </div>

            {/* Footer */}
            <div className="px-4 py-2.5 border-t border-emerald-900/20 flex items-center gap-2">
              <RiErrorWarningLine className="text-slate-700 text-xs" />
              <span className="text-[10px] text-slate-700 font-mono">
                Anomaly detection · Live WebSocket feed
              </span>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Bell Button ── */}
      <motion.button
        onClick={() => setOpen(v => !v)}
        whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}
        className={`relative w-12 h-12 rounded-full flex items-center justify-center cursor-pointer border-none
                    shadow-[0_8px_28px_rgba(0,0,0,.6)] transition-all duration-200
                    ${open
                      ? 'bg-gradient-to-br from-emerald-500 to-teal-600 shadow-emerald-500/30'
                      : 'bg-[#0d1a0d] border border-emerald-900/40 hover:border-emerald-500/50'}`}>

        {/* Ripple pulse when new alert */}
        {pulse && (
          <span className="absolute inset-0 rounded-full border-2 border-emerald-400 animate-ping opacity-60" />
        )}

        {/* Bell icon */}
        {open
          ? <RiBellFill className="text-white text-xl" />
          : <RiBellLine className={`text-xl ${unreadCnt > 0 ? 'text-emerald-400' : 'text-slate-500'}`} />
        }

        {/* Unread badge */}
        {unreadCnt > 0 && !open && (
          <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] bg-rose-500 text-white
                           text-[9px] font-black rounded-full flex items-center justify-center px-1
                           border-2 border-[#070d07]">
            {unreadCnt > 9 ? '9+' : unreadCnt}
          </span>
        )}
      </motion.button>
    </div>
  )
}