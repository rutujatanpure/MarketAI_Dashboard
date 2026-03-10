import { useCurrency } from '../hooks/useCurrency'

const CURRENCIES = [
  { code:'USD', flag:'🇺🇸', label:'USD' },
  { code:'INR', flag:'🇮🇳', label:'INR' },
  { code:'EUR', flag:'🇪🇺', label:'EUR' },
  { code:'GBP', flag:'🇬🇧', label:'GBP' },
]

export default function CurrencySelector({ compact=true }) {
  const { currency, changeCurrency } = useCurrency()
  if (compact) {
    return (
      <div className="flex gap-0.5 p-0.5 bg-navy-800/60 rounded-xl border border-slate-700/30">
        {CURRENCIES.map(c => (
          <button key={c.code} onClick={()=>changeCurrency(c.code)}
            className={`px-2.5 py-1.5 rounded-lg text-xs font-mono font-bold transition-all border-none cursor-pointer
              ${currency===c.code ? 'bg-gradient-to-r from-emerald-500 to-teal-500 text-white' : 'bg-transparent text-slate-400 hover:text-slate-200'}`}>
            {c.label}
          </button>
        ))}
      </div>
    )
  }
  return (
    <div className="glass-card p-4">
      <h4 className="text-xs font-mono text-slate-500 uppercase tracking-wider mb-3">Currency</h4>
      <div className="grid grid-cols-2 gap-2">
        {CURRENCIES.map(c => (
          <button key={c.code} onClick={()=>changeCurrency(c.code)}
            className={`flex items-center gap-2 px-3 py-2.5 rounded-xl border text-sm font-bold transition-all cursor-pointer font-display
              ${currency===c.code ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'border-slate-700/40 text-slate-400 hover:border-slate-600 hover:text-slate-200 bg-transparent'}`}>
            <span>{c.flag}</span>{c.label}
          </button>
        ))}
      </div>
    </div>
  )
}