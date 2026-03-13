export default function LoadingSpinner({ size='md', color='emerald', label='', fullScreen=false }) {
  const sizes  = { xs:'w-4 h-4 border', sm:'w-6 h-6 border-2', md:'w-8 h-8 border-2', lg:'w-12 h-12 border-2', xl:'w-16 h-16 border-[3px]' }
  const colors = {
    emerald:'border-emerald-500/20 border-t-emerald-400',
    cyan:   'border-cyan-500/20 border-t-cyan-400',
    rose:   'border-rose-500/20 border-t-rose-400',
    white:  'border-white/20 border-t-white',
  }
  const spinner = (
    <div className="flex flex-col items-center justify-center gap-3">
      <div className={`rounded-full animate-spin ${sizes[size]||sizes.md} ${colors[color]||colors.emerald}`}/>
      {label && <p className="text-xs font-mono text-slate-500 animate-pulse m-0">{label}</p>}
    </div>
  )
  if (fullScreen) return (
    <div className="fixed inset-0 flex items-center justify-center bg-navy-950/80 backdrop-blur-sm z-50">{spinner}</div>
  )
  return spinner
}

export function SkeletonCard() {
  return (
    <div className="glass-card p-5 animate-pulse">
      <div className="flex items-center gap-3 mb-4">
        <div className="w-10 h-10 rounded-xl bg-slate-800"/>
        <div className="space-y-2">
          <div className="w-20 h-3 rounded bg-slate-800"/>
          <div className="w-12 h-2 rounded bg-slate-800/60"/>
        </div>
      </div>
      <div className="w-32 h-7 rounded bg-slate-800 mb-2"/>
      <div className="w-full h-2 rounded bg-slate-800/60"/>
    </div>
  )
}
