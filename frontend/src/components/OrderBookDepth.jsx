import { useMemo } from 'react'
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { useCurrency } from '../hooks/useCurrency'

function generateDepth(price) {
  if (!price) return []
  const data = []
  let bidVol = 0, askVol = 0
  for (let i = 20; i >= 1; i--) {
    bidVol += Math.random() * 5 + 1
    data.push({ price: +(price - i * price * 0.001).toFixed(2), bid: +bidVol.toFixed(2), ask: null })
  }
  for (let i = 1; i <= 20; i++) {
    askVol += Math.random() * 5 + 1
    data.push({ price: +(price + i * price * 0.001).toFixed(2), bid: null, ask: +askVol.toFixed(2) })
  }
  return data
}

const Tip = ({ active, payload }) => {
  if (!active || !payload?.length) return null
  const d = payload[0]?.payload
  return (
    <div className="glass-card px-3 py-2 text-xs font-mono">
      <p className="text-slate-400">Price: ${d.price}</p>
      {d.bid  != null && <p className="text-emerald-400">Bid Vol: {d.bid}</p>}
      {d.ask  != null && <p className="text-rose-400">Ask Vol: {d.ask}</p>}
    </div>
  )
}

export default function OrderBookDepth({ price = 0, height = 260 }) {
  const { format }  = useCurrency()
  const data        = useMemo(() => generateDepth(price), [Math.round(price)])

  return (
    <div style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 4, right: 0, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="bidGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#34d399" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#34d399" stopOpacity={0} />
            </linearGradient>
            <linearGradient id="askGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#fb7185" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#fb7185" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
          <XAxis dataKey="price" tick={{ fill: '#475569', fontSize: 9, fontFamily: 'JetBrains Mono' }} tickFormatter={v => `$${v}`} />
          <YAxis width={40} tick={{ fill: '#475569', fontSize: 9 }} axisLine={false} tickLine={false} />
          <Tooltip content={<Tip />} />
          <Area type="stepBefore" dataKey="bid" stroke="#34d399" strokeWidth={1.5} fill="url(#bidGrad)" connectNulls />
          <Area type="stepAfter"  dataKey="ask" stroke="#fb7185" strokeWidth={1.5} fill="url(#askGrad)" connectNulls />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}