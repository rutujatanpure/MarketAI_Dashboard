import toast from 'react-hot-toast'
import { apiService } from './apiService'

export const notificationService = {
  getRecent: () => apiService.get('/api/alerts/recent').then(r => r.data),

  showPriceAlert(alert) {
    const isUp  = alert.priceChange > 0
    const emoji = isUp ? '📈' : '📉'
    const color = isUp ? '#34d399' : '#fb7185'
    toast.custom(t => (
      <div style={{ background: '#0f2040', border: `1px solid ${color}40`, borderRadius: 12, padding: '12px 16px', minWidth: 240, opacity: t.visible ? 1 : 0, transition: 'opacity 0.3s' }}>
        <p style={{ color, fontWeight: 700, margin: 0 }}>{emoji} {alert.symbol}</p>
        <p style={{ color: '#94a3b8', fontSize: 12, margin: '4px 0 0', fontFamily: 'JetBrains Mono' }}>{alert.message}</p>
      </div>
    ), { duration: 5000 })
  },

  showAiSignal(symbol, signal) {
    const map = { BUY: { emoji: '🟢', color: '#34d399' }, SELL: { emoji: '🔴', color: '#fb7185' }, HOLD: { emoji: '🟡', color: '#fbbf24' } }
    const s   = map[signal] || map.HOLD
    toast.custom(() => (
      <div style={{ background: '#0f2040', border: `1px solid ${s.color}40`, borderRadius: 12, padding: '12px 16px', minWidth: 220 }}>
        <p style={{ color: s.color, fontWeight: 700, margin: 0 }}>{s.emoji} AI Signal: {signal}</p>
        <p style={{ color: '#94a3b8', fontSize: 12, margin: '4px 0 0', fontFamily: 'JetBrains Mono' }}>{symbol}</p>
      </div>
    ), { duration: 4000 })
  },
}