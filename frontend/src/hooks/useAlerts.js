import { useState, useEffect } from 'react'
import { usePrices } from '../context/PriceContext'
import { marketService } from '../services/marketService'

/**
 * Hook to access price anomaly alerts.
 *
 * Usage:
 *   const { alerts, loading } = useAlerts()           → all recent alerts
 *   const { alerts } = useAlerts('BTCUSDT', 5)        → last 5 alerts for BTC
 *
 * Returns:
 *   alerts  — array of AlertNotification objects, newest first
 *   loading — boolean
 */
export function useAlerts(symbol = null, limit = 20) {
  const { alerts: wsAlerts } = usePrices()   // real-time alerts from WebSocket
  const [fetchedAlerts, setFetchedAlerts] = useState([])
  const [loading,       setLoading]       = useState(false)

  // Fetch historical alerts on mount
  useEffect(() => {
    setLoading(true)
    const req = symbol
      ? marketService.getAlertsBySymbol(symbol)
      : marketService.getRecentAlerts()

    req.then(data => setFetchedAlerts(data || []))
       .catch(() => setFetchedAlerts([]))
       .finally(() => setLoading(false))
  }, [symbol])

  // Merge real-time WebSocket alerts with fetched historical ones
  const merged = [...wsAlerts, ...fetchedAlerts]
    .filter(a => !symbol || a.symbol === symbol)
    .reduce((acc, alert) => {
      // Deduplicate by timestamp
      const key = `${alert.symbol}-${alert.timestamp}`
      if (!acc.seen.has(key)) {
        acc.seen.add(key)
        acc.list.push(alert)
      }
      return acc
    }, { seen: new Set(), list: [] })
    .list
    .slice(0, limit)

  return { alerts: merged, loading }
}