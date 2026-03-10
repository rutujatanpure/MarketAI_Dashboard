import { useMemo } from 'react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer
} from 'recharts'
import { useCurrency } from '../hooks/useCurrency'
import dayjs from 'dayjs'

const CustomTooltip = ({ active, payload, label, format }) => {
  if (!active || !payload?.length) return null
  return (
    <div className="glass-card px-3 py-2 text-xs font-mono">
      <p className="text-slate-400">{dayjs(label).format('MMM D, HH:mm')}</p>
      <p className="text-cyan-400 font-semibold mt-0.5">{format(payload[0].value, 2)}</p>
    </div>
  )
}

export default function DetailChart({ history = [], height = 260 }) {
  const { format } = useCurrency()

  const data = useMemo(() => {
    const pts = history.slice(-120)
    if (!pts.length) return [{ t: Date.now(), price: 0 }]
    return pts.map(h => ({ t: h.timestamp || h.t, price: h.price || h.close }))
  }, [history])

  const isUp  = data.length > 1 && data[data.length - 1].price >= data[0].price
  const color = isUp ? '#34d399' : '#fb7185'

  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 4, right: 0, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor={color} stopOpacity={0.25} />
            <stop offset="95%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
        <XAxis dataKey="t" hide />
        <YAxis
          width={68}
          tickFormatter={v => format(v, v > 1000 ? 0 : 2)}
          tick={{ fill: '#475569', fontSize: 10, fontFamily: 'JetBrains Mono' }}
          axisLine={false} tickLine={false}
          domain={['auto', 'auto']}
        />
        <Tooltip content={<CustomTooltip format={format} />} />
        <Area
          type="monotone" dataKey="price"
          stroke={color} strokeWidth={2}
          fill="url(#chartGrad)"
          dot={false} activeDot={{ r: 4, fill: color, strokeWidth: 0 }}
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}