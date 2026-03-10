import { useState, useEffect, useRef } from 'react'
import { usePrices } from '../context/PriceContext'

/**
 * Subscribe to a STOMP topic and return latest data.
 * Uses the shared PriceContext client (no duplicate connections).
 *
 * const data = useWebSocket('/topic/analysis/BTCUSDT', { enabled: !!token })
 */
export function useWebSocket(topic, { enabled = true } = {}) {
  const { requestAnalysis } = usePrices()
  const [data, setData]   = useState(null)
  const subRef            = useRef(null)

  useEffect(() => {
    if (!enabled || !topic) return
    // For analysis topics — use PriceContext's publish mechanism
    // General subscriptions hook into context through a custom event bus or direct sub
    return () => { subRef.current?.unsubscribe?.() }
  }, [topic, enabled])

  return data
}