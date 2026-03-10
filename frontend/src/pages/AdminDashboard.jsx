/**
 * AdminDashboard.jsx — Production-ready admin panel
 *
 * FIXES IN THIS VERSION:
 * 1. Navbar 2-row layout — brand+actions TOP row, tabs BOTTOM row — NO overlap ever
 * 2. React.memo on every tab component — prevents re-renders on unrelated state changes
 * 3. useMemo on expensive derived values (filtered user list)
 * 4. Removed framer-motion row animations (was causing 100s of layout recalculations)
 * 5. Tab switch animation: 100ms only (was 150ms+)
 * 6. useCallback on all handlers — stable references between renders
 * 7. All logic, colors, API endpoints UNCHANGED
 */
import { useState, useEffect, useCallback, useRef, useMemo, memo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import { apiService } from '../services/apiService'
import { useAuth } from '../context/AuthContext'
import clsx from 'clsx'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import {
  RiShieldLine, RiUserLine, RiUserFollowLine,
  RiDeleteBin6Line, RiRefreshLine, RiSearchLine,
  RiAddLine, RiStockLine, RiBarChart2Line, RiCloseLine,
  RiBrainLine, RiAlertLine, RiBarChartLine, RiServerLine,
  RiDatabase2Line, RiCpuLine, RiTimerLine, RiPulseLine,
  RiArrowUpLine,
  RiLogoutBoxLine, RiDashboardLine,
  RiLineChartLine, RiGlobalLine, RiCheckLine,
  RiFireLine, RiLoader4Line, RiWifiLine,
  RiErrorWarningLine, RiSignalWifiErrorLine,
} from 'react-icons/ri'

dayjs.extend(relativeTime)

// ── Timeout-wrapped fetch ─────────────────────────────────────────────────────
async function safeFetch(fn, timeout = 8000) {
  try {
    const result = await Promise.race([
      fn(),
      new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), timeout))
    ])
    return { data: result, error: null }
  } catch (e) {
    return { data: null, error: e?.message ?? 'failed' }
  }
}

function toArray(d) {
  if (!d) return []
  if (Array.isArray(d)) return d
  if (d.content && Array.isArray(d.content)) return d.content
  if (d.users  && Array.isArray(d.users))   return d.users
  if (d.alerts && Array.isArray(d.alerts))  return d.alerts
  if (d.data   && Array.isArray(d.data))    return d.data
  return []
}

function parsePrometheus(raw) {
  const out = {}
  if (typeof raw !== 'string') return out
  for (const line of raw.split('\n')) {
    if (!line || line.startsWith('#')) continue
    const m = line.match(/^([a-zA-Z_][a-zA-Z0-9_]*)(\{[^}]*\})?\s+([\d.eE+\-]+)/)
    if (!m) continue
    let name       = m[1]
    const labels   = m[2] ?? ''
    const value    = parseFloat(m[3])
    if (isNaN(value)) continue
    if (labels.includes('quantile="0.5"')  || labels.includes("quantile='0.5'"))  { out[name.replace(/_seconds$/,'')+'_p50'] = value; continue }
    if (labels.includes('quantile="0.95"') || labels.includes("quantile='0.95'")) { out[name.replace(/_seconds$/,'')+'_p95'] = value; continue }
    if (labels.includes('quantile="0.99"') || labels.includes("quantile='0.99'")) { out[name.replace(/_seconds$/,'')+'_p99'] = value; continue }
    if (name.endsWith('_seconds_count') || name.endsWith('_seconds_sum') || name.endsWith('_seconds_max') || (name.endsWith('_count') && name.includes('_seconds'))) continue
    if (labels.includes('le=')) continue
    if (name.endsWith('_total_total')) out[name.slice(0,-6)] = value
    out[name.replace(/_seconds$/,'')] = value
    out[name] = value
  }
  return out
}

const fmtN  = n => n == null ? '—' : n >= 1e9 ? (n/1e9).toFixed(1)+'B' : n >= 1e6 ? (n/1e6).toFixed(1)+'M' : n >= 1e3 ? (n/1e3).toFixed(1)+'K' : String(Math.round(n))
const fmtMs = s => s == null ? '—' : `${(s * 1000).toFixed(1)} ms`

const riskColor = s => s>=75?'text-red-400':s>=50?'text-amber-400':s>=25?'text-cyan-400':'text-emerald-400'
const riskBg    = s => s>=75?'bg-red-500/10 border-red-500/25':s>=50?'bg-amber-500/10 border-amber-500/25':s>=25?'bg-cyan-500/10 border-cyan-500/25':'bg-emerald-500/10 border-emerald-500/25'
const sevColor  = s => s==='CRITICAL'?'text-red-400 bg-red-500/10 border-red-500/25':s==='HIGH'?'text-amber-400 bg-amber-500/10 border-amber-500/20':'text-slate-400 bg-white/[0.04] border-white/[0.08]'

const PRESETS = {
  NSE:    ['ZOMATO','PAYTM','IRCTC','HAL','RVNL','IRFC','PFC','ADANIPORTS','BAJAJFINSV','HCLTECH'],
  BSE:    ['TATAPOWER-BSE','NHPC-BSE','IREDA-BSE','CESC-BSE','TORNTPOWER-BSE'],
  CRYPTO: ['SOLUSDT','AVAXUSDT','LINKUSDT','UNIUSDT','NEARUSDT','APTUSDT','ARBUSDT','OPUSDT','DOTUSDT','ADAUSDT'],
}

const TABS = [
  { id:'overview',    label:'Overview',       icon:RiDashboardLine },
  { id:'users',       label:'Users',          icon:RiUserLine      },
  { id:'alerts',      label:'Alerts',         icon:RiAlertLine     },
  { id:'risk',        label:'Risk',           icon:RiShieldLine    },
  { id:'performance', label:'Performance',    icon:RiBarChartLine  },
  { id:'infra',       label:'Infrastructure', icon:RiServerLine    },
  { id:'symbols',     label:'Symbols',        icon:RiStockLine     },
]

// ═══ Shared atoms ═════════════════════════════════════════════════════════════

function Bar({ value=0, max=100, color='#06b6d4' }) {
  const pct = Math.min((Math.max(value,0)/Math.max(max,1))*100, 100)
  return (
    <div className="h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
      <motion.div className="h-full rounded-full" initial={{width:0}} animate={{width:`${pct}%`}}
        transition={{duration:0.8,ease:'easeOut'}} style={{background:color}}/>
    </div>
  )
}

function Tile({ icon:Icon, label, value, color='text-cyan-400', bg='bg-white/[0.03] border-white/[0.08]' }) {
  return (
    <div className={clsx('rounded-2xl border p-4 flex flex-col gap-3', bg)}>
      <div className="w-9 h-9 rounded-xl bg-white/[0.06] border border-white/[0.1] flex items-center justify-center">
        <Icon size={16} className={color}/>
      </div>
      <div>
        <p className="text-2xl font-black text-white tabular-nums leading-none">{value ?? '—'}</p>
        <p className="text-[11px] text-slate-400 font-mono mt-1">{label}</p>
      </div>
    </div>
  )
}

function SvcRow({ icon:Icon, name, status, detail, color='text-emerald-400' }) {
  const ok = ['UP','CONNECTED','RUNNING','ONLINE'].includes(status?.toUpperCase?.())
  return (
    <div className="flex items-center gap-3 py-3 border-b border-white/[0.05] last:border-0">
      <div className="w-8 h-8 rounded-xl bg-white/[0.04] border border-white/[0.08] flex items-center justify-center flex-shrink-0">
        <Icon size={14} className={color}/>
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-white">{name}</p>
        {detail && <p className="text-[10px] text-slate-500 font-mono truncate">{detail}</p>}
      </div>
      <div className="flex items-center gap-1.5 flex-shrink-0">
        <span className={clsx('w-1.5 h-1.5 rounded-full flex-shrink-0', ok?'bg-emerald-400 animate-pulse':'bg-red-400')}/>
        <span className={clsx('text-[10px] font-mono font-bold', ok?'text-emerald-400':'text-red-400')}>{status}</span>
      </div>
    </div>
  )
}

function Toast({ msg, type, onDone }) {
  useEffect(() => { const t = setTimeout(onDone,3000); return ()=>clearTimeout(t) }, [onDone])
  return (
    <motion.div initial={{opacity:0,y:24}} animate={{opacity:1,y:0}} exit={{opacity:0,y:24}}
      className={clsx('fixed bottom-6 right-6 z-[300] px-4 py-3 rounded-xl border text-sm font-semibold shadow-2xl',
        type==='success'?'bg-emerald-500/15 border-emerald-500/30 text-emerald-300':
        type==='error'  ?'bg-red-500/15 border-red-500/30 text-red-300':'bg-white/[0.08] border-white/[0.15] text-white')}>
      {msg}
    </motion.div>
  )
}

function Offline({ msg='Backend not responding' }) {
  return (
    <div className="flex flex-col items-center py-10 gap-2 text-slate-600">
      <RiSignalWifiErrorLine size={28} className="opacity-30"/>
      <p className="text-xs font-mono">{msg}</p>
    </div>
  )
}

function Spinner() {
  return (
    <div className="flex justify-center py-20">
      <div className="w-8 h-8 border-2 border-cyan-500/30 border-t-cyan-400 rounded-full animate-spin"/>
    </div>
  )
}

// ═══ OVERVIEW ════════════════════════════════════════════════════════════════
const OverviewTab = memo(function OverviewTab({ userStats, alertStats, backtestStats, riskSymbols, recentAlerts, loading }) {
  if (loading) return <Spinner/>
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6 gap-4">
        <Tile icon={RiUserLine}       label="Total Users"       value={userStats?.totalUsers}      color="text-cyan-400"/>
        <Tile icon={RiUserFollowLine} label="Active Users"      value={userStats?.activeUsers}     color="text-emerald-400" bg="bg-emerald-500/[.08] border-emerald-500/20"/>
        <Tile icon={RiAlertLine}      label="Alerts 24h"        value={alertStats?.totalAlerts24h} color="text-amber-400"   bg="bg-amber-500/[.08] border-amber-500/20"/>
        <Tile icon={RiFireLine}       label="Critical Alerts"   value={alertStats?.criticalCount}  color="text-red-400"     bg="bg-red-500/[.08] border-red-500/20"/>
        <Tile icon={RiShieldLine}     label="High-Risk Symbols" value={riskSymbols?.length}        color="text-purple-400"  bg="bg-purple-500/[.08] border-purple-500/20"/>
        <Tile icon={RiBrainLine}      label="AI Accuracy"       value={backtestStats?.averageAccuracy?`${backtestStats.averageAccuracy.toFixed(1)}%`:null} color="text-indigo-400" bg="bg-indigo-500/[.08] border-indigo-500/20"/>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
          <div className="flex items-center gap-2 mb-4">
            <RiFireLine size={14} className="text-red-400"/>
            <span className="text-sm font-semibold text-white">High-Risk Symbols</span>
            <span className="ml-auto text-[10px] text-slate-500 font-mono">score ≥ 70</span>
          </div>
          {riskSymbols?.length ? (
            <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1">
              {riskSymbols.map((r,i) => (
                <div key={r.symbol||i} className={clsx('rounded-xl p-3 border', riskBg(r.compositeRiskScore))}>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-bold text-white font-mono">{r.symbol}</span>
                    <span className={clsx('text-sm font-black font-mono', riskColor(r.compositeRiskScore))}>{r.compositeRiskScore}/100</span>
                  </div>
                  <Bar value={r.compositeRiskScore} max={100} color={r.compositeRiskScore>=75?'#ef4444':r.compositeRiskScore>=50?'#f59e0b':'#06b6d4'}/>
                  <div className="flex justify-between mt-1.5">
                    <span className={clsx('text-[10px] font-mono font-bold', riskColor(r.compositeRiskScore))}>{r.riskLevel}</span>
                    <span className="text-[10px] text-slate-500 font-mono">{r.marketRegime?.replace(/_/g,' ')}</span>
                  </div>
                </div>
              ))}
            </div>
          ) : <Offline msg="No high-risk symbols or /api/risk/high-risk offline"/>}
        </div>
        <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
          <div className="flex items-center gap-2 mb-4">
            <RiAlertLine size={14} className="text-amber-400"/>
            <span className="text-sm font-semibold text-white">Recent Alerts</span>
            {(alertStats?.unreadCount ?? 0) > 0 && (
              <span className="ml-auto text-[10px] text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded-full font-mono">{alertStats.unreadCount} unread</span>
            )}
          </div>
          <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
            {recentAlerts?.length ? recentAlerts.slice(0,10).map((a,i) => (
              <div key={a.id||i} className={clsx('p-3 rounded-xl border', sevColor(a.severity))}>
                <div className="flex items-center justify-between mb-1">
                  <div className="flex items-center gap-1.5">
                    <span className={clsx('w-1.5 h-1.5 rounded-full', a.severity==='CRITICAL'?'bg-red-400':a.severity==='HIGH'?'bg-amber-400':'bg-slate-500')}/>
                    <span className="text-[10px] font-bold font-mono">{a.symbol}</span>
                    <span className="text-[9px] px-1.5 py-0.5 rounded bg-white/[0.06] font-mono text-slate-400">{a.alertType?.replace(/_/g,' ')}</span>
                  </div>
                  <span className="text-[9px] text-slate-600 font-mono">{dayjs(a.timestamp).fromNow()}</span>
                </div>
                <p className="text-[10px] text-slate-300 leading-snug">{a.message}</p>
              </div>
            )) : <Offline msg="No recent alerts or /api/alerts/market/recent offline"/>}
          </div>
        </div>
      </div>
      {alertStats && (
        <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
          <p className="text-sm font-semibold text-white mb-4">Alert Breakdown — Last 24h</p>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
            {[
              ['Total',      alertStats.totalAlerts24h,   'text-white',      '#ffffff'],
              ['Unread',     alertStats.unreadCount,      'text-amber-400',  '#f59e0b'],
              ['Critical',   alertStats.criticalCount,    'text-red-400',    '#ef4444'],
              ['Anomalies',  alertStats.anomalyCount,     'text-purple-400', '#a78bfa'],
              ['Pump/Dump',  alertStats.pumpDumpCount,    'text-orange-400', '#fb923c'],
              ['Vol Spikes', alertStats.volumeSpikeCount, 'text-cyan-400',   '#06b6d4'],
            ].map(([l,v,c,h]) => (
              <div key={l} className="rounded-xl bg-white/[0.03] border border-white/[0.06] p-3 text-center">
                <p className={clsx('text-2xl font-black tabular-nums', c)}>{v ?? 0}</p>
                <p className="text-[10px] text-slate-500 font-mono mt-1">{l}</p>
                <div className="mt-2"><Bar value={v??0} max={alertStats.totalAlerts24h||1} color={h}/></div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
})

// ═══ USERS ════════════════════════════════════════════════════════════════════
const UsersTab = memo(function UsersTab({ users, onToggle, onDelete, actionId, loading, error }) {
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')

  const shown = useMemo(() => users.filter(u => {
    const q = search.toLowerCase()
    const ms = !q || (u.username||'').toLowerCase().includes(q) || (u.email||'').toLowerCase().includes(q)
    const mf = filter==='all' || (filter==='active'&&u.enabled) || (filter==='disabled'&&!u.enabled)
    return ms && mf
  }), [users, search, filter])

  if (loading) return <Spinner/>
  return (
    <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] overflow-hidden">
      <div className="p-4 border-b border-white/[0.06] flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[200px]">
          <RiSearchLine className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={13}/>
          <input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search username or email…"
            className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl pl-9 pr-4 py-2 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-cyan-500/40 font-mono"/>
        </div>
        <div className="flex gap-0.5 p-0.5 bg-white/[0.04] rounded-xl border border-white/[0.07]">
          {['all','active','disabled'].map(f=>(
            <button key={f} onClick={()=>setFilter(f)}
              className={clsx('px-3 py-1.5 rounded-[10px] text-[10px] font-mono font-bold transition-all capitalize',
                filter===f?'bg-cyan-500 text-slate-950':'text-slate-500 hover:text-white')}>
              {f}
            </button>
          ))}
        </div>
        <span className="text-[11px] text-slate-500 font-mono">{shown.length}/{users.length}</span>
      </div>
      {error ? (
        <div className="flex flex-col items-center py-16 gap-3">
          <RiErrorWarningLine size={28} className="text-red-400 opacity-60"/>
          <p className="text-sm font-mono text-red-400">Could not load users from /api/admin/users</p>
          <p className="text-xs font-mono text-slate-600">{error}</p>
        </div>
      ) : users.length === 0 ? (
        <div className="flex flex-col items-center py-16 gap-3 text-slate-600">
          <RiUserLine size={28} className="opacity-20"/>
          <p className="text-sm font-mono">No users returned from backend</p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-white/[0.06]">
                  {['User','Email','Mobile','Role','Status','Joined','Actions'].map(h=>(
                    <th key={h} className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {shown.map((u,i) => {
                  const isAdm = u.role==='ADMIN' || u.roles?.some?.(r=>r.includes('ADMIN'))
                  return (
                    <tr key={u.id||u.username||i} className="border-b border-white/[0.04] hover:bg-white/[0.025] transition-colors">
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-2.5">
                          <div className="w-8 h-8 rounded-xl bg-cyan-500/15 border border-cyan-500/25 flex items-center justify-center text-xs font-black text-cyan-400 flex-shrink-0">
                            {(u.username||u.email||'?')[0].toUpperCase()}
                          </div>
                          <span className="text-sm font-semibold text-white whitespace-nowrap">{u.username ?? '—'}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-400 font-mono whitespace-nowrap">{u.email ?? '—'}</td>
                      <td className="px-5 py-3.5 text-xs text-slate-400 font-mono whitespace-nowrap">{u.mobileNumber ?? u.mobile ?? '—'}</td>
                      <td className="px-5 py-3.5">
                        <span className={clsx('text-[10px] px-2 py-0.5 rounded-lg font-bold border whitespace-nowrap',
                          isAdm?'bg-amber-500/10 text-amber-400 border-amber-500/20':'bg-emerald-500/10 text-emerald-400 border-emerald-500/20')}>
                          {isAdm?'ADMIN':'USER'}
                        </span>
                      </td>
                      <td className="px-5 py-3.5">
                        <span className={clsx('text-[10px] px-2 py-0.5 rounded-lg font-bold border whitespace-nowrap',
                          u.enabled?'bg-emerald-500/10 text-emerald-400 border-emerald-500/20':'bg-red-500/10 text-red-400 border-red-500/20')}>
                          {u.enabled?'● Active':'● Disabled'}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-500 font-mono whitespace-nowrap">
                        {u.createdAt ? dayjs(u.createdAt).format('DD MMM YYYY') : '—'}
                      </td>
                      <td className="px-5 py-3.5">
                        {isAdm ? (
                          <span className="text-[10px] text-slate-700 font-mono">Protected</span>
                        ) : (
                          <div className="flex items-center gap-2">
                            <button onClick={()=>onToggle(u.id)} disabled={actionId===u.id+'_t'}
                              className={clsx('text-[10px] px-3 py-1.5 rounded-lg border font-semibold transition-all disabled:opacity-40 whitespace-nowrap',
                                u.enabled?'border-amber-500/25 text-amber-400 hover:bg-amber-500/10':'border-emerald-500/25 text-emerald-400 hover:bg-emerald-500/10')}>
                              {actionId===u.id+'_t'?<RiLoader4Line size={11} className="animate-spin"/>:u.enabled?'Disable':'Enable'}
                            </button>
                            <button onClick={()=>onDelete(u.id)} disabled={actionId===u.id+'_d'}
                              className="p-1.5 rounded-lg border border-red-500/20 text-red-400 hover:bg-red-500/10 transition-all disabled:opacity-40">
                              {actionId===u.id+'_d'?<RiLoader4Line size={11} className="animate-spin"/>:<RiDeleteBin6Line size={12}/>}
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {shown.length===0 && <p className="text-center py-12 text-slate-600 text-sm">No users match filter</p>}
        </>
      )}
    </div>
  )
})

// ═══ ALERTS ═══════════════════════════════════════════════════════════════════
const AlertsTab = memo(function AlertsTab({ alerts, pumpDump, anomalies, onAck, loading }) {
  const [filter, setFilter] = useState('ALL')
  const shown = filter==='ALL'?alerts:filter==='PUMP_DUMP'?pumpDump:anomalies
  if (loading) return <Spinner/>
  return (
    <div className="space-y-4">
      <div className="flex gap-2 flex-wrap">
        {[['ALL','All Alerts',alerts],['PUMP_DUMP','Pump/Dump',pumpDump],['ANOMALY','Anomalies',anomalies]].map(([v,l,arr])=>(
          <button key={v} onClick={()=>setFilter(v)}
            className={clsx('px-4 py-2 rounded-xl text-xs font-semibold border transition-all',
              filter===v?'bg-cyan-500 text-slate-950 border-cyan-500':'bg-white/[0.04] border-white/[0.08] text-slate-400 hover:text-white')}>
            {l} <span className="opacity-60 text-[10px]">({arr?.length ?? 0})</span>
          </button>
        ))}
      </div>
      <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] overflow-hidden">
        {!shown?.length ? (
          <Offline msg={`No ${filter==='ALL'?'':filter.toLowerCase()+' '}alerts — backend may be offline`}/>
        ) : (
          <div className="overflow-x-auto max-h-[600px] overflow-y-auto">
            <table className="w-full">
              <thead className="sticky top-0 bg-[#0B1425] z-10 border-b border-white/[0.06]">
                <tr>
                  {['Symbol','Type','Severity','Message','Time','Action'].map(h=>(
                    <th key={h} className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {shown.map((a,i) => (
                  <tr key={a.id||i} className="border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors">
                    <td className="px-5 py-3 text-xs font-bold text-white font-mono whitespace-nowrap">{a.symbol}</td>
                    <td className="px-5 py-3"><span className="text-[10px] text-slate-300 font-mono bg-white/[0.05] px-2 py-0.5 rounded whitespace-nowrap">{a.alertType?.replace(/_/g,' ')}</span></td>
                    <td className="px-5 py-3"><span className={clsx('text-[10px] font-bold px-2 py-0.5 rounded border whitespace-nowrap', sevColor(a.severity))}>{a.severity}</span></td>
                    <td className="px-5 py-3 max-w-xs"><p className="text-xs text-slate-400 truncate">{a.message}</p></td>
                    <td className="px-5 py-3 text-[10px] text-slate-500 font-mono whitespace-nowrap">{dayjs(a.timestamp).fromNow()}</td>
                    <td className="px-5 py-3">
                      {a.acknowledged
                        ? <span className="text-[9px] text-emerald-400 font-mono flex items-center gap-1"><RiCheckLine size={9}/>ACK</span>
                        : <button onClick={()=>onAck(a.id)} className="text-[9px] px-2 py-1 rounded-lg border border-cyan-500/25 text-cyan-400 hover:bg-cyan-500/10 font-mono transition-all">ACK</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
})

// ═══ RISK ═════════════════════════════════════════════════════════════════════
const RiskTab = memo(function RiskTab({ riskSymbols, pumpDumpSyms, volumeSpikes, loading }) {
  if (loading) return <Spinner/>
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
          <div className="flex items-center gap-2 mb-4">
            <RiShieldLine size={14} className="text-purple-400"/>
            <span className="text-sm font-semibold text-white">High-Risk Symbols</span>
            <span className="ml-auto text-[10px] font-mono text-slate-500">{riskSymbols?.length ?? 0} symbols</span>
          </div>
          {riskSymbols?.length ? (
            <div className="space-y-3 max-h-[500px] overflow-y-auto pr-1">
              {riskSymbols.map((r,i) => (
                <div key={r.symbol||i} className={clsx('rounded-xl p-4 border', riskBg(r.compositeRiskScore))}>
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <span className="text-sm font-bold text-white font-mono">{r.symbol}</span>
                      <span className="text-[10px] text-slate-500 font-mono ml-2">{r.marketRegime?.replace(/_/g,' ')}</span>
                    </div>
                    <div className="text-right">
                      <span className={clsx('text-xl font-black font-mono', riskColor(r.compositeRiskScore))}>{r.compositeRiskScore}</span>
                      <span className="text-slate-500 text-sm">/100</span>
                    </div>
                  </div>
                  <Bar value={r.compositeRiskScore} max={100} color={r.compositeRiskScore>=75?'#ef4444':r.compositeRiskScore>=50?'#f59e0b':'#06b6d4'}/>
                  <div className="grid grid-cols-3 gap-2 mt-3">
                    {[['Volatility',r.priceVolatilityScore],['Vol Anomaly',r.volumeAnomalyScore],['Momentum',r.momentumRiskScore]].map(([l,v])=>(
                      <div key={l}>
                        <p className="text-[9px] text-slate-500 font-mono">{l}</p>
                        <p className={clsx('text-xs font-bold font-mono', riskColor(v??0))}>{v??'—'}</p>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          ) : <Offline msg="No high-risk symbols — /api/risk/high-risk?minScore=70"/>}
        </div>
        <div className="space-y-4">
          {[
            { title:'Pump/Dump Detected', items:pumpDumpSyms, color:'text-orange-400', border:'border-orange-500/20', icon:RiFireLine,     val: s=>`${s.pumpDumpProbability?.toFixed(0)}%` },
            { title:'Volume Spikes',      items:volumeSpikes,  color:'text-cyan-400',   border:'border-cyan-500/20',   icon:RiBarChart2Line, val: s=>`${s.volumeRatio?.toFixed(2)}×` },
          ].map(g => (
            <div key={g.title} className={clsx('rounded-2xl bg-[#0B1425] border p-4', g.border)}>
              <div className="flex items-center gap-2 mb-3">
                <g.icon size={13} className={g.color}/>
                <span className="text-sm font-semibold text-white">{g.title}</span>
              </div>
              {g.items?.length ? (
                <div className="space-y-2 max-h-52 overflow-y-auto">
                  {g.items.slice(0,8).map((s,i) => (
                    <div key={i} className="flex items-center justify-between py-1.5 border-b border-white/[0.04] last:border-0">
                      <span className="text-xs font-bold text-white font-mono">{s.symbol}</span>
                      <span className={clsx('text-xs font-bold font-mono', g.color)}>{g.val(s)}</span>
                    </div>
                  ))}
                </div>
              ) : <Offline msg="None detected"/>}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
})

// ═══ PERFORMANCE ═════════════════════════════════════════════════════════════
const PerformanceTab = memo(function PerformanceTab({ backtestStats, backtestAll, loading }) {
  if (loading) return <Spinner/>
  return (
    <div className="space-y-6">
      {backtestStats && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <Tile icon={RiBrainLine}    label="Avg AI Accuracy"  value={backtestStats.averageAccuracy?`${backtestStats.averageAccuracy.toFixed(1)}%`:null} color="text-indigo-400" bg="bg-indigo-500/[.08] border-indigo-500/20"/>
          <Tile icon={RiBarChartLine} label="Total Backtests"  value={backtestStats.totalBacktests}  color="text-cyan-400"/>
          <Tile icon={RiArrowUpLine}  label="Winning Symbols"  value={backtestStats.winningSymbols}  color="text-emerald-400" bg="bg-emerald-500/[.08] border-emerald-500/20"/>
          <Tile icon={RiTimerLine}    label="Symbols Analyzed" value={backtestStats.symbolsAnalyzed} color="text-slate-400"/>
        </div>
      )}
      <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] overflow-hidden">
        <div className="p-5 border-b border-white/[0.06] flex items-center gap-2">
          <RiLineChartLine size={13} className="text-cyan-400"/>
          <span className="text-sm font-semibold text-white">Per-Symbol Backtest Results</span>
        </div>
        {backtestAll?.length ? (
          <div className="overflow-x-auto max-h-[500px] overflow-y-auto">
            <table className="w-full">
              <thead className="sticky top-0 bg-[#0B1425] border-b border-white/[0.06]">
                <tr>
                  {['Symbol','Accuracy','Win Rate','Trades','Profit Factor','Best Signal','Tested'].map(h=>(
                    <th key={h} className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {backtestAll.map((b,i) => (
                  <tr key={b.symbol||i} className="border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors">
                    <td className="px-5 py-3 text-xs font-bold text-white font-mono">{b.symbol}</td>
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-2 min-w-[80px]">
                        <span className={clsx('text-xs font-bold font-mono',(b.accuracy??0)>=70?'text-emerald-400':(b.accuracy??0)>=50?'text-amber-400':'text-red-400')}>
                          {b.accuracy?.toFixed(1)??'—'}%
                        </span>
                        <div className="flex-1 h-1 bg-white/[0.06] rounded-full overflow-hidden">
                          <div className="h-full rounded-full bg-cyan-400" style={{width:`${b.accuracy??0}%`}}/>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3 text-xs font-mono text-slate-300 whitespace-nowrap">{b.winRate?.toFixed(1)??'—'}%</td>
                    <td className="px-5 py-3 text-xs font-mono text-slate-300">{b.totalTrades??'—'}</td>
                    <td className="px-5 py-3 text-xs font-mono text-slate-300">{b.profitFactor?.toFixed(2)??'—'}</td>
                    <td className="px-5 py-3">
                      <span className={clsx('text-[10px] font-bold px-2 py-0.5 rounded border',
                        b.bestSignal==='BUY'?'bg-emerald-500/10 text-emerald-400 border-emerald-500/20':
                        b.bestSignal==='SELL'?'bg-red-500/10 text-red-400 border-red-500/20':'bg-amber-500/10 text-amber-400 border-amber-500/20')}>
                        {b.bestSignal??'—'}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-[10px] text-slate-500 font-mono whitespace-nowrap">
                      {b.testedAt?dayjs(b.testedAt).format('DD MMM HH:mm'):'—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <Offline msg="No backtest results — /api/backtest/all"/>}
      </div>
    </div>
  )
})

// ═══ INFRASTRUCTURE ═══════════════════════════════════════════════════════════
const InfraTab = memo(function InfraTab({ prom, promRaw, promErr, health, loading }) {
  if (loading) return <Spinner/>
  const comp  = health?.components ?? {}
  const mongo = comp.mongo?.status ?? comp.db?.status ?? 'UNKNOWN'
  const redis = comp.redis?.status ?? 'RUNNING'
  const kafka = comp.kafka?.status ?? 'RUNNING'
  const p = key => prom[key] ?? prom[key+'_total'] ?? null
  const totalCalls = p('marketai_ai_calls_total') ?? 0
  const cacheHits  = p('marketai_ai_cache_hits_total') ?? 0
  const cacheRate  = (totalCalls+cacheHits)>0 ? `${((cacheHits/(totalCalls+cacheHits))*100).toFixed(1)}%` : '—'
  const dailyUsed  = p('marketai_daily_ai_calls_used')
  const dailyStr   = dailyUsed!=null ? `${Math.round(dailyUsed)}/200` : '—'
  const lat = (base, suffix) => prom[base+suffix] ?? prom[base+'_seconds'+suffix] ?? null
  const promTiles = [
    ['AI Calls',      p('marketai_ai_calls_total'),           'text-purple-400'],
    ['Cache Hits',    p('marketai_ai_cache_hits_total'),      'text-emerald-400'],
    ['Anomalies',     p('marketai_anomalies_detected_total'), 'text-amber-400'],
    ['Pump/Dump',     p('marketai_pump_dump_alerts_total'),   'text-orange-400'],
    ['Vol Spikes',    p('marketai_volume_spikes_total'),      'text-cyan-400'],
    ['Alerts Fired',  p('marketai_alerts_fired_total'),       'text-red-400'],
    ['Circ. Breaks',  p('marketai_circuit_breaks_total'),     'text-red-400'],
    ['Rate Limits',   p('marketai_rate_limit_hits_total'),    'text-amber-400'],
    ['Active Syms',   p('marketai_active_symbols'),           'text-cyan-400'],
    ['WS Conns',      p('marketai_websocket_connections'),    'text-indigo-400'],
    ['AI / Day',      dailyStr,                               'text-purple-400'],
    ['Unread Alerts', p('marketai_unread_alerts'),            'text-amber-400'],
  ]
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2"><RiServerLine size={13} className="text-emerald-400"/><span className="text-sm font-semibold text-white">Core Services</span></div>
            <span className={clsx('text-[9px] font-bold px-2 py-0.5 rounded-full border',health?.status==='UP'?'bg-emerald-500/10 text-emerald-400 border-emerald-500/20':'bg-red-500/10 text-red-400 border-red-500/20')}>{health?.status??'UNKNOWN'}</span>
          </div>
          <SvcRow icon={RiCpuLine}       name="Spring Boot" status="UP"        detail="Port 8080 · REST API" color="text-cyan-400"/>
          <SvcRow icon={RiDatabase2Line} name="MongoDB"     status={mongo}     detail="Primary data store"   color="text-emerald-400"/>
          <SvcRow icon={RiGlobalLine}    name="REST API"    status="UP"        detail="/api/* endpoints"     color="text-cyan-400"/>
          <SvcRow icon={RiWifiLine}      name="WebSocket"   status="CONNECTED" detail={p('marketai_websocket_connections')!=null?`${Math.round(p('marketai_websocket_connections'))} active conns`:'STOMP · /ws'} color="text-indigo-400"/>
        </div>
        <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
          <div className="flex items-center gap-2 mb-4"><RiPulseLine size={13} className="text-amber-400"/><span className="text-sm font-semibold text-white">Message Queue</span></div>
          <SvcRow icon={RiDatabase2Line} name="Kafka Broker" status={kafka}   detail="localhost:9092" color="text-amber-400"/>
          <SvcRow icon={RiServerLine}    name="Zookeeper"    status="RUNNING" detail="localhost:2181" color="text-amber-400"/>
          <div className="mt-3 pt-3 border-t border-white/[0.05] space-y-2">
            {[['Kafka Messages',p('marketai_kafka_messages_total')!=null?fmtN(p('marketai_kafka_messages_total')):'—','text-amber-400'],
              ['Consumer Lag',p('marketai_kafka_consumer_lag')!=null?String(Math.round(p('marketai_kafka_consumer_lag'))):'0',(p('marketai_kafka_consumer_lag')??0)>100?'text-red-400':'text-emerald-400'],
            ].map(([l,v,c])=>(
              <div key={l} className="flex justify-between text-xs">
                <span className="text-slate-500 font-mono">{l}</span>
                <span className={clsx('font-bold font-mono',c)}>{v}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
          <div className="flex items-center gap-2 mb-4"><RiDatabase2Line size={13} className="text-red-400"/><span className="text-sm font-semibold text-white">Cache & Monitoring</span></div>
          <SvcRow icon={RiDatabase2Line} name="Redis"      status={redis}   detail="localhost:6379" color="text-red-400"/>
          <SvcRow icon={RiBarChartLine}  name="Prometheus" status="RUNNING" detail="localhost:9090" color="text-orange-400"/>
          <SvcRow icon={RiLineChartLine} name="Grafana"    status="RUNNING" detail="localhost:3000" color="text-orange-400"/>
          <div className="mt-3 pt-3 border-t border-white/[0.05] space-y-2">
            {[['Cache Hit Rate',cacheRate,'text-emerald-400'],['AI Budget Used',dailyStr,'text-purple-400']].map(([l,v,c])=>(
              <div key={l} className="flex justify-between text-xs">
                <span className="text-slate-500 font-mono">{l}</span>
                <span className={clsx('font-bold font-mono',c)}>{v}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2"><RiBarChartLine size={13} className="text-orange-400"/><span className="text-sm font-semibold text-white">Prometheus Metrics</span></div>
          <span className="text-[9px] text-slate-500 font-mono">live · /actuator/prometheus</span>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {promTiles.map(([label,val,cls]) => (
            <div key={label} className="rounded-xl bg-white/[0.03] border border-white/[0.06] p-3 text-center">
              <p className={clsx('text-xl font-black tabular-nums', cls)}>{typeof val==='number'?fmtN(val):(val??'—')}</p>
              <p className="text-[9px] text-slate-500 font-mono mt-1 leading-tight">{label}</p>
            </div>
          ))}
        </div>
      </div>
      <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] p-5">
        <div className="flex items-center gap-2 mb-4"><RiTimerLine size={13} className="text-cyan-400"/><span className="text-sm font-semibold text-white">Latency Distribution (p50 / p95 / p99)</span></div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[
            { name:'AI Call (Gemini)',  color:'text-purple-400', p50:lat('marketai_ai_call_latency','_p50'), p95:lat('marketai_ai_call_latency','_p95'), p99:lat('marketai_ai_call_latency','_p99') },
            { name:'Indicator Calc',   color:'text-cyan-400',   p50:lat('marketai_indicator_calc_latency','_p50'), p95:lat('marketai_indicator_calc_latency','_p95'), p99:lat('marketai_indicator_calc_latency','_p99') },
            { name:'Kafka Processing', color:'text-amber-400',  p50:lat('marketai_kafka_processing_latency','_p50'), p95:lat('marketai_kafka_processing_latency','_p95'), p99:lat('marketai_kafka_processing_latency','_p99') },
          ].map(t => (
            <div key={t.name} className="rounded-xl bg-white/[0.03] border border-white/[0.06] p-4">
              <p className={clsx('text-xs font-bold mb-3', t.color)}>{t.name}</p>
              {[['p50 (median)',t.p50],['p95',t.p95],['p99 (tail)',t.p99]].map(([l,v]) => (
                <div key={l} className="flex justify-between mb-1.5">
                  <span className="text-[10px] text-slate-500 font-mono">{l}</span>
                  <span className={clsx('text-[10px] font-bold font-mono', v!=null?'text-white':'text-slate-700')}>{fmtMs(v)}</span>
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
      {/* DEBUG PANEL */}
      <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] overflow-hidden">
        <div className="p-4 border-b border-white/[0.06] flex items-center justify-between flex-wrap gap-2">
          <div className="flex items-center gap-2">
            <RiErrorWarningLine size={13} className="text-yellow-400"/>
            <span className="text-sm font-semibold text-white">Prometheus Debug</span>
            <span className="text-[10px] text-slate-500 font-mono hidden sm:block">— raw backend output</span>
          </div>
          <span className={clsx('text-[9px] font-mono px-2 py-0.5 rounded-full border',
            promErr?'text-red-400 bg-red-500/10 border-red-500/20':promRaw?'text-emerald-400 bg-emerald-500/10 border-emerald-500/20':'text-slate-500 bg-white/[0.03] border-white/[0.08]')}>
            {promErr?'✗ fetch failed':promRaw?'✓ data received':'not fetched yet'}
          </span>
        </div>
        <div className="p-4 space-y-4">
          {promErr && (
            <div className="rounded-xl bg-red-500/10 border border-red-500/20 p-4">
              <p className="text-xs font-bold text-red-400 mb-1">Fetch Error</p>
              <pre className="text-[10px] text-red-300 font-mono whitespace-pre-wrap">{promErr}</pre>
            </div>
          )}
          {!promErr && promRaw && (() => {
            const allParsed = Object.keys(prom)
            const mktKeys   = allParsed.filter(k=>k.startsWith('marketai'))
            const otherKeys = allParsed.filter(k=>!k.startsWith('marketai'))
            return (
              <div className="space-y-3">
                <p className="text-[10px] font-bold text-emerald-400 font-mono">marketai_* keys parsed: {mktKeys.length}</p>
                {mktKeys.length > 0 ? (
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-1.5">
                    {mktKeys.map(k => (
                      <div key={k} className="flex justify-between gap-2 bg-white/[0.02] rounded-lg px-3 py-1.5 border border-white/[0.05]">
                        <span className="text-[10px] font-mono text-slate-400 truncate">{k}</span>
                        <span className="text-[10px] font-mono text-emerald-400 font-bold flex-shrink-0">{prom[k]}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="rounded-xl bg-amber-500/10 border border-amber-500/20 p-4">
                    <p className="text-xs font-bold text-amber-400 mb-2">No marketai_ metrics found</p>
                    <pre className="text-[9px] text-slate-400 font-mono whitespace-pre-wrap mt-1 max-h-48 overflow-y-auto bg-black/20 rounded p-2">{promRaw.slice(0,800)}</pre>
                  </div>
                )}
                <p className="text-[10px] text-slate-500 font-mono">Other Spring/JVM metrics: {otherKeys.length} keys</p>
              </div>
            )
          })()}
          {!promErr && !promRaw && <p className="text-xs text-slate-600 font-mono">Click Refresh to fetch Prometheus data</p>}
        </div>
      </div>
    </div>
  )
})

// ═══ SYMBOLS ═════════════════════════════════════════════════════════════════
const SymbolsTab = memo(function SymbolsTab({ stockList, onAddPreset, onToggleStock, onRemoveStock, onAddCustom }) {
  const [modal, setModal] = useState(false)
  const [form, setForm] = useState({ symbol:'', exchange:'NSE', name:'' })
  const [t, setT] = useState(null)
  const toast = (msg, type='success') => { setT({msg,type}); setTimeout(()=>setT(null),2500) }
  return (
    <div className="space-y-5">
      <div className="grid md:grid-cols-3 gap-4">
        {[
          { label:'NSE Quick-Add',    exchange:'NSE',    items:PRESETS.NSE,    color:'text-emerald-400', bg:'bg-emerald-500/10 border-emerald-500/20', btn:'border-emerald-500/25 text-emerald-400 hover:bg-emerald-500/10' },
          { label:'BSE Quick-Add',    exchange:'BSE',    items:PRESETS.BSE,    color:'text-teal-400',    bg:'bg-teal-500/10 border-teal-500/15',       btn:'border-teal-500/25 text-teal-400 hover:bg-teal-500/10' },
          { label:'Crypto Quick-Add', exchange:'CRYPTO', items:PRESETS.CRYPTO, color:'text-amber-400',   bg:'bg-amber-500/10 border-amber-500/20',     btn:'border-amber-500/25 text-amber-400 hover:bg-amber-500/10' },
        ].map(g => (
          <div key={g.label} className={clsx('rounded-2xl bg-[#0B1425] border p-4', g.bg)}>
            <h4 className={clsx('font-bold text-xs font-mono uppercase tracking-widest mb-3', g.color)}>{g.label}</h4>
            <div className="flex flex-wrap gap-1.5">
              {g.items.map(sym => (
                <button key={sym} onClick={()=>onAddPreset(sym,g.exchange,toast)}
                  className={clsx('text-[10px] px-2.5 py-1 rounded-lg border font-mono font-bold transition-all hover:scale-105', g.btn)}>
                  + {sym.replace('-BSE','')}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="rounded-2xl bg-[#0B1425] border border-white/[0.07] overflow-hidden">
        <div className="p-5 border-b border-white/[0.06] flex items-center justify-between">
          <span className="text-sm font-semibold text-white">Active Symbols <span className="text-slate-500 font-mono text-xs">({stockList.length})</span></span>
          <button onClick={()=>setModal(true)} className="flex items-center gap-1.5 px-4 py-2 bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold rounded-xl text-xs transition-all">
            <RiAddLine size={13}/> Add Custom
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead><tr className="border-b border-white/[0.06]">
              {['Symbol','Name','Exchange','Status','Actions'].map(h=>(
                <th key={h} className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-500 whitespace-nowrap">{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {stockList.map(s => (
                <tr key={s.symbol} className="border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors">
                  <td className="px-5 py-3 text-xs font-bold text-white font-mono">{s.symbol}</td>
                  <td className="px-5 py-3 text-xs text-slate-400">{s.name}</td>
                  <td className="px-5 py-3">
                    <span className={clsx('text-[10px] px-2 py-0.5 rounded-lg font-mono font-bold border',
                      s.exchange==='NSE'?'bg-emerald-500/10 text-emerald-400 border-emerald-500/20':
                      s.exchange==='BSE'?'bg-teal-500/10 text-teal-400 border-teal-500/20':'bg-amber-500/10 text-amber-400 border-amber-500/20')}>
                      {s.exchange}
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <span className={clsx('text-[10px] px-2 py-0.5 rounded-lg border',
                      s.active?'bg-emerald-500/10 text-emerald-400 border-emerald-500/20':'bg-slate-800 text-slate-500 border-slate-700')}>
                      {s.active?'● Active':'● Inactive'}
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2">
                      <button onClick={()=>onToggleStock(s.symbol)}
                        className={clsx('text-[10px] px-3 py-1.5 rounded-lg border font-semibold transition-all whitespace-nowrap',
                          s.active?'border-amber-500/25 text-amber-400 hover:bg-amber-500/10':'border-emerald-500/25 text-emerald-400 hover:bg-emerald-500/10')}>
                        {s.active?'Disable':'Enable'}
                      </button>
                      <button onClick={()=>{ if(window.confirm(`Remove ${s.symbol}?`)) onRemoveStock(s.symbol) }}
                        className="p-1.5 rounded-lg border border-red-500/20 text-red-400 hover:bg-red-500/10 transition-all">
                        <RiDeleteBin6Line size={12}/>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <AnimatePresence>
        {modal && (
          <div className="fixed inset-0 z-[200] flex items-center justify-center px-4">
            <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={()=>setModal(false)}/>
            <motion.div initial={{opacity:0,scale:0.95}} animate={{opacity:1,scale:1}} exit={{opacity:0,scale:0.95}}
              className="relative bg-[#0B1425] border border-white/[0.12] rounded-2xl p-6 w-full max-w-sm shadow-2xl">
              <div className="flex items-center justify-between mb-5">
                <h3 className="font-bold text-white">Add Custom Symbol</h3>
                <button onClick={()=>setModal(false)} className="text-slate-400 hover:text-white"><RiCloseLine/></button>
              </div>
              <div className="space-y-4">
                <div>
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-2 block">Symbol</label>
                  <input value={form.symbol} onChange={e=>setForm(p=>({...p,symbol:e.target.value.toUpperCase()}))}
                    className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl px-4 py-2.5 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-cyan-500/50 font-mono"
                    placeholder="e.g. ZOMATO or LINKUSDT"/>
                </div>
                <div>
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-2 block">Exchange</label>
                  <select value={form.exchange} onChange={e=>setForm(p=>({...p,exchange:e.target.value}))}
                    className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-cyan-500/50">
                    <option value="NSE">NSE</option><option value="BSE">BSE</option><option value="CRYPTO">Crypto</option>
                  </select>
                </div>
                <div>
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-2 block">Display Name (optional)</label>
                  <input value={form.name} onChange={e=>setForm(p=>({...p,name:e.target.value}))}
                    className="w-full bg-white/[0.04] border border-white/[0.08] rounded-xl px-4 py-2.5 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-cyan-500/50"
                    placeholder="e.g. Zomato Limited"/>
                </div>
                <div className="flex gap-3">
                  <button onClick={()=>setModal(false)} className="flex-1 py-2.5 bg-white/[0.05] hover:bg-white/[0.08] text-slate-300 font-semibold rounded-xl text-sm transition-all">Cancel</button>
                  <button onClick={()=>{ if(!form.symbol.trim()){toast('Symbol required','error');return}; onAddCustom(form,toast); setForm({symbol:'',exchange:'NSE',name:''}); setModal(false) }}
                    className="flex-1 py-2.5 bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold rounded-xl text-sm transition-all">Add Symbol</button>
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
      <AnimatePresence>{t && <Toast msg={t.msg} type={t.type} onDone={()=>setT(null)}/>}</AnimatePresence>
    </div>
  )
})

// ═══ ROOT ═════════════════════════════════════════════════════════════════════
export default function AdminDashboard() {
  const { user, logout, isAdmin, loading: authLoading } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState('overview')

  const [users,        setUsers]        = useState([])
  const [usersErr,     setUsersErr]     = useState(null)
  const [userStats,    setUserStats]    = useState(null)
  const [alertStats,   setAlertStats]   = useState(null)
  const [recentAlerts, setRecentAlerts] = useState([])
  const [pumpDump,     setPumpDump]     = useState([])
  const [anomalies,    setAnomalies]    = useState([])
  const [riskSymbols,  setRiskSymbols]  = useState([])
  const [pumpDumpSyms, setPumpDumpSyms] = useState([])
  const [volumeSpikes, setVolumeSpikes] = useState([])
  const [backtestStats,setBacktestStats]= useState(null)
  const [backtestAll,  setBacktestAll]  = useState([])
  const [health,       setHealth]       = useState(null)
  const [prom,         setProm]         = useState({})
  const [promRaw,      setPromRaw]      = useState(null)
  const [promErr,      setPromErr]      = useState(null)
  const [stockList,    setStockList]    = useState([
    { symbol:'RELIANCE',     exchange:'NSE',    name:'Reliance Industries', active:true },
    { symbol:'TCS',          exchange:'NSE',    name:'Tata Consultancy',    active:true },
    { symbol:'HDFCBANK',     exchange:'NSE',    name:'HDFC Bank',           active:true },
    { symbol:'RELIANCE-BSE', exchange:'BSE',    name:'Reliance Inds (BSE)', active:true },
    { symbol:'BTCUSDT',      exchange:'CRYPTO', name:'Bitcoin / USDT',      active:true },
    { symbol:'ETHUSDT',      exchange:'CRYPTO', name:'Ethereum / USDT',     active:true },
  ])

  const [loadingOverview, setLoadingOverview] = useState(true)
  const [loadingUsers,    setLoadingUsers]    = useState(false)
  const [loadingAlerts,   setLoadingAlerts]   = useState(false)
  const [loadingRisk,     setLoadingRisk]     = useState(false)
  const [loadingPerf,     setLoadingPerf]     = useState(false)
  const [loadingInfra,    setLoadingInfra]    = useState(false)
  const [refreshing,      setRefreshing]      = useState(false)
  const [lastRefresh,     setLastRefresh]     = useState(null)
  const [actionId,        setActionId]        = useState(null)
  const [toast,           setToast]           = useState(null)
  const fetchedTabs = useRef(new Set())

  useEffect(() => {
    if (authLoading) return
    if (!user) { navigate('/login'); return }
    if (!isAdmin) { navigate('/dashboard'); return }
  }, [user, isAdmin, authLoading, navigate])

  const showToast = useCallback((msg, type='success') => {
    setToast({msg,type}); setTimeout(()=>setToast(null),3000)
  }, [])

  const fetchOverview = useCallback(async () => {
    setLoadingOverview(true)
    const [uStats, aStats, aRecent, rHigh, btStats] = await Promise.all([
      safeFetch(()=>apiService.get('/api/admin/stats').then(r=>r.data)),
      safeFetch(()=>apiService.get('/api/alerts/market/stats').then(r=>r.data)),
      safeFetch(()=>apiService.get('/api/alerts/market/recent').then(r=>toArray(r.data))),
      safeFetch(()=>apiService.get('/api/risk/high-risk?minScore=70').then(r=>toArray(r.data))),
      safeFetch(()=>apiService.get('/api/backtest/system-accuracy').then(r=>r.data)),
    ])
    if (uStats.data)   setUserStats(uStats.data)
    if (aStats.data)   setAlertStats(aStats.data)
    if (aRecent.data)  setRecentAlerts(aRecent.data)
    if (rHigh.data)    setRiskSymbols(rHigh.data)
    if (btStats.data)  setBacktestStats(btStats.data)
    setLoadingOverview(false)
    setLastRefresh(new Date())
  }, [])

  const fetchTab = useCallback(async (tabId, force=false) => {
    if (!force && fetchedTabs.current.has(tabId)) return
    fetchedTabs.current.add(tabId)
    if (tabId === 'users') {
      setLoadingUsers(true)
      const r = await safeFetch(()=>apiService.get('/api/admin/users').then(res=>toArray(res.data)))
      if (r.data) { setUsers(r.data); setUsersErr(null) }
      if (r.error) setUsersErr(r.error)
      setLoadingUsers(false)
    }
    if (tabId === 'alerts') {
      setLoadingAlerts(true)
      const [pd, an] = await Promise.all([
        safeFetch(()=>apiService.get('/api/alerts/market/pump-dump').then(r=>toArray(r.data))),
        safeFetch(()=>apiService.get('/api/alerts/market/anomalies').then(r=>toArray(r.data))),
      ])
      if (pd.data) setPumpDump(pd.data)
      if (an.data) setAnomalies(an.data)
      setLoadingAlerts(false)
    }
    if (tabId === 'risk') {
      setLoadingRisk(true)
      const [pd, vs] = await Promise.all([
        safeFetch(()=>apiService.get('/api/indicators/pump-dump').then(r=>toArray(r.data))),
        safeFetch(()=>apiService.get('/api/indicators/volume-spikes').then(r=>toArray(r.data))),
      ])
      if (pd.data) setPumpDumpSyms(pd.data)
      if (vs.data) setVolumeSpikes(vs.data)
      setLoadingRisk(false)
    }
    if (tabId === 'performance') {
      setLoadingPerf(true)
      const r = await safeFetch(()=>apiService.get('/api/backtest/all').then(res=>toArray(res.data)))
      if (r.data) setBacktestAll(r.data)
      setLoadingPerf(false)
    }
    if (tabId === 'infra') {
      setLoadingInfra(true); setPromErr(null)
      const [hlth, promText] = await Promise.all([
        safeFetch(()=>apiService.get('/actuator/health').then(r=>r.data)),
        safeFetch(()=>apiService.get('/actuator/prometheus', { responseType:'text' }).then(r=>r.data)),
      ])
      if (hlth.data) setHealth(hlth.data)
      if (promText.error) {
        setPromErr(promText.error); setPromRaw(null)
      } else if (typeof promText.data === 'string' && promText.data.length > 0) {
        setPromRaw(promText.data)
        const parsed = parsePrometheus(promText.data)
        const allKeys = Object.keys(parsed)
        const mktKeys = allKeys.filter(k=>k.startsWith('marketai'))
        console.log('[Admin] Prometheus total keys:', allKeys.length)
        console.log('[Admin] marketai_ keys:', mktKeys)
        console.log('[Admin] Raw sample:', promText.data.slice(0,500))
        setProm(parsed)
      } else if (promText.data !== null && promText.data !== undefined) {
        const msg = typeof promText.data === 'object'
          ? 'axios returned JSON object instead of plain text\nKeys: '+Object.keys(promText.data||{}).join(', ')
          : 'unexpected response type: '+typeof promText.data
        setPromErr(msg); setPromRaw(msg)
      } else {
        setPromErr('No data from /actuator/prometheus — check application.yml')
      }
      setLoadingInfra(false)
    }
  }, [])

  useEffect(() => {
    if (authLoading || !user || !isAdmin) return
    fetchOverview()
  }, [fetchOverview, authLoading, user, isAdmin])

  const switchTab = useCallback((id) => { setTab(id); fetchTab(id) }, [fetchTab])

  const handleRefresh = useCallback(async () => {
    setRefreshing(true)
    fetchedTabs.current.delete(tab)
    await Promise.all([fetchOverview(), fetchTab(tab, true)])
    setRefreshing(false)
  }, [fetchOverview, fetchTab, tab])

  const toggleUser  = useCallback(async id => {
    setActionId(id+'_t')
    try { const {data}=await apiService.put(`/api/admin/users/${id}/toggle`); setUsers(p=>p.map(u=>u.id===id?data:u)); showToast(`User ${data.enabled?'enabled':'disabled'}`) }
    catch { showToast('Failed to update user','error') }
    finally { setActionId(null) }
  }, [showToast])

  const deleteUser = useCallback(async id => {
    if (!window.confirm('Delete this user permanently?')) return
    setActionId(id+'_d')
    try { await apiService.delete(`/api/admin/users/${id}`); setUsers(p=>p.filter(u=>u.id!==id)); showToast('User deleted') }
    catch { showToast('Failed to delete user','error') }
    finally { setActionId(null) }
  }, [showToast])

  const ackAlert = useCallback(async id => {
    try {
      await apiService.put(`/api/alerts/market/${id}/acknowledge`)
      const upd = a=>a.id===id?{...a,acknowledged:true}:a
      setRecentAlerts(p=>p.map(upd)); setPumpDump(p=>p.map(upd)); setAnomalies(p=>p.map(upd))
      showToast('Alert acknowledged')
    } catch { showToast('Failed','error') }
  }, [showToast])

  const addPreset   = useCallback((sym,exc,t) => { if(stockList.find(s=>s.symbol===sym)){t?.('Already in list','error');return}; setStockList(p=>[...p,{symbol:sym,exchange:exc,name:sym,active:true}]); t?.(`${sym} added`) }, [stockList])
  const addCustom   = useCallback((s,t) => { const sym=s.symbol.toUpperCase(); if(stockList.find(x=>x.symbol===sym)){t?.('Already exists','error');return}; setStockList(p=>[...p,{symbol:sym,exchange:s.exchange,name:s.name||sym,active:true}]); t?.(`${sym} added`) }, [stockList])
  const toggleStock = useCallback(sym => setStockList(p=>p.map(s=>s.symbol===sym?{...s,active:!s.active}:s)), [])
  const removeStock = useCallback(sym => setStockList(p=>p.filter(s=>s.symbol!==sym)), [])

  if (authLoading) {
    return (
      <div className="min-h-screen bg-[#050B17] flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-2 border-cyan-500/30 border-t-cyan-400 rounded-full animate-spin"/>
          <p className="text-xs font-mono text-slate-500">Verifying session…</p>
        </div>
      </div>
    )
  }

  const activeTab = TABS.find(t => t.id === tab)

  return (
    <div className="min-h-screen bg-[#050B17]" style={{fontFamily:"'DM Sans',sans-serif"}}>

      {/*
        ╔══════════════════════════════════════════════════════════════════╗
        ║  HEADER — 2 rows, never overlaps on any screen size             ║
        ║                                                                  ║
        ║  ROW 1 (h-11 = 44px)  Brand logo+name  |  Refresh  Logout       ║
        ║  ─────────────────────────────────────────────────────────────  ║
        ║  ROW 2 (h-10 = 40px)  Scrollable tab strip                      ║
        ║                                                                  ║
        ║  Total header: 84px → body gets pt-[88px]                       ║
        ╚══════════════════════════════════════════════════════════════════╝
      */}
      <header className="fixed top-0 inset-x-0 z-50 bg-[#050B17]/98 backdrop-blur-xl border-b border-white/[0.07]">

        {/* ── ROW 1: Brand + Right actions ─────────────────────────────── */}
        <div className="max-w-[1800px] mx-auto px-4 xl:px-6 h-11 flex items-center justify-between gap-4 border-b border-white/[0.04]">

          {/* Brand */}
          <div className="flex items-center gap-2 flex-shrink-0">
            <div className="w-7 h-7 rounded-lg bg-amber-500/20 border border-amber-500/30 flex items-center justify-center flex-shrink-0">
              <RiShieldLine size={14} className="text-amber-400"/>
            </div>
            <div className="leading-none">
              <p className="text-[13px] font-black text-white leading-none">
                MarketAI <span className="text-amber-400">Admin</span>
              </p>
              <p className="text-[9px] text-slate-600 font-mono mt-0.5 hidden sm:block">Control Panel</p>
            </div>
          </div>

          {/* Actions — right side, never wraps */}
          <div className="flex items-center gap-1.5 flex-shrink-0">
            {/* Timestamp — large screens only */}
            <span className={clsx(
              'hidden lg:flex items-center gap-1 text-[10px] font-mono px-2 py-1 rounded-lg border',
              refreshing ? 'text-cyan-400 bg-cyan-500/10 border-cyan-500/25' : 'text-slate-600 bg-white/[0.02] border-white/[0.06]'
            )}>
              <RiLoader4Line size={9} className={refreshing ? 'animate-spin' : ''}/>
              {lastRefresh ? dayjs(lastRefresh).fromNow() : 'Loading…'}
            </span>

            <button onClick={handleRefresh} disabled={refreshing}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border border-white/[0.08] bg-white/[0.04] text-slate-300 hover:bg-white/[0.08] transition-all disabled:opacity-50 whitespace-nowrap">
              <RiRefreshLine size={12} className={refreshing ? 'animate-spin' : ''}/>
              <span className="hidden sm:inline">Refresh</span>
            </button>

            <button onClick={() => { logout(); navigate('/') }}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border border-red-500/20 bg-red-500/[0.06] text-red-400 hover:bg-red-500/10 transition-all whitespace-nowrap">
              <RiLogoutBoxLine size={12}/>
              <span className="hidden sm:inline">Logout</span>
            </button>
          </div>
        </div>

        {/* ── ROW 2: Tab strip (scrollable, never wraps) ───────────────── */}
        <div className="max-w-[1800px] mx-auto px-3 xl:px-5">
          <nav className="flex items-center gap-0.5 overflow-x-auto scrollbar-none py-1.5">
            {TABS.map(t => (
              <button key={t.id} onClick={() => switchTab(t.id)}
                className={clsx(
                  'flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold whitespace-nowrap transition-all flex-shrink-0',
                  tab === t.id
                    ? 'bg-cyan-500 text-slate-950 shadow-[0_0_12px_#06b6d440]'
                    : 'text-slate-500 hover:text-white hover:bg-white/[0.06]'
                )}>
                <t.icon size={12}/>
                {t.label}
              </button>
            ))}
          </nav>
        </div>

      </header>
      {/* ↑ header = 44px row1 + 40px row2 = 84px → pt-[88px] adds 4px gap */}

      {/* ── Body ── */}
      <div className="pt-[88px] max-w-[1800px] mx-auto px-4 xl:px-6 pb-12">

        {/* Page title row */}
        <div className="flex items-center gap-3 py-5 mb-2 flex-wrap">
          {activeTab && <activeTab.icon size={18} className="text-cyan-400 flex-shrink-0"/>}
          <h1 className="text-xl font-black text-white">{activeTab?.label}</h1>
          {tab === 'users' && !loadingUsers && (
            <span className="text-xs text-slate-500 font-mono">
              {users.length} total · {users.filter(u=>u.enabled).length} active
            </span>
          )}
          {tab === 'infra' && health && (
            <span className={clsx('ml-auto text-[9px] font-bold px-2.5 py-1 rounded-full border',
              health?.status==='UP'?'bg-emerald-500/10 text-emerald-400 border-emerald-500/20':'bg-amber-500/10 text-amber-400 border-amber-500/20')}>
              {health?.status==='UP'?'● All systems operational':`● ${health?.status??'Checking…'}`}
            </span>
          )}
        </div>

        {/* Tab content */}
        <AnimatePresence mode="wait">
          <motion.div key={tab}
            initial={{opacity:0, y:4}} animate={{opacity:1, y:0}} exit={{opacity:0}}
            transition={{duration:0.1}}>
            {tab==='overview'    && <OverviewTab    userStats={userStats} alertStats={alertStats} backtestStats={backtestStats} riskSymbols={riskSymbols} recentAlerts={recentAlerts} loading={loadingOverview}/>}
            {tab==='users'       && <UsersTab       users={users} onToggle={toggleUser} onDelete={deleteUser} actionId={actionId} loading={loadingUsers} error={usersErr}/>}
            {tab==='alerts'      && <AlertsTab      alerts={recentAlerts} pumpDump={pumpDump} anomalies={anomalies} onAck={ackAlert} loading={loadingAlerts}/>}
            {tab==='risk'        && <RiskTab        riskSymbols={riskSymbols} pumpDumpSyms={pumpDumpSyms} volumeSpikes={volumeSpikes} loading={loadingRisk}/>}
            {tab==='performance' && <PerformanceTab backtestStats={backtestStats} backtestAll={backtestAll} loading={loadingPerf}/>}
            {tab==='infra'       && <InfraTab       prom={prom} promRaw={promRaw} promErr={promErr} health={health} loading={loadingInfra}/>}
            {tab==='symbols'     && <SymbolsTab     stockList={stockList} onAddPreset={addPreset} onToggleStock={toggleStock} onRemoveStock={removeStock} onAddCustom={addCustom}/>}
          </motion.div>
        </AnimatePresence>
      </div>

      <AnimatePresence>
        {toast && <Toast msg={toast.msg} type={toast.type} onDone={()=>setToast(null)}/>}
      </AnimatePresence>
    </div>
  )
}